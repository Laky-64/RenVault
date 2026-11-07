package vault

import (
	"context"
	"errors"
	"log"
	"slices"
	"strings"
	"sync"
	"time"

	"github.com/Laky-64/appleservices"
	"github.com/Laky-64/appleservices/keychain"
	"github.com/Laky-64/renvault-core/apple"
)

var errLocked = errors.New("vault: locked")

func zeroPayloadSecrets(p *payload) {
	if p == nil {
		return
	}
	zero(p.Peer.PrivateKey)
	if p.Local != nil {
		zero(p.Local.Key)
		zero(p.Local.Identity)
	}
	for _, op := range p.Outbox {
		zeroOp(op)
	}
	for i := range p.Passkey {
		zero(p.Passkey[i].PrivateKeyD)
	}
}

type Vault struct {
	base string

	mu        sync.Mutex
	freshMu   sync.Mutex
	vf        *vaultFile
	k         []byte
	p         *payload
	store     *appleStore
	sess      *apple.Session
	pending   appleservices.Credentials
	autoLock  time.Duration
	timer     *time.Timer
	timerGen  uint64
	onLock    func()
	onSync    func()
	onProfile func()
	onPeers   func()
	ips       []string
	seen      map[string]seenHost
	about     deviceAbout
	pairing   *pairing
	sh        *share
	net       *peerNet
	recIDs    []string
	keyIDs    []string
	syncer    *syncer
	inflight  uint64
	lastErr   error
}

func New(base string) *Vault {
	st := newAppleStore(base)
	sess := apple.NewSession(st)
	sess.SetAnisetteServer(loadAnisette(base))
	v := &Vault{base: base, store: st, sess: sess}
	if sh, err := loadShare(base); err != nil {
		log.Printf("vault: local vault: %v", err)
	} else if sh != nil {
		v.sh = sh
		v.startNetLocked()
	}
	return v
}

func (v *Vault) Base() string {
	return v.base
}

func (v *Vault) attachLocked() {
	if v.sh == nil {
		v.startSyncLocked(false)
		return
	}
	if v.sh.identity == nil && v.p.Local != nil {
		v.sh.identity = slices.Clone(v.p.Local.Identity)
	}
	v.rebuildShareLocked()
	v.startNetLocked()
}

func (v *Vault) Configured() bool {
	_, ok, err := loadVaultFile(v.base)
	return err == nil && ok
}

func (v *Vault) Unlocked() bool {
	v.mu.Lock()
	defer v.mu.Unlock()
	return v.k != nil
}

func (v *Vault) UnlockWithPassword(masterPassword string) error {
	vf, ok, err := loadVaultFile(v.base)
	if err != nil {
		return err
	}
	if !ok {
		return errNoVault
	}
	pw := []byte(masterPassword)
	defer zero(pw)
	k, p, err := vf.unlockWithPassword(pw)
	if err != nil {
		return err
	}
	v.mu.Lock()
	defer v.mu.Unlock()
	v.adoptLocked(vf, k, p)
	v.renewQuickLocked()
	return nil
}

func (v *Vault) adoptLocked(vf vaultFile, k []byte, p payload) {
	_ = lockMemory(k)
	if v.k != nil {
		zero(v.k)
	}
	zeroPayloadSecrets(v.p)
	v.vf = &vf
	v.k = k
	v.p = &p
	prunePwned(v.p)
	v.applyAutoLockLocked()
	v.resetTimerLocked()
	v.attachLocked()
}

func (v *Vault) SetOnLock(fn func()) {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.onLock = fn
}

func (v *Vault) SetOnSync(fn func()) {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.onSync = fn
}

func (v *Vault) SetOnProfile(fn func()) {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.onProfile = fn
}

func (v *Vault) SetOnPeers(fn func()) {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.onPeers = fn
}

func (v *Vault) notify() {
	v.mu.Lock()
	fn := v.onSync
	v.mu.Unlock()
	if fn != nil {
		fn()
	}
	go v.checkFresh()
}

