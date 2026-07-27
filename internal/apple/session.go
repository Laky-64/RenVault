package apple

import (
	"strings"
	"sync"

	"github.com/Laky-64/appleservices"
)

type Session struct {
	mu      sync.Mutex
	store   appleservices.Store
	creds   appleservices.Credentials
	login   *appleservices.Login
	client  *appleservices.Client
	bottles []appleservices.BottleRef
	peer    *appleservices.PeerKey
	profile *appleservices.Profile
}

func NewSession(store appleservices.Store) *Session {
	return &Session{store: store}
}

func (s *Session) SetCredentials(c appleservices.Credentials) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.creds == c {
		return
	}
	s.resetLoginLocked()
	s.creds = c
}

func (s *Session) Start(c appleservices.Credentials) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.resetLoginLocked()
	s.creds = c
	l, err := appleservices.BeginLogin(c, s.store)
	if err != nil {
		return false, err
	}
	s.login = l
	if l.NeedsTwoFactor() {
		return true, nil
	}
	if err := s.loadProfileLocked(); err != nil {
		return false, err
	}
	return false, nil
}

func (s *Session) RequestCode() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.login == nil {
		return ErrNoLogin
	}
	return s.login.RequestCode()
}

func (s *Session) SubmitCode(code string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.login == nil {
		return ErrNoLogin
	}
	if err := s.login.SubmitCode(code); err != nil {
		return err
	}
	s.client = nil
	if err := s.loadProfileLocked(); err != nil {
		return err
	}
	return nil
}

func (s *Session) Bottles() ([]BottleInfo, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	c, err := s.ensureClientLocked()
	if err != nil {
		return nil, err
	}
	refs, err := c.ViableBottles()
	if err != nil {
		return nil, err
	}
	if len(refs) == 0 {
		return nil, ErrNoBottles
	}
	s.bottles = refs
	out := make([]BottleInfo, len(refs))
	for i, r := range refs {
		out[i] = BottleInfo{
			Index:      i,
			Name:       r.Device.Name,
			Model:      r.Device.Model,
			ShortModel: r.Device.ShortModel,
			Class:      r.Device.Class,
			Serial:     r.Device.Serial,
			Build:      r.Device.Build,
			OS:         r.Device.OS,
			OSVersion:  r.Device.OSVersion,
			ImageURL:   r.Device.ImageURL,
		}
	}
	return out, nil
}

func (s *Session) RecoverBottle(index int, passcode []byte) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if index < 0 || index >= len(s.bottles) {
		return ErrBottleIndex
	}
	c, err := s.ensureClientLocked()
	if err != nil {
		return err
	}
	pk, err := c.RecoverPeer(s.bottles[index], string(passcode))
	if err != nil {
		return err
	}
	s.clearPeerLocked()
	s.peer = &pk
	return nil
}

func (s *Session) loadProfileLocked() error {
	c, err := s.ensureClientLocked()
	if err != nil {
		return err
	}
	p, err := c.Profile()
	if err != nil {
		return err
	}
	s.profile = &p
	return nil
}

func (s *Session) LoadProfile() (appleservices.Profile, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := s.loadProfileLocked(); err != nil {
		return appleservices.Profile{}, err
	}
	return *s.profile, nil
}

func (s *Session) Profile() (appleservices.Profile, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.profile == nil {
		return appleservices.Profile{}, false
	}
	return *s.profile, true
}

func (s *Session) TakePeer() (appleservices.PeerKey, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.peer == nil {
		return appleservices.PeerKey{}, false
	}
	pk := *s.peer
	s.peer = nil
	return pk, true
}

func (s *Session) Keychain(peer appleservices.PeerKey) (*appleservices.KeychainVault, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	c, err := s.ensureClientLocked()
	if err != nil {
		return nil, err
	}
	return c.OpenKeychainWithPeer(peer)
}

func (s *Session) Close() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.resetLoginLocked()
	s.creds = appleservices.Credentials{}
}

func (s *Session) ensureClientLocked() (*appleservices.Client, error) {
	if s.client != nil {
		return s.client, nil
	}
	if s.creds.AppleID == "" {
		return nil, ErrNoLogin
	}
	var lastErr error
	for attempt := 0; attempt < 2; attempt++ {
		if s.login == nil {
			l, err := appleservices.BeginLogin(s.creds, s.store)
			if err != nil {
				return nil, err
			}
			s.login = l
		}
		if s.login.NeedsTwoFactor() {
			return nil, ErrTwoFactorRequired
		}
		c, err := s.login.Client()
		if err == nil {
			s.client = c
			return c, nil
		}
		lastErr = err
		if attempt == 1 {
			break
		}
		if err := s.store.SaveSession(nil); err != nil {
			return nil, err
		}
		s.login = nil
	}
	if isTwoFactor(lastErr) {
		return nil, ErrTwoFactorRequired
	}
	return nil, lastErr
}

func (s *Session) resetLoginLocked() {
	s.login = nil
	s.client = nil
	s.bottles = nil
	s.profile = nil
	s.clearPeerLocked()
}

func (s *Session) clearPeerLocked() {
	if s.peer != nil {
		zero(s.peer.PrivateKey)
		s.peer = nil
	}
}

func isTwoFactor(err error) bool {
	if err == nil {
		return false
	}
	return strings.Contains(err.Error(), "two-factor")
}

func zero(b []byte) {
	for i := range b {
		b[i] = 0
	}
}
