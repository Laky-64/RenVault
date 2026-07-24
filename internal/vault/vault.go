package vault

import (
	"errors"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/Laky-64/RenVault/internal/apple"
	"github.com/Laky-64/appleservices"
	"github.com/Laky-64/appleservices/keychain"
)

var errLocked = errors.New("vault: locked")

func zeroPayloadSecrets(p *payload) {
	if p == nil {
		return
	}
	zero(p.Peer.PrivateKey)
}

type Vault struct {
	base string

	mu       sync.Mutex
	vf       *vaultFile
	k        []byte
	p        *payload
	store    *appleStore
	sess     *apple.Session
	pending  appleservices.Credentials
	autoLock time.Duration
	timer    *time.Timer
	timerGen uint64
}

func New(base string) *Vault {
	st := newAppleStore(base)
	return &Vault{base: base, store: st, sess: apple.NewSession(st)}
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
	_ = lockMemory(k)

	v.mu.Lock()
	defer v.mu.Unlock()
	if v.k != nil {
		zero(v.k)
	}
	zeroPayloadSecrets(v.p)
	v.vf = &vf
	v.k = k
	pc := p
	v.p = &pc
	v.resetTimerLocked()
	return nil
}

func (v *Vault) SetAutoLock(d time.Duration) {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.autoLock = d
	v.resetTimerLocked()
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
	v.sess.Close()
	v.store.clear()
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

type keychainSource interface {
	WebPasswords() ([]keychain.WebPassword, error)
	WiFiPasswords() ([]keychain.WiFiPassword, error)
}

var _ keychainSource = (*appleservices.KeychainVault)(nil)

func (v *Vault) saveLocked() error {
	if v.vf == nil || v.k == nil || v.p == nil {
		return errLocked
	}
	nvf, err := v.vf.reencryptPayload(v.k, *v.p)
	if err != nil {
		return err
	}
	if err := saveVaultFile(v.base, nvf); err != nil {
		return err
	}
	v.vf = &nvf
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

	peer, ok := v.sess.TakePeer()
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
	if sess, ok := v.store.take(); ok {
		p.Session = sess
	}
	p.SyncedAt = time.Now().UTC()

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
	v.resetTimerLocked()
	return nil
}

func (v *Vault) Sync() (bool, error) {
	v.mu.Lock()
	if v.p == nil || v.k == nil {
		v.mu.Unlock()
		return false, errLocked
	}
	origP := v.p
	creds := v.p.Credentials
	peer := v.p.Peer
	session := v.p.Session
	v.mu.Unlock()

	v.store.set(session)
	v.sess.SetCredentials(creds)

	src, err := v.sess.Keychain(peer)
	if errors.Is(err, apple.ErrTwoFactorRequired) {
		return true, nil
	}
	if err != nil {
		return false, err
	}

	local := &payload{}
	if err := fetchCaches(src, local); err != nil {
		return false, err
	}

	prof, err := v.sess.LoadProfile()
	if errors.Is(err, apple.ErrTwoFactorRequired) {
		return true, nil
	}
	if err != nil {
		return false, err
	}
	local.ProfileName = prof.Name
	local.ProfilePhoto = prof.Photo
	local.ProfilePhotoType = prof.PhotoType

	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil || v.p != origP {
		return false, errLocked
	}
	copyCaches(v.p, local)
	if sess, ok := v.store.take(); ok {
		v.p.Session = sess
	}
	v.p.SyncedAt = time.Now().UTC()
	return false, v.saveLocked()
}

func (v *Vault) ProfileInfo() ProfileMeta {
	v.mu.Lock()
	p := v.p
	if p != nil {
		m := ProfileMeta{Name: p.ProfileName, HasPhoto: len(p.ProfilePhoto) > 0}
		v.mu.Unlock()
		return m
	}
	v.mu.Unlock()
	if pr, ok := v.sess.Profile(); ok {
		return ProfileMeta{Name: pr.Name, HasPhoto: len(pr.Photo) > 0}
	}
	return ProfileMeta{}
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

func (v *Vault) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasPrefix(r.URL.Path, "/profile") {
			next.ServeHTTP(w, r)
			return
		}
		photo, ptype := v.photoBytes()
		if len(photo) == 0 {
			http.Error(w, "no profile photo", http.StatusNotFound)
			return
		}
		if ptype == "" {
			ptype = http.DetectContentType(photo)
		}
		w.Header().Set("Content-Type", ptype)
		w.Header().Set("Content-Length", fmt.Sprint(len(photo)))
		w.Header().Set("Cache-Control", "no-cache")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Content-Security-Policy", "default-src 'none'; sandbox")
		_, _ = w.Write(photo)
	})
}

func fetchCaches(src keychainSource, p *payload) error {
	web, err := src.WebPasswords()
	if err != nil {
		return err
	}
	wifi, err := src.WiFiPasswords()
	if err != nil {
		return err
	}
	p.Web = web
	p.WiFi = wifi
	return nil
}

func copyCaches(dst, src *payload) {
	dst.Web = src.Web
	dst.WiFi = src.WiFi
	dst.ProfileName = src.ProfileName
	dst.ProfilePhoto = src.ProfilePhoto
	dst.ProfilePhotoType = src.ProfilePhotoType
}