func (v *Vault) resetTimerLocked() {
	v.timerGen++
	if v.timer != nil {
		v.timer.Stop()
		v.timer = nil
	}
	if v.autoLock <= 0 || v.k == nil {
		return
	}
	gen := v.timerGen
	v.timer = time.AfterFunc(v.autoLock, func() { v.autoLockFire(gen) })
}

func (v *Vault) autoLockFire(gen uint64) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.timerGen != gen {
		return
	}
	v.lockLocked()
}

func (v *Vault) Lock() {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.lockLocked()
}

func (v *Vault) lockLocked() {
	wasUnlocked := v.k != nil
	if v.timer != nil {
		v.timer.Stop()
		v.timer = nil
	}
	if v.k != nil {
		zero(v.k)
		v.k = nil
	}
	zeroPayloadSecrets(v.p)
	v.p = nil
	v.vf = nil
	v.pending = appleservices.Credentials{}
	v.pairing = nil
	v.recIDs = nil
	v.keyIDs = nil
	v.sess.Close()
	v.store.clear()
	v.stopSyncLocked()
	v.inflight = 0
	if wasUnlocked && v.onLock != nil {
		go v.onLock()
	}
}

func (v *Vault) ListWeb() []WebMeta {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return nil
	}
	v.resetTimerLocked()
	return webMetas(*v.p)
}

func (v *Vault) ListWiFi() []WiFiMeta {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return nil
	}
	v.resetTimerLocked()
	return wifiMetas(*v.p)
}

func (v *Vault) ListPasskey() []PasskeyMeta {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return nil
	}
	v.resetTimerLocked()
	return passkeyMetas(*v.p)
}

func (v *Vault) GetPassword(id string) (string, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return "", errLocked
	}
	v.resetTimerLocked()
	if w, ok := findWeb(*v.p, id); ok {
		return w.Password, nil
	}
	if w, ok := findWiFi(*v.p, id); ok {
		return w.Password, nil
	}
	return "", errors.New("vault: entry not found")
}

func (v *Vault) GetTOTP(id string) (string, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return "", errLocked
	}
	v.resetTimerLocked()
	w, ok := findWeb(*v.p, id)
	if !ok {
		return "", errors.New("vault: entry not found or has no TOTP")
	}
	if w.TOTP == "" {
		return "", errors.New("vault: entry has no TOTP")
	}
	return w.TOTPCode(time.Now())
}

type PwnedReport struct {
	Checked   int       `json:"checked"`
	Pwned     int       `json:"pwned"`
	CheckedAt time.Time `json:"checkedAt"`
}

func (v *Vault) PwnedInfo() PwnedReport {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return PwnedReport{}
	}
	checked := 0
	for _, w := range v.p.Web {
		if !w.IsDeleted && w.Password != "" {
			checked++
		}
	}
	return PwnedReport{Checked: checked, Pwned: leakedCount(v.p), CheckedAt: v.p.PwnedAt}
}

func (v *Vault) CheckPwned() (PwnedReport, error) {
	v.mu.Lock()
	if v.p == nil || v.k == nil {
		v.mu.Unlock()
		return PwnedReport{}, errLocked
	}
	origP := v.p
	v.resetTimerLocked()
	checked := livePasswords(v.p)
	v.mu.Unlock()

	hashes := make([]string, 0, len(checked))
	for _, h := range checked {
		hashes = append(hashes, h)
	}
	ctx, cancel := context.WithTimeout(context.Background(), pwnedTimeout)
	defer cancel()
	hit, err := lookupPwned(ctx, hashes)
	if err != nil {
		return PwnedReport{}, err
	}

	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil || v.p != origP {
		return PwnedReport{}, errLocked
	}
	v.resetTimerLocked()
	recordPwned(v.p, checked, hit)
	v.p.PwnedAt = time.Now().UTC()
	if err := v.saveLocked(); err != nil {
		return PwnedReport{}, err
	}
	return PwnedReport{Checked: len(checked), Pwned: leakedCount(v.p), CheckedAt: v.p.PwnedAt}, nil
}

const (
	flagBackupEligible = 0x08
	flagBackupState    = 0x10
)

