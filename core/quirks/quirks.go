package quirks

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"time"

	"github.com/Laky-64/renvault-core/app"
)

const (
	source  = "https://raw.githubusercontent.com/apple/password-manager-resources/main/quirks/"
	ttl     = 7 * 24 * time.Hour
	retry   = time.Minute
	timeout = 5 * time.Second
	limit   = 2 << 20
)

type resource struct {
	file  string
	parse func([]byte) map[string]string

	mu     sync.Mutex
	fetch  sync.Mutex
	table  map[string]string
	loaded time.Time
	tried  time.Time
}

var (
	dirMu sync.Mutex
	dir   string

	rules = &resource{file: "password-rules.json", parse: parseRules}
	urls  = &resource{file: "change-password-URLs.json", parse: parseURLs}
	share = &resource{file: "shared-credentials.json", parse: parseShared}
)

func SetDir(path string) {
	dirMu.Lock()
	dir = path
	dirMu.Unlock()
	for _, r := range []*resource{rules, urls, share} {
		r.load()
	}
}

func PasswordRules(host string) (string, bool) {
	return lookup(rules.get(true), host)
}

func ChangeURL(host string) string {
	value, _ := lookup(urls.get(true), host)
	return value
}

func SharedSite(domain string) (string, bool) {
	group, ok := share.get(false)[strings.ToLower(domain)]
	return group, ok
}

func lookup(table map[string]string, host string) (string, bool) {
	host = strings.ToLower(strings.TrimSuffix(strings.TrimSpace(host), "."))
	for host != "" {
		if value, ok := table[host]; ok {
			return value, true
		}
		_, rest, found := strings.Cut(host, ".")
		if !found || !strings.Contains(rest, ".") {
			break
		}
		host = rest
	}
	return "", false
}

func (r *resource) path() string {
	dirMu.Lock()
	defer dirMu.Unlock()
	if dir == "" {
		return ""
	}
	return filepath.Join(dir, r.file)
}

func (r *resource) load() {
	path := r.path()
	if path == "" {
		return
	}
	info, err := os.Stat(path)
	if err == nil {
		if data, err := os.ReadFile(path); err == nil {
			if table := r.parse(data); table != nil {
				r.mu.Lock()
				r.table, r.loaded = table, info.ModTime()
				r.mu.Unlock()
			}
		}
	}
	r.mu.Lock()
	stale := time.Since(r.loaded) > ttl
	r.mu.Unlock()
	if stale {
		go r.refresh()
	}
}

func (r *resource) get(block bool) map[string]string {
	r.mu.Lock()
	empty := r.table == nil
	stale := time.Since(r.loaded) > ttl
	r.mu.Unlock()
	switch {
	case empty && block:
		r.refresh()
	case stale:
		go r.refresh()
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.table
}

func (r *resource) refresh() {
	r.fetch.Lock()
	defer r.fetch.Unlock()
	r.mu.Lock()
	fresh := r.table != nil && time.Since(r.loaded) <= ttl
	recent := time.Since(r.tried) < retry
	if !fresh && !recent {
		r.tried = time.Now()
	}
	r.mu.Unlock()
	if fresh || recent {
		return
	}

	data, table := r.download()
	if table == nil {
		return
	}
	r.mu.Lock()
	r.table, r.loaded = table, time.Now()
	r.mu.Unlock()
	if path := r.path(); path != "" && os.MkdirAll(filepath.Dir(path), 0o700) == nil {
		temp := path + ".tmp"
		if os.WriteFile(temp, data, 0o600) == nil {
			_ = os.Rename(temp, path)
		}
	}
}

func (r *resource) download() ([]byte, map[string]string) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, source+r.file, nil)
	if err != nil {
		return nil, nil
	}
	request.Header.Set("User-Agent", app.Name)
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		return nil, nil
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, nil
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, limit))
	if err != nil {
		return nil, nil
	}
	table := r.parse(data)
	if table == nil {
		return nil, nil
	}
	return data, table
}

func parseRules(data []byte) map[string]string {
	var raw map[string]struct {
		Rules string `json:"password-rules"`
	}
	if json.Unmarshal(data, &raw) != nil || len(raw) == 0 {
		return nil
	}
	table := make(map[string]string, len(raw))
	for host, entry := range raw {
		table[strings.ToLower(host)] = entry.Rules
	}
	return table
}

func parseURLs(data []byte) map[string]string {
	var raw map[string]string
	if json.Unmarshal(data, &raw) != nil || len(raw) == 0 {
		return nil
	}
	table := make(map[string]string, len(raw))
	for host, url := range raw {
		table[strings.ToLower(host)] = url
	}
	return table
}

func parseShared(data []byte) map[string]string {
	var raw []struct {
		Shared   []string `json:"shared"`
		From     []string `json:"from"`
		To       []string `json:"to"`
		Obsolete bool     `json:"fromDomainsAreObsoleted"`
	}
	if json.Unmarshal(data, &raw) != nil || len(raw) == 0 {
		return nil
	}
	table := map[string]string{}
	join := func(domains []string) {
		if len(domains) < 2 {
			return
		}
		group := slices.Min(domains)
		for _, d := range domains {
			if existing, ok := table[d]; ok && existing < group {
				group = existing
			}
		}
		for _, d := range domains {
			table[strings.ToLower(d)] = strings.ToLower(group)
		}
	}
	for _, entry := range raw {
		switch {
		case len(entry.Shared) > 0:
			join(entry.Shared)
		case entry.Obsolete && len(entry.To) > 0:
			join(append(slices.Clone(entry.From), entry.To...))
		}
	}
	return table
}
