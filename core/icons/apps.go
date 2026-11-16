package icons

import (
	"bytes"
	"errors"
	"image"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/Laky-64/http"
	"golang.org/x/net/html"
)

var (
	ErrUnknownApp  = errors.New("icons: app not found on the store")
	packagePattern = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$`)
)

type AppInfo struct {
	Name string
	Icon []byte
}

type storePage struct {
	name string
	icon string
}

type nameEntry struct {
	name    string
	expires time.Time
}

var (
	namesMu sync.Mutex
	names   = map[string]nameEntry{}
)

func (s *IconService) App(pkg, lang string) (AppInfo, error) {
	if name, _ := s.provider.Load().(string); name == ProviderOff {
		return AppInfo{}, ErrDisabled
	}
	if !packagePattern.MatchString(pkg) {
		return AppInfo{}, ErrUnknownApp
	}
	var (
		once sync.Once
		page storePage
		perr error
	)
	load := func() (storePage, error) {
		once.Do(func() { page, perr = fetchStorePage(pkg, lang) })
		return page, perr
	}

	nameKey := "appname:" + lang + ":" + pkg
	name, ok := cachedName(nameKey)
	if !ok {
		if data, fromDisk := s.loadDisk(nameKey); fromDisk {
			name = string(data)
		} else if p, err := load(); err == nil {
			name = p.name
			s.saveDisk(nameKey, []byte(name))
		}
		rememberName(nameKey, name)
	}

	icon, err := s.cached("app:"+pkg, func() (image.Image, error) {
		p, err := load()
		if err != nil {
			return nil, err
		}
		if p.icon == "" {
			return nil, ErrUnknownApp
		}
		return s.getFavicon(p.icon)
	})
	if name == "" && err != nil {
		return AppInfo{}, err
	}
	return AppInfo{Name: name, Icon: icon}, nil
}

func cachedName(key string) (string, bool) {
	namesMu.Lock()
	defer namesMu.Unlock()
	e, ok := names[key]
	if !ok || time.Now().After(e.expires) {
		return "", false
	}
	return e.name, true
}

func rememberName(key, name string) {
	ttl := hitTTL
	if name == "" {
		ttl = missTTL
	}
	namesMu.Lock()
	names[key] = nameEntry{name: name, expires: time.Now().Add(ttl)}
	namesMu.Unlock()
}

func fetchStorePage(pkg, lang string) (storePage, error) {
	query := url.Values{"id": {pkg}}
	if lang != "" {
		query.Set("hl", lang)
	}
	result, err := http.ExecuteRequest(
		"https://play.google.com/store/apps/details?"+query.Encode(),
		http.Timeout(fetchTimeout),
		http.Headers(map[string]string{"User-Agent": "Mozilla/5.0"}),
	)
	if err != nil {
		return storePage{}, ErrUnknownApp
	}
	page := parseStorePage(result.Body)
	if page.name == "" && page.icon == "" {
		return storePage{}, ErrUnknownApp
	}
	return page, nil
}

func parseStorePage(body []byte) storePage {
	var page storePage
	tokens := html.NewTokenizer(bytes.NewReader(body))
	for {
		switch tokens.Next() {
		case html.ErrorToken:
			return page
		case html.StartTagToken, html.SelfClosingTagToken:
			tag, attrs := tokens.TagName()
			if string(tag) == "body" {
				return page
			}
			if string(tag) != "meta" || !attrs {
				continue
			}
			var property, content string
			for {
				key, value, more := tokens.TagAttr()
				switch string(key) {
				case "property":
					property = string(value)
				case "content":
					content = string(value)
				}
				if !more {
					break
				}
			}
			switch property {
			case "og:title":
				page.name = storeTitle(content)
			case "og:image":
				page.icon = sizedIcon(content)
			}
		}
	}
}

func storeTitle(title string) string {
	if i := strings.LastIndex(title, " - "); i > 0 {
		title = title[:i]
	}
	return strings.TrimSpace(title)
}

func sizedIcon(src string) string {
	if !strings.HasPrefix(src, "https://") {
		return ""
	}
	if i := strings.LastIndex(src, "="); i > strings.LastIndex(src, "/") {
		src = src[:i]
	}
	return src + "=s256"
}
