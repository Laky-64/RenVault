package icons

import (
	"image"

	"github.com/Laky-64/http"
)

type IconService struct {
}

func New() *IconService {
	return &IconService{}
}

func (s *IconService) tile(src string) ([]byte, error) {
	favicon, err := s.getFavicon(src)
	if err != nil {
		return nil, err
	}
	return process(favicon)
}

func (s *IconService) getFavicon(url string) (image.Image, error) {
	request, err := http.ExecuteRequest(url)
	if err != nil {
		return nil, err
	}
	return decode(request.Body)
}
