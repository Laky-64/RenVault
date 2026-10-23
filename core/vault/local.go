package vault

import (
	"cmp"
	"crypto/ecdh"
	"crypto/rand"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/Laky-64/appleservices/keychain"
	"golang.org/x/crypto/chacha20poly1305"
)

const (
	maxPhotoBytes     = 2 << 20
	maxDeviceName     = 64
	maxProfileName    = 128
	maxRememberedAddr = 6
	shareVersion      = 1
)

var (
	errAppleOnly = errors.New("vault: only available for iCloud vaults")
	errLocalOnly = errors.New("vault: only available for local vaults")
	errBadPhoto  = errors.New("vault: the photo must be a JPEG or PNG image under 2 MiB")
	errNoShare   = errors.New("vault: the local vault is not available")
)

type hlc struct {
	Wall  int64  `cbor:"w"`
	Count uint32 `cbor:"c"`
	Node  string `cbor:"n"`
}

func (c hlc) compare(o hlc) int {
	return cmp.Or(
		cmp.Compare(c.Wall, o.Wall),
		cmp.Compare(c.Count, o.Count),
		strings.Compare(c.Node, o.Node),
	)
}

type sealedDevice struct {
	ID      string `cbor:"id"`
	Key     []byte `cbor:"key"`
	Clock   hlc    `cbor:"clock"`
	Removed bool   `cbor:"removed,omitempty"`
	Box     []byte `cbor:"box,omitempty"`
}

type sealedBlob struct {
	Clock hlc    `cbor:"clock"`
	Box   []byte `cbor:"box,omitempty"`
}

type peerState struct {
	Addrs    []string  `cbor:"addrs,omitempty"`
	SyncedAt time.Time `cbor:"synced_at"`
}

type shareFile struct {
	Version  int                  `cbor:"version"`
	Node     string               `cbor:"node"`
	Sealer   string               `cbor:"sealer"`
	Identity []byte               `cbor:"identity"`
	Clock    hlc                  `cbor:"clock"`
	Records  []sealedRecord       `cbor:"records,omitempty"`
	Devices  []sealedDevice       `cbor:"devices,omitempty"`
	Profile  sealedBlob           `cbor:"profile"`
	Names    sealedBlob           `cbor:"names"`
	Peers    map[string]peerState `cbor:"peers,omitempty"`
}

type share struct {
	shareFile
	identity []byte
}

type localKeys struct {
	Key      []byte `cbor:"key"`
	Identity []byte `cbor:"identity"`
}

type profileState struct {
	Name      string `cbor:"name"`
	Photo     []byte `cbor:"photo,omitempty"`
	PhotoType string `cbor:"photo_type,omitempty"`
}

func sharePath(base string) string {
	return filepath.Join(base, "share.cbor")
}

func loadShare(base string) (*share, error) {
	data, err := os.ReadFile(sharePath(base))
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	var f shareFile
	if err := cborDec.Unmarshal(data, &f); err != nil {
		return nil, err
	}
	sh := &share{shareFile: f}
	if secret, _, err := deviceSecret(base, f.Sealer, false); err == nil {
		sh.identity, _ = openWith(secret, []byte("identity:"+f.Node), f.Identity)
		zero(secret)
	}
	return sh, nil
}

func saveShare(base string, sh *share) error {
	data, err := cborEnc.Marshal(sh.shareFile)
	if err != nil {
		return err
	}
	return writeFileAtomic(sharePath(base), data, 0o600)
}

func removeShare(base string) {
	_ = os.Remove(sharePath(base))
	forgetDeviceSecret(base)
}

func sealWith(key, ad, plain []byte) ([]byte, error) {
	aead, err := chacha20poly1305.NewX(key)
	if err != nil {
		return nil, err
	}
	nonce := make([]byte, aead.NonceSize(), aead.NonceSize()+len(plain)+aead.Overhead())
	if _, err := rand.Read(nonce); err != nil {
		return nil, err
	}
	return aead.Seal(nonce, nonce, plain, ad), nil
}

