package apple

import (
	"github.com/Laky-64/appleservices"
	"github.com/Laky-64/appleservices/anisette"
)

func (s *Session) SetAnisetteServer(url string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.anisette = url
}

func (s *Session) beginLocked() (*appleservices.Login, error) {
	if s.anisette == "" {
		return appleservices.BeginLogin(s.creds, s.store)
	}

	dev, err := s.store.LoadDevice()
	if err != nil {
		return nil, err
	}
	state := anisette.NewState()
	if dev != nil && len(dev.Identifier) == 16 {
		state = anisette.State{Identifier: dev.Identifier, AdiPB: dev.ProvisioningBlob}
	}
	provider := anisette.NewProviderFromState(state, []string{s.anisette})

	l, err := appleservices.BeginLogin(s.creds, s.store, appleservices.WithAnisette(provider))
	if err != nil {
		return nil, err
	}

	if kept := provider.State(); len(kept.Identifier) != 0 {
		if err := s.store.SaveDevice(&appleservices.Device{Identifier: kept.Identifier, ProvisioningBlob: kept.AdiPB}); err != nil {
			return nil, err
		}
	}
	return l, nil
}
