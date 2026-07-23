package vault

import (
	"sync"

	"github.com/Laky-64/appleservices"
)

var _ appleservices.Store = (*appleStore)(nil)

type appleStore struct {
	base  string
	mu    sync.Mutex
	sess  *appleservices.Session
	dirty bool
}

func newAppleStore(base string) *appleStore {
	return &appleStore{base: base}
}

func (s *appleStore) LoadDevice() (*appleservices.Device, error) {
	d, err := loadDevice(s.base)
	if err != nil {
		return nil, err
	}
	return &appleservices.Device{Identifier: d.Identifier, ProvisioningBlob: d.ProvisioningBlob}, nil
}

func (s *appleStore) SaveDevice(d *appleservices.Device) error {
	if d == nil {
		return nil
	}
	return saveDevice(s.base, deviceFile{Identifier: d.Identifier, ProvisioningBlob: d.ProvisioningBlob})
}

func (s *appleStore) LoadSession() (*appleservices.Session, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.sess, nil
}

func (s *appleStore) SaveSession(sess *appleservices.Session) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.sess = sess
	s.dirty = true
	return nil
}

func (s *appleStore) set(sess *appleservices.Session) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.dirty {
		return
	}
	s.sess = sess
}

func (s *appleStore) take() (*appleservices.Session, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.dirty {
		return nil, false
	}
	s.dirty = false
	return s.sess, true
}

func (s *appleStore) clear() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.sess = nil
	s.dirty = false
}