type Assertion struct {
	AuthenticatorData []byte `json:"authenticatorData"`
	Signature         []byte `json:"signature"`
	UserHandle        []byte `json:"userHandle"`
}

func (v *Vault) SignAssertion(id string, clientDataHash []byte, userPresent, userVerified bool, signCount uint32) (Assertion, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return Assertion{}, errLocked
	}
	v.resetTimerLocked()
	e, ok := findPasskey(*v.p, id)
	if !ok {
		return Assertion{}, errors.New("vault: passkey not found")
	}
	pk, err := e.passkey()
	if err != nil {
		return Assertion{}, err
	}
	flags := byte(flagBackupEligible | flagBackupState)
	if userPresent {
		flags |= byte(keychain.FlagUserPresent)
		if userVerified {
			flags |= keychain.FlagUserVerified
		}
	}
	authData, sig, err := pk.Sign(clientDataHash, flags, signCount)
	if err != nil {
		return Assertion{}, err
	}
	return Assertion{AuthenticatorData: authData, Signature: sig, UserHandle: e.UserHandle}, nil
}

type keychainSource interface {
	Items() ([]keychain.Item, error)
	WiFiPasswords() ([]keychain.WiFiPassword, error)
}

var _ keychainSource = (*appleservices.KeychainVault)(nil)

func (v *Vault) saveLocked() error {
	if v.vf == nil || v.k == nil || v.p == nil {
		return errLocked
	}
	stored := *v.p
	if v.sh != nil {
		if err := saveShare(v.base, v.sh); err != nil {
			return err
		}
		stored.Web = nil
		stored.Passkey = nil
		stored.Names = nil
		stored.ProfileName = ""
		stored.ProfilePhoto = nil
		stored.ProfilePhotoType = ""
	}
	nvf, err := v.vf.reencryptPayload(v.k, stored)
	if err != nil {
		return err
	}
	if err := saveVaultFile(v.base, nvf); err != nil {
		return err
	}
	v.vf = &nvf
	return nil
}

func (v *Vault) ChangeMasterPassword(current, next string) error {
	if strings.TrimSpace(next) == "" {
		return errors.New("vault: the new master password is empty")
	}
	if next == current {
		return errors.New("vault: the new master password is the same as the current one")
	}
	cur := []byte(current)
	defer zero(cur)
	nxt := []byte(next)
	defer zero(nxt)

	v.mu.Lock()
	defer v.mu.Unlock()
	if v.vf == nil || v.k == nil || v.p == nil {
		return errLocked
	}
	blob, ok := v.vf.passwordWrap()
	if !ok {
		return errors.New("vault: no password wrap in header")
	}
	kek := deriveKEK(cur, v.vf.Header.Salt, v.vf.Header.Argon)
	_ = lockMemory(kek)
	check, err := unwrapKey(kek, blob)
	zero(kek)
	if err != nil {
		return err
	}
	zero(check)

	salt, err := newSalt()
	if err != nil {
		return err
	}
	argon := defaultArgon()
	nkek := deriveKEK(nxt, salt, argon)
	defer zero(nkek)
	_ = lockMemory(nkek)
	wrapped, err := wrapKey(nkek, v.k)
	if err != nil {
		return err
	}

	prev := v.vf.Header
	updated := prev
	updated.Salt = salt
	updated.Argon = argon
	updated.Wraps = make([]wrappedKey, 0, len(prev.Wraps))
	for _, w := range prev.Wraps {
		if w.Method == methodPassword {
			w.Blob = wrapped
		}
		updated.Wraps = append(updated.Wraps, w)
	}
	v.vf.Header = updated
	if err := v.saveLocked(); err != nil {
		v.vf.Header = prev
		return err
	}
	return nil
}

func (v *Vault) StartLogin(appleID, password string) (bool, error) {
	creds := appleservices.Credentials{AppleID: appleID, Password: password}
	v.mu.Lock()
	v.pending = creds
	v.mu.Unlock()
	v.store.clear()
	return v.sess.Start(creds)
}

func (v *Vault) RequestCode() error {
	return v.sess.RequestCode()
}

