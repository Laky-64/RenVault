//go:build !windows

package winplugin

type Service struct {
	vault Vault
}

func NewService(v Vault, _ Hooks) *Service {
	return &Service{vault: v}
}

func (s *Service) Start() error { return nil }

func (s *Service) Status() Status { return Status{} }

func (s *Service) Install() error { return ErrUnsupported }

func (s *Service) Sync() error { return nil }

func (s *Service) Register() error { return ErrUnsupported }

func (s *Service) Restart() error { return ErrUnsupported }

func OpenSettings() error { return ErrUnsupported }
