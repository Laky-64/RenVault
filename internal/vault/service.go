package vault

import (
	"time"

	"github.com/Laky-64/RenVault/internal/apple"
)

type Service struct {
	v *Vault
}

func NewService() *Service {
	base, err := storageBase()
	if err != nil {
		panic("vault: cannot resolve storage base: " + err.Error())
	}
	_ = disableCoreDump()
	v := New(base)
	v.SetAutoLock(5 * time.Minute)
	return &Service{v: v}
}

func (s *Service) ServiceName() string { return "Vault" }

func (s *Service) Configured() bool {
	return s.v.Configured()
}

func (s *Service) Unlocked() bool {
	return s.v.Unlocked()
}

func (s *Service) UnlockWithPassword(pw string) error {
	return s.v.UnlockWithPassword(pw)
}

func (s *Service) Lock() {
	s.v.Lock()
}

func (s *Service) ListWeb() []WebMeta {
	return s.v.ListWeb()
}

func (s *Service) ListWiFi() []WiFiMeta {
	return s.v.ListWiFi()
}

func (s *Service) GetPassword(id string) (string, error) {
	return s.v.GetPassword(id)
}

func (s *Service) GetTOTP(id string) (string, error) {
	return s.v.GetTOTP(id)
}

func (s *Service) StartLogin(appleID, password string) (bool, error) {
	return s.v.StartLogin(appleID, password)
}

func (s *Service) RequestCode() error {
	return s.v.RequestCode()
}

func (s *Service) SubmitCode(code string) error {
	return s.v.SubmitCode(code)
}

func (s *Service) Bottles() ([]apple.BottleInfo, error) {
	return s.v.Bottles()
}

func (s *Service) RecoverBottle(index int, passcode string) error {
	return s.v.RecoverBottle(index, passcode)
}

func (s *Service) FinishSetup(masterPassword string) error {
	return s.v.FinishSetup(masterPassword)
}

func (s *Service) Sync() (bool, error) {
	return s.v.Sync()
}

func (s *Service) SetAutoLockMinutes(m int) {
	s.v.SetAutoLock(time.Duration(m) * time.Minute)
}