func (v *Vault) SubmitCode(code string) error {
	return v.sess.SubmitCode(code)
}

func (v *Vault) Bottles() ([]apple.BottleInfo, error) {
	return v.sess.Bottles()
}

func (v *Vault) RecoverBottle(index int, passcode string) error {
	pc := []byte(passcode)
	defer zero(pc)
	return v.sess.RecoverBottle(index, pc)
}

func (v *Vault) FinishSetup(masterPassword string) error {
	v.mu.Lock()
	creds := v.pending
	v.mu.Unlock()
	if creds.AppleID == "" {
		return apple.ErrNoLogin
	}

	peer, ok := v.sess.CopyPeer()
	if !ok {
		return apple.ErrNoPeer
	}
	p := &payload{Credentials: creds, Peer: peer}

	src, err := v.sess.Keychain(peer)
	if err != nil {
		zeroPayloadSecrets(p)
		return err
	}
	if err := fetchCaches(src, p); err != nil {
		zeroPayloadSecrets(p)
		return err
	}
	pr, ok := v.sess.Profile()
	if !ok {
		zeroPayloadSecrets(p)
		return apple.ErrNoProfile
	}
	p.ProfileName = pr.Name
	p.ProfilePhoto = pr.Photo
	p.ProfilePhotoType = pr.PhotoType
	p.Session, _ = v.store.LoadSession()
	p.SyncedAt = time.Now().UTC()
	if err := v.install(masterPassword, p, true); err != nil {
		return err
	}
	v.store.take()
	v.sess.ClearPeer()
	return nil
}

func (v *Vault) install(masterPassword string, p *payload, fresh bool) error {
	k, err := genKey()
	if err != nil {
		zeroPayloadSecrets(p)
		return err
	}
	mpw := []byte(masterPassword)
	defer zero(mpw)
	vf, err := newVaultFile(mpw, k, *p)
	if err != nil {
		zero(k)
		zeroPayloadSecrets(p)
		return err
	}
	if err := saveVaultFile(v.base, vf); err != nil {
		zero(k)
		zeroPayloadSecrets(p)
		return err
	}
	_ = lockMemory(k)

	v.mu.Lock()
	defer v.mu.Unlock()
	if v.k != nil {
		zero(v.k)
	}
	zeroPayloadSecrets(v.p)
	v.vf = &vf
	v.k = k
	v.p = p
	prunePwned(v.p)
	v.applyAutoLockLocked()
	v.resetTimerLocked()
	if v.sh != nil {
		v.attachLocked()
	} else {
		v.startSyncLocked(fresh)
	}
	return nil
}

func (v *Vault) Sync() (bool, error) {
	if v.Local() {
		return false, v.syncPeers()
	}
	src, err := v.keychainSession()
	if errors.Is(err, apple.ErrTwoFactorRequired) {
		return true, nil
	}
	if err != nil {
		return false, err
	}
	if err := v.syncAll(src); errors.Is(err, apple.ErrTwoFactorRequired) {
		return true, nil
	} else if err != nil {
		return false, err
	}
	return false, nil
}

func (v *Vault) syncAll(src *appleservices.KeychainVault) error {
	v.mu.Lock()
	origP := v.p
	v.mu.Unlock()
	if origP == nil {
		return errLocked
	}

	local := &payload{}
	if err := fetchCaches(src, local); err != nil {
		return err
	}
	prof, err := v.sess.LoadProfile()
	if err != nil {
		return err
	}
	local.ProfileName = prof.Name
	local.ProfilePhoto = prof.Photo
	local.ProfilePhotoType = prof.PhotoType

	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil || v.k == nil || v.p != origP {
		return errLocked
	}
	if len(v.p.Outbox) > 0 {
		return nil
	}
	copyCaches(v.p, local)
	if sess, ok := v.store.take(); ok {
		v.p.Session = sess
	}
	v.p.SyncedAt = time.Now().UTC()
	return v.saveLocked()
}