func openWith(key, ad, box []byte) ([]byte, error) {
	aead, err := chacha20poly1305.NewX(key)
	if err != nil {
		return nil, err
	}
	if len(box) < aead.NonceSize() {
		return nil, errors.New("vault: sealed box too short")
	}
	return aead.Open(nil, box[:aead.NonceSize()], box[aead.NonceSize():], ad)
}

func adOf(parts ...any) []byte {
	ad, _ := cborEnc.Marshal(parts)
	return ad
}

func (d sealedDevice) ad() []byte { return adOf("device", d.ID, d.Key, d.Clock, d.Removed) }

func blobAD(kind string, clock hlc) []byte { return adOf(kind, clock) }

func (sh *share) tick() hlc {
	now := time.Now().UnixMilli()
	if now > sh.Clock.Wall {
		sh.Clock = hlc{Wall: now}
	} else {
		sh.Clock.Count++
	}
	sh.Clock.Node = sh.Node
	return sh.Clock
}

func (sh *share) observe(c hlc) {
	if c.Wall > sh.Clock.Wall || (c.Wall == sh.Clock.Wall && c.Count > sh.Clock.Count) {
		sh.Clock.Wall, sh.Clock.Count = c.Wall, c.Count
	}
}

func (sh *share) record(id string) int {
	return slices.IndexFunc(sh.Records, func(r sealedRecord) bool { return r.ID == id })
}

func (sh *share) device(id string) int {
	return slices.IndexFunc(sh.Devices, func(d sealedDevice) bool { return d.ID == id })
}

func (sh *share) trusted(key []byte) (sealedDevice, bool) {
	for _, d := range sh.Devices {
		if !d.Removed && d.ID != sh.Node && slices.Equal(d.Key, key) {
			return d, true
		}
	}
	return sealedDevice{}, false
}

func (sh *share) remember(id string, addrs ...string) {
	if sh.Peers == nil {
		sh.Peers = map[string]peerState{}
	}
	st := sh.Peers[id]
	next := make([]string, 0, maxRememberedAddr)
	for _, a := range slices.Concat(addrs, st.Addrs) {
		if a != "" && !slices.Contains(next, a) && len(next) < maxRememberedAddr {
			next = append(next, a)
		}
	}
	st.Addrs = next
	sh.Peers[id] = st
}

func (sh *share) putDevice(key []byte, d sealedDevice, name string) error {
	d.Clock = sh.tick()
	d.Box = nil
	if key != nil {
		box, err := sealWith(key, d.ad(), []byte(cleanDeviceName(name)))
		if err != nil {
			return err
		}
		d.Box = box
	}
	if i := sh.device(d.ID); i >= 0 {
		sh.Devices[i] = d
	} else {
		sh.Devices = append(sh.Devices, d)
	}
	return nil
}

func (sh *share) deviceName(key []byte, d sealedDevice) string {
	if key == nil || d.Box == nil {
		return ""
	}
	name, err := openWith(key, d.ad(), d.Box)
	if err != nil {
		return ""
	}
	return string(name)
}

func (sh *share) putBlob(key []byte, kind string, value any) (sealedBlob, error) {
	plain, err := cborEnc.Marshal(value)
	if err != nil {
		return sealedBlob{}, err
	}
	b := sealedBlob{Clock: sh.tick()}
	b.Box, err = sealWith(key, blobAD(kind, b.Clock), plain)
	return b, err
}

func openBlob(key []byte, kind string, b sealedBlob, into any) bool {
	if b.Box == nil {
		return false
	}
	plain, err := openWith(key, blobAD(kind, b.Clock), b.Box)
	if err != nil {
		return false
	}
	return cborDec.Unmarshal(plain, into) == nil
}

func (sh *share) reseal(from, to []byte) {
	sh.resealRecords(from, to)
	for i, d := range sh.Devices {
		if name := sh.deviceName(from, d); name != "" {
			if box, err := sealWith(to, d.ad(), []byte(name)); err == nil {
				sh.Devices[i].Box = box
			}
		}
	}
	for _, blob := range []struct {
		kind string
		b    *sealedBlob
	}{{"profile", &sh.Profile}, {"names", &sh.Names}} {
		if blob.b.Box == nil {
			continue
		}
		plain, err := openWith(from, blobAD(blob.kind, blob.b.Clock), blob.b.Box)
		if err != nil {
			continue
		}
		if box, err := sealWith(to, blobAD(blob.kind, blob.b.Clock), plain); err == nil {
			blob.b.Box = box
		}
	}
}

