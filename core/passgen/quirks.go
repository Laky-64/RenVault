package passgen

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"
)

const (
	quirksURL     = "https://raw.githubusercontent.com/apple/password-manager-resources/main/quirks/password-rules.json"
	quirksFile    = "password-rules.json"
	quirksTTL     = 7 * 24 * time.Hour
	quirksTimeout = 5 * time.Second
	quirksLimit   = 2 << 20
)

var quirks struct {
	mu       sync.Mutex
	dir      string
	table    map[string]string
	loaded   time.Time
	fetching bool
}

func SetDir(dir string) {
	quirks.mu.Lock()
	quirks.dir = dir
	quirks.table, quirks.loaded = readQuirks(dir)
	stale := time.Since(quirks.loaded) > quirksTTL
	quirks.mu.Unlock()
	if stale {
		go refreshQuirks()
	}
}

func quirkFor(host string) (string, bool) {
	quirks.mu.Lock()
	empty := quirks.table == nil
	stale := time.Since(quirks.loaded) > quirksTTL
	quirks.mu.Unlock()
	if empty {
		refreshQuirks()
	} else if stale {
		go refreshQuirks()
	}
	quirks.mu.Lock()
	defer quirks.mu.Unlock()
	text, ok := quirks.table[host]
	return text, ok
}

func refreshQuirks() {
	quirks.mu.Lock()
	if quirks.fetching {
		quirks.mu.Unlock()
		return
	}
	quirks.fetching = true
	dir := quirks.dir
	quirks.mu.Unlock()

	data, table := downloadQuirks()

	quirks.mu.Lock()
	defer quirks.mu.Unlock()
	quirks.fetching = false
	if table == nil {
		return
	}
	quirks.table, quirks.loaded = table, time.Now()
	if dir != "" && os.MkdirAll(dir, 0o700) == nil {
		temp := filepath.Join(dir, quirksFile+".tmp")
		if os.WriteFile(temp, data, 0o600) == nil {
			_ = os.Rename(temp, filepath.Join(dir, quirksFile))
		}
	}
}

func downloadQuirks() ([]byte, map[string]string) {
	ctx, cancel := context.WithTimeout(context.Background(), quirksTimeout)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, quirksURL, nil)
	if err != nil {
		return nil, nil
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		return nil, nil
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, nil
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, quirksLimit))
	if err != nil {
		return nil, nil
	}
	table := parseQuirks(data)
	if table == nil {
		return nil, nil
	}
	return data, table
}

func readQuirks(dir string) (map[string]string, time.Time) {
	if dir == "" {
		return nil, time.Time{}
	}
	path := filepath.Join(dir, quirksFile)
	info, err := os.Stat(path)
	if err != nil {
		return nil, time.Time{}
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, time.Time{}
	}
	table := parseQuirks(data)
	if table == nil {
		return nil, time.Time{}
	}
	return table, info.ModTime()
}

func parseQuirks(data []byte) map[string]string {
	var raw map[string]struct {
		Rules string `json:"password-rules"`
	}
	if json.Unmarshal(data, &raw) != nil || len(raw) == 0 {
		return nil
	}
	table := make(map[string]string, len(raw))
	for host, entry := range raw {
		table[host] = entry.Rules
	}
	return table
}
