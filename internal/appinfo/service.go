package appinfo

const (
	Name        = "RenVault"
	Description = "An unofficial cross-platform Apple Passwords client for managing your credentials securely"
)

type Service struct{}

func NewService() *Service { return &Service{} }

func (s *Service) ServiceName() string { return "AppInfo" }

func (s *Service) AppName() string { return Name }

func (s *Service) AppDescription() string { return Description }
