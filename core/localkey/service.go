package localkey

import (
	"context"
	"errors"
	"sync"

	"github.com/Laky-64/renvault-core/ctap"
	"github.com/Laky-64/renvault-core/ctaphid"
	"github.com/Laky-64/renvault-core/vault"
)

const deviceName = "RenVault Passkey"

var errLocked = errors.New("localkey: the vault is locked")

type Vault interface {
	ListPasskey() []vault.PasskeyMeta
	SignAssertion(id string, clientDataHash []byte, userVerified bool, signCount uint32) (vault.Assertion, error)
	CreatePasskey(rp, userName, displayName string, userHandle []byte, userVerified bool) (vault.Registration, error)
	Unlocked() bool
}

type Service struct {
	vault   Vault
	choose  ctap.Chooser
	approve ctap.Approver

	mu     sync.Mutex
	device *ctaphid.UHID
	stop   context.CancelFunc
}

func NewService(v Vault, choose ctap.Chooser, approve ctap.Approver) *Service {
	return &Service{vault: v, choose: choose, approve: approve}
}

func Supported() bool {
	return ctaphid.Present()
}

func Permitted() bool {
	return ctaphid.Available()
}

func (s *Service) Running() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.device != nil
}

func (s *Service) Start() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.device != nil {
		return nil
	}
	if !s.vault.Unlocked() {
		return errLocked
	}
	device, err := ctaphid.Open(deviceName)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithCancel(context.Background())
	s.device, s.stop = device, cancel

	choose := func(ctx context.Context, rp string, options []ctap.Credential) (ctap.Credential, error) {
		if !s.vault.Unlocked() {
			return ctap.Credential{}, errLocked
		}
		return s.choose(ctx, rp, options)
	}
	approve := func(ctx context.Context, c ctap.NewCredential) error {
		if !s.vault.Unlocked() {
			return errLocked
		}
		return s.approve(ctx, c)
	}
	server := ctaphid.NewServer(device, ctap.NewAuthenticator(store{v: s.vault}, choose, approve, ctap.TransportsUSB))
	go func() {
		_ = server.Serve(ctx)
		s.mu.Lock()
		mine := s.device == device
		s.mu.Unlock()
		if mine {
			_ = s.Stop()
		}
	}()
	return nil
}

func (s *Service) Stop() error {
	s.mu.Lock()
	device, stop := s.device, s.stop
	s.device, s.stop = nil, nil
	s.mu.Unlock()

	if stop != nil {
		stop()
	}
	if device != nil {
		return device.Close()
	}
	return nil
}
