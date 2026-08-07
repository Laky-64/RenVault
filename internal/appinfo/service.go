package appinfo

//go:generate go run ../../build/tools/appinfo

type Service struct{}

func NewService() *Service { return &Service{} }

func (s *Service) ServiceName() string { return "AppInfo" }

func (s *Service) AppName() string { return Name }

func (s *Service) AppDescription() string { return Description }

func (s *Service) AppVersion() string { return Version }

func (s *Service) AppIdentifier() string { return Identifier }
