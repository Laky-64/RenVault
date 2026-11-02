package icons

import (
	"errors"
	"fmt"
	"image"
	"net/url"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/Laky-64/http"
)

const (
	hitTTL  = 7 * 24 * time.Hour
	missTTL = 30 * time.Minute

	fetchTimeout = 10 * time.Second
	maxEntries   = 512
)

var (
	ErrUnsupportedURL = errors.New("unsupported image url")
	ErrDisabled       = errors.New("icons: site icons are turned off")
)

type entry struct {
	png     []byte
	err     error
	expires time.Time
	ready   chan struct{}
}

type IconService struct {
	mu       sync.Mutex
	cache    map[string]*entry
	provider atomic.Value
	dir      string
}

func New() *IconService {
	return &IconService{cache: make(map[string]*entry)}
}

const (
	ProviderSite       = "site"
	ProviderDuckDuckGo = "duckduckgo"
	ProviderGoogle     = "google"
	ProviderBitwarden  = "bitwarden"
	ProviderOff        = "off"
)

func (s *IconService) SetProvider(name string) {
	s.provider.Store(name)
}

func (s *IconService) Tile(domain string) ([]byte, error) {
	png, err := s.tileFor(domain)
	if err == nil || errors.Is(err, ErrDisabled) {
		return png, err
	}
	if !strings.HasPrefix(domain, "www.") {
		if png, werr := s.tileFor("www." + domain); werr == nil {
			return png, nil
		}
	}
	if parent, ok := parentDomain(strings.TrimPrefix(domain, "www.")); ok {
		if png, perr := s.tileFor(parent); perr == nil {
			return png, nil
		}
	}
	return nil, err
}

func parentDomain(domain string) (string, bool) {
	labels := strings.Split(strings.TrimSuffix(domain, "."), ".")
	if len(labels) < 3 {
		return "", false
	}
	if len(labels) == 3 && len(labels[1]) <= 3 && len(labels[2]) == 2 {
		return "", false
	}
	return strings.Join(labels[1:], "."), true
}

func (s *IconService) tileFor(domain string) ([]byte, error) {
	name, _ := s.provider.Load().(string)
	switch name {
	case ProviderOff:
		return nil, ErrDisabled
	case ProviderGoogle:
		return s.tile(googleURL(domain))
	case ProviderBitwarden:
		return s.cached("bitwarden:"+domain, func() (image.Image, error) { return s.bitwardenIcon(domain) })
	case ProviderSite:
		return s.cached("site:"+domain, func() (image.Image, error) { return siteIcon(domain) })
	default:
		return s.tile(fmt.Sprintf("https://icons.duckduckgo.com/ip3/%s.ico", domain))
	}
}

func (s *IconService) Image(src string) ([]byte, error) {
	parsed, err := url.Parse(src)
	if err != nil || parsed.Scheme != "https" || parsed.Host == "" {
		return nil, ErrUnsupportedURL
	}
	return s.tile(parsed.String())
}

const placeholderSide = 19

func (s *IconService) bitwardenIcon(domain string) (image.Image, error) {
	img, err := s.getFavicon("https://icons.bitwarden.net/" + url.PathEscape(domain) + "/icon.png")
	if err != nil {
		return nil, err
	}
	if placeholder(img) {
		return nil, errNoSiteIcon
	}
	return img, nil
}

func placeholder(img image.Image) bool {
	b := img.Bounds()
	if b.Dx() != placeholderSide || b.Dy() != placeholderSide {
		return false
	}
	for y := b.Min.Y; y < b.Max.Y; y++ {
		for x := b.Min.X; x < b.Max.X; x++ {
			r, g, bl, _ := img.At(x, y).RGBA()
			if r != g || g != bl {
				return false
			}
		}
	}
	return true
}

func googleURL(domain string) string {
	return "https://www.google.com/s2/favicons?sz=256&domain=" + url.QueryEscape(domain)
}

func (s *IconService) tile(src string) ([]byte, error) {
	return s.cached(src, func() (image.Image, error) { return s.getFavicon(src) })
}

func (s *IconService) cached(src string, load func() (image.Image, error)) ([]byte, error) {
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

		png, fromDisk := s.loadDisk(src)
		var err error
		if !fromDisk {
			png, err = s.fetch(load)
			if err == nil {
				s.saveDisk(src, png)
			}
		}

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

func (s *IconService) fetch(load func() (image.Image, error)) ([]byte, error) {
	favicon, err := load()
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