func (v *Vault) refreshWeb(src *appleservices.KeychainVault) error {
	v.mu.Lock()
	origP := v.p
	v.mu.Unlock()
	if origP == nil {
		return errLocked
	}

	items, err := src.Items()
	if err != nil {
		return err
	}
	web := keychain.WebPasswords(items)
	passkey := passkeyEntries(items)

	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil || v.k == nil || v.p != origP {
		return errLocked
	}
	if len(v.p.Outbox) > 0 {
		return nil
	}
	for i := range v.p.Passkey {
		zero(v.p.Passkey[i].PrivateKeyD)
	}
	before := v.p.Web
	v.p.Web = web
	v.p.Passkey = passkey
	trackHistory(v.p, before)
	prunePwned(v.p)
	if sess, ok := v.store.take(); ok {
		v.p.Session = sess
	}
	v.p.SyncedAt = time.Now().UTC()
	return v.saveLocked()
}

func (v *Vault) ProfileInfo() ProfileMeta {
	v.mu.Lock()
	p := v.p
	pending := v.pending.AppleID
	if p != nil {
		m := ProfileMeta{
			Name:     p.ProfileName,
			AppleID:  p.Credentials.AppleID,
			HasPhoto: len(p.ProfilePhoto) > 0,
			Local:    v.sh != nil,
		}
		v.mu.Unlock()
		return m
	}
	local := v.sh != nil
	v.mu.Unlock()
	if pr, ok := v.sess.Profile(); ok && !local {
		return ProfileMeta{Name: pr.Name, AppleID: pending, HasPhoto: len(pr.Photo) > 0}
	}
	return ProfileMeta{AppleID: pending, Local: local}
}

func (v *Vault) SignOut() error {
	v.leave()
	v.mu.Lock()
	defer v.mu.Unlock()
	var err error
	if v.sh == nil && v.k != nil {
		err = appleservices.Logout(v.store)
	}
	v.lockLocked()
	if v.sh != nil {
		v.stopNetLocked()
		v.sh = nil
		removeShare(v.base)
	}
	if rmErr := removeVaultFile(v.base); rmErr != nil {
		return rmErr
	}
	return err
}

func (v *Vault) photoBytes() ([]byte, string) {
	v.mu.Lock()
	p := v.p
	if p != nil {
		b, t := p.ProfilePhoto, p.ProfilePhotoType
		v.mu.Unlock()
		return cloneBytes(b), t
	}
	v.mu.Unlock()
	if pr, ok := v.sess.Profile(); ok {
		return cloneBytes(pr.Photo), pr.PhotoType
	}
	return nil, ""
}

func cloneBytes(b []byte) []byte {
	if len(b) == 0 {
		return nil
	}
	out := make([]byte, len(b))
	copy(out, b)
	return out
}

func (v *Vault) ProfilePhoto() ([]byte, string) {
	return v.photoBytes()
}

func fetchCaches(src keychainSource, p *payload) error {
	items, err := src.Items()
	if err != nil {
		return err
	}
	wifi, err := src.WiFiPasswords()
	if err != nil {
		return err
	}
	p.Web = keychain.WebPasswords(items)
	p.WiFi = wifi
	p.Passkey = passkeyEntries(items)
	return nil
}

func passkeyEntries(items []keychain.Item) []passkeyEntry {
	found := keychain.Passkeys(items)
	entries := make([]passkeyEntry, 0, len(found))
	for _, k := range found {
		e, err := newPasskeyEntry(k)
		if err != nil {
			continue
		}
		entries = append(entries, e)
	}
	return entries
}

func prunePwned(p *payload) {
	if len(p.Web) == 0 {
		return
	}
	p.Pwned = nil
	live := livePasswords(p)
	p.Clean = slices.DeleteFunc(p.Clean, func(c string) bool { _, ok := live[c]; return !ok })
}

func copyCaches(dst, src *payload) {
	dst.Web = src.Web
	dst.WiFi = src.WiFi
	dst.Passkey = src.Passkey
	dst.ProfileName = src.ProfileName
	dst.ProfilePhoto = src.ProfilePhoto
	dst.ProfilePhotoType = src.ProfilePhotoType
	prunePwned(dst)
}