func cleanDeviceName(name string) string {
	name = strings.TrimSpace(name)
	if name == "" {
		return "RenVault"
	}
	if utf8.RuneCountInString(name) > maxDeviceName {
		name = string([]rune(name)[:maxDeviceName])
	}
	return name
}

func photoType(photo []byte) (string, error) {
	if len(photo) == 0 {
		return "", nil
	}
	if len(photo) > maxPhotoBytes {
		return "", errBadPhoto
	}
	switch kind := http.DetectContentType(photo); kind {
	case "image/jpeg", "image/png":
		return kind, nil
	}
	return "", errBadPhoto
}

func (v *Vault) Local() bool {
	v.mu.Lock()
	defer v.mu.Unlock()
	return v.sh != nil
}

func (v *Vault) sharedKeyLocked() []byte {
	if v.sh == nil || v.p == nil || v.p.Local == nil || v.k == nil {
		return nil
	}
	return v.p.Local.Key
}

func (v *Vault) recordOfLocked(id string) string {
	i := webIndex(v.p, id)
	if i < 0 || i >= len(v.recIDs) {
		return ""
	}
	return v.recIDs[i]
}

func (v *Vault) rebuildShareLocked() {
	key := v.sharedKeyLocked()
	if key == nil {
		return
	}
	migrated := false
	for _, r := range slices.Clone(v.sh.Records) {
		if !r.legacy() {
			continue
		}
		if w, ok := openRecord(key, r); ok && v.sh.putWeb(key, r.ID, w) == nil {
			migrated = true
		}
	}
	if migrated {
		_ = saveShare(v.base, v.sh)
	}
	web := make([]keychain.WebPassword, 0, len(v.sh.Records))
	ids := make([]string, 0, len(v.sh.Records))
	for _, r := range v.sh.Records {
		w, ok := openRecord(key, r)
		if !ok {
			continue
		}
		web = append(web, w)
		ids = append(ids, r.ID)
	}
	v.p.Web = web
	v.recIDs = ids
	prunePwned(v.p)

	var profile profileState
	openBlob(key, "profile", v.sh.Profile, &profile)
	v.p.ProfileName = profile.Name
	v.p.ProfilePhoto = profile.Photo
	v.p.ProfilePhotoType = profile.PhotoType

	var names []NameSwap
	openBlob(key, "names", v.sh.Names, &names)
	v.p.Names = sanitizeNames(names)
}

func (v *Vault) localPut(id string, w keychain.WebPassword) error {
	if err := v.sh.putWeb(v.sharedKeyLocked(), id, w); err != nil {
		return err
	}
	v.rebuildShareLocked()
	return nil
}

func (v *Vault) localTrash(id string, trash bool) error {
	i := webIndex(v.p, id)
	rec := v.recordOfLocked(id)
	if i < 0 || rec == "" {
		return errNotFound
	}
	w := v.p.Web[i]
	if w.IsDeleted == trash {
		return nil
	}
	w.IsDeleted = trash
	w.DeletedAt = time.Time{}
	if trash {
		w.DeletedAt = time.Now().UTC()
	}
	return v.localPut(rec, w)
}

func (v *Vault) localPurge(id string) error {
	i := webIndex(v.p, id)
	rec := v.recordOfLocked(id)
	if i < 0 || rec == "" || !v.p.Web[i].IsDeleted {
		return errNotFound
	}
	if err := v.sh.purge(v.sharedKeyLocked(), rec); err != nil {
		return err
	}
	v.rebuildShareLocked()
	return nil
}

func (v *Vault) localNamesLocked(names []NameSwap) error {
	blob, err := v.sh.putBlob(v.sharedKeyLocked(), "names", names)
	if err != nil {
		return err
	}
	v.sh.Names = blob
	return nil
}

