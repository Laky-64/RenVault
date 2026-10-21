package agent

import "context"

type Service struct {
	agent *Agent
}

func NewService(a *Agent) *Service {
	return &Service{agent: a}
}

func (s *Service) Supported() bool {
	return Supported() && s.agent != nil
}

func (s *Service) Reason() string {
	return Reason()
}

func (s *Service) Authenticate(ctx context.Context) error {
	if !s.Supported() {
		return ErrUnsupported
	}
	return s.agent.Run(ctx)
}
