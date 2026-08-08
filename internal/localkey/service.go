package localkey

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/Laky-64/RenVault/internal/ctap"
	"github.com/Laky-64/RenVault/internal/ctaphid"
	"github.com/Laky-64/RenVault/internal/vault"
	"github.com/wailsapp/wails/v3/pkg/application"
)

const (
	RequestEvent   = "localkey:request"
	ResolvedEvent  = "localkey:resolved"
	deviceName     = "RenVault Passkey"
	approvalWindow = 60 * time.Second
)

var (
	errLocked  = errors.New("localkey: the vault is locked")
	errDenied  = errors.New("localkey: the request was declined")
	errExpired = errors.New("localkey: the request expired")
)

type Vault interface {
	ListPasskey() []vault.PasskeyMeta
	SignAssertion(id string, clientDataHash []byte, userVerified bool, signCount uint32) (vault.Assertion, error)
	Unlocked() bool
}

type Request struct {
	ID           string    `json:"id"`
	RelyingParty string    `json:"relyingParty"`
	Accounts     []Account `json:"accounts"`
}

type Account struct {
	Label string `json:"label"`
	Title string `json:"title"`
}

type Service struct {
	vault Vault

	mu      sync.Mutex
	device  *ctaphid.UHID
	stop    context.CancelFunc
	waiting map[string]chan int
}

func NewService(v Vault) *Service {
	return &Service{vault: v, waiting: map[string]chan int{}}
}

func (s *Service) Supported() bool {
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

	server := ctaphid.NewServer(device, ctap.NewAuthenticator(store{v: s.vault}, s.approve, ctap.TransportsUSB))
	go func() {
		_ = server.Serve(ctx)
		s.Stop()
	}()
	return nil
}

func (s *Service) Stop() error {
	s.mu.Lock()
	device, stop := s.device, s.stop
	s.device, s.stop = nil, nil
	for id, ch := range s.waiting {
		close(ch)
		delete(s.waiting, id)
	}
	s.mu.Unlock()

	if stop != nil {
		stop()
	}
	if device != nil {
		return device.Close()
	}
	return nil
}

func (s *Service) Approve(id string, account int) {
	s.resolve(id, account)
}

func (s *Service) Deny(id string) {
	s.resolve(id, -1)
}

func (s *Service) resolve(id string, account int) {
	s.mu.Lock()
	ch, ok := s.waiting[id]
	if ok {
		delete(s.waiting, id)
	}
	s.mu.Unlock()
	if ok {
		ch <- account
		close(ch)
	}
}

func (s *Service) approve(ctx context.Context, relyingParty string, options []ctap.Credential) (ctap.Credential, error) {
	if !s.vault.Unlocked() {
		return ctap.Credential{}, errLocked
	}
	id, err := newID()
	if err != nil {
		return ctap.Credential{}, err
	}

	reply := make(chan int, 1)
	s.mu.Lock()
	s.waiting[id] = reply
	s.mu.Unlock()
	defer s.resolve(id, -1)

	app := application.Get()
	if app == nil {
		return ctap.Credential{}, errors.New("localkey: no application to ask")
	}
	app.Event.Emit(RequestEvent, Request{
		ID:           id,
		RelyingParty: relyingParty,
		Accounts:     accountsOf(options),
	})

	select {
	case account, open := <-reply:
		app.Event.Emit(ResolvedEvent, id)
		if !open || account < 0 || account >= len(options) {
			return ctap.Credential{}, errDenied
		}
		return options[account], nil
	case <-time.After(approvalWindow):
		app.Event.Emit(ResolvedEvent, id)
		return ctap.Credential{}, errExpired
	case <-ctx.Done():
		app.Event.Emit(ResolvedEvent, id)
		return ctap.Credential{}, ctx.Err()
	}
}

func accountsOf(options []ctap.Credential) []Account {
	out := make([]Account, 0, len(options))
	for _, option := range options {
		label := option.UserName
		if label == "" {
			label = option.DisplayName
		}
		out = append(out, Account{Label: label, Title: option.RelyingParty})
	}
	return out
}

func newID() (string, error) {
	var buf [16]byte
	if _, err := rand.Read(buf[:]); err != nil {
		return "", fmt.Errorf("localkey: request id: %w", err)
	}
	return hex.EncodeToString(buf[:]), nil
}