func (v *Vault) FinishLocalSetup(masterPassword, name, deviceName string, photo []byte) error {
	kind, err := photoType(photo)
	if err != nil {
		return err
	}
	name = strings.TrimSpace(name)
	if utf8.RuneCountInString(name) > maxProfileName {
		return errors.New("vault: the name is too long")
	}
	identity, err := ecdh.X25519().GenerateKey(rand.Reader)
	if err != nil {
		return err
	}
	shared, err := genKey()
	if err != nil {
		return err
	}
	sh := &share{shareFile: shareFile{Version: shareVersion, Node: newLocalID()}, identity: identity.Bytes()}
	secret, sealer, err := deviceSecret(v.base, "", true)
	if err != nil {
		return err
	}
	sh.Sealer = sealer
	sh.Identity, err = sealWith(secret, []byte("identity:"+sh.Node), sh.identity)
	zero(secret)
	if err != nil {
		return err
	}
	if err := sh.putDevice(shared, sealedDevice{ID: sh.Node, Key: identity.PublicKey().Bytes()}, deviceName); err != nil {
		return err
	}
	if name != "" || len(photo) > 0 {
		if sh.Profile, err = sh.putBlob(shared, "profile", profileState{Name: name, Photo: photo, PhotoType: kind}); err != nil {
			return err
		}
	}
	if err := saveShare(v.base, sh); err != nil {
		return err
	}
	p := &payload{Local: &localKeys{Key: shared, Identity: slices.Clone(sh.identity)}}
	v.mu.Lock()
	v.sh = sh
	v.mu.Unlock()
	if err := v.install(masterPassword, p, false); err != nil {
		v.mu.Lock()
		v.sh = nil
		v.mu.Unlock()
		removeShare(v.base)
		return err
	}
	return nil
}

func (v *Vault) SetProfile(name string, photo []byte, setPhoto bool) error {
	name = strings.TrimSpace(name)
	if utf8.RuneCountInString(name) > maxProfileName {
		return errors.New("vault: the name is too long")
	}
	kind, err := photoType(photo)
	if err != nil {
		return err
	}
	return v.mutate(func(p *payload) error {
		if v.sh == nil {
			return errLocalOnly
		}
		next := profileState{Name: name, Photo: p.ProfilePhoto, PhotoType: p.ProfilePhotoType}
		if setPhoto {
			next.Photo, next.PhotoType = photo, kind
		}
		blob, err := v.sh.putBlob(v.sharedKeyLocked(), "profile", next)
		if err != nil {
			return err
		}
		v.sh.Profile = blob
		v.rebuildShareLocked()
		return nil
	})
}

type DeviceInfo struct {
	ID       string
	Name     string
	Self     bool
	SyncedAt time.Time
}

func (v *Vault) Devices() ([]DeviceInfo, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.sh == nil {
		return nil, errLocalOnly
	}
	key := v.sharedKeyLocked()
	if key == nil {
		return nil, errLocked
	}
	out := make([]DeviceInfo, 0, len(v.sh.Devices))
	for _, d := range v.sh.Devices {
		if d.Removed {
			continue
		}
		out = append(out, DeviceInfo{
			ID:       d.ID,
			Name:     cmp.Or(v.sh.deviceName(key, d), "RenVault"),
			Self:     d.ID == v.sh.Node,
			SyncedAt: v.sh.Peers[d.ID].SyncedAt,
		})
	}
	slices.SortStableFunc(out, func(a, b DeviceInfo) int {
		if a.Self != b.Self {
			if a.Self {
				return -1
			}
			return 1
		}
		return strings.Compare(strings.ToLower(a.Name), strings.ToLower(b.Name))
	})
	return out, nil
}

func (v *Vault) RemoveDevice(id string) error {
	return v.mutate(func(*payload) error {
		if v.sh == nil {
			return errLocalOnly
		}
		i := v.sh.device(id)
		if i < 0 || id == v.sh.Node {
			return errNotFound
		}
		key := v.sharedKeyLocked()
		d := v.sh.Devices[i]
		name := v.sh.deviceName(key, d)
		d.Removed = true
		delete(v.sh.Peers, id)
		return v.sh.putDevice(key, d, name)
	})
}
