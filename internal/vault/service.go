package vault

import (
	"time"

	"github.com/Laky-64/RenVault/internal/apple"
	"github.com/wailsapp/wails/v3/pkg/application"
)

const (
	LockedEvent     = "vault:locked"
	SyncedEvent     = "vault:synced"
	defaultAutoLock = 5 * time.Minute
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
	v.SetAutoLock(defaultAutoLock)
	v.SetOnLock(func() {
		if app := application.Get(); app != nil {
			app.Event.Emit(LockedEvent)
		}
	})
	v.SetOnSync(func() {
		if app := application.Get(); app != nil {
			app.Event.Emit(SyncedEvent)
		}
	})
	go changeURLIndex()
	return &Service{v: v}
}

func (s *Service) Settings() Settings {
	return s.v.Settings()
}

func (s *Service) SetSortPreference(field string, ascending bool) error {
	return s.v.SetSort(field, ascending)
}

func (s *Service) AutoLockMinutes() int {
	return int(s.v.AutoLock() / time.Minute)
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

func (s *Service) ChangePasswordURL(domain string) string {
	return changePasswordURL(domain)
}

func (s *Service) CheckPwned() (PwnedReport, error) {
	return s.v.CheckPwned()
}

func (s *Service) PwnedInfo() PwnedReport {
	return s.v.PwnedInfo()
}

func (s *Service) ListPasskey() []PasskeyMeta {
	return s.v.ListPasskey()
}

func (s *Service) SignAssertion(id string, clientDataHash []byte, userVerified bool, signCount uint32) (Assertion, error) {
	return s.v.SignAssertion(id, clientDataHash, userVerified, signCount)
}

func (s *Service) SyncState() SyncStatus {
	return s.v.SyncState()
}

func (s *Service) AddPassword(title, website, username, password, note string) error {
	return s.v.AddPassword(title, website, username, password, note)
}

func (s *Service) EditPassword(id, title, website, username, password, note, totpURL string, domains []string, dropTOTP bool) (string, error) {
	return s.v.EditPassword(id, title, website, username, password, note, totpURL, domains, dropTOTP)
}

func (s *Service) DeletePassword(id string) error {
	return s.v.DeletePassword(id)
}

func (s *Service) DeletePasswords(ids []string) error {
	return s.v.DeletePasswords(ids)
}

func (s *Service) RestorePassword(id string) error {
	return s.v.RestorePassword(id)
}

func (s *Service) PurgePassword(id string) error {
	return s.v.PurgePassword(id)
}

func (s *Service) PurgePasswords(ids []string) error {
	return s.v.PurgePasswords(ids)
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

func (s *Service) ProfileInfo() ProfileMeta {
	return s.v.ProfileInfo()
}

func (s *Service) Middleware() application.Middleware {
	return s.v.Middleware
}

func (s *Service) SetAutoLockMinutes(m int) {
	s.v.SetAutoLock(time.Duration(m) * time.Minute)
}
