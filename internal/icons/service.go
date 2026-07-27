package icons

import (
	"image"
	"sync"
	"time"

	"github.com/Laky-64/http"
)

const (
	hitTTL  = 7 * 24 * time.Hour
	missTTL = 30 * time.Minute

	fetchTimeout = 10 * time.Second
	maxEntries   = 512
)

type entry struct {
	png     []byte
	err     error
	expires time.Time
	ready   chan struct{}
}

type IconService struct {
	mu    sync.Mutex
	cache map[string]*entry
}

func New() *IconService {
	return &IconService{cache: make(map[string]*entry)}
}

func (s *IconService) tile(src string) ([]byte, error) {
	for {
		s.mu.Lock()
		if e, ok := s.cache[src]; ok {
			if e.ready != nil {
				ready := e.ready
				s.mu.Unlock()
				<-ready
				continue
			}
			if time.Now().Before(e.expires) {
				png, err := e.png, e.err
				s.mu.Unlock()
				return png, err
			}
		}

		e := &entry{ready: make(chan struct{})}
		s.cache[src] = e
		s.evictLocked()
		s.mu.Unlock()

		png, err := s.fetch(src)

		s.mu.Lock()
		ttl := hitTTL
		if err != nil {
			ttl = missTTL
		}
		e.png, e.err, e.expires = png, err, time.Now().Add(ttl)
		ready := e.ready
		e.ready = nil
		s.mu.Unlock()

		close(ready)
		return png, err
	}
}

func (s *IconService) evictLocked() {
	if len(s.cache) <= maxEntries {
		return
	}
	now := time.Now()
	for k, e := range s.cache {
		if e.ready == nil && now.After(e.expires) {
			delete(s.cache, k)
		}
	}
	for k, e := range s.cache {
		if len(s.cache) <= maxEntries {
			return
		}
		if e.ready == nil {
			delete(s.cache, k)
		}
	}
}

func (s *IconService) fetch(src string) ([]byte, error) {
	favicon, err := s.getFavicon(src)
	if err != nil {
		return nil, err
	}
	return process(favicon)
}

func (s *IconService) getFavicon(url string) (image.Image, error) {
	request, err := http.ExecuteRequest(url, http.Timeout(fetchTimeout))
	if err != nil {
		return nil, err
	}
	return decode(request.Body)
}
