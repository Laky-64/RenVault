package vault

import (
	"encoding/json"
	"strings"
	"sync"
	"time"

	"github.com/Laky-64/http"
)

const (
	changeURLsSource   = "https://raw.githubusercontent.com/apple/password-manager-resources/main/quirks/change-password-URLs.json"
	changeFetchTimeout = 10 * time.Second
)

var (
	changeOnce sync.Once
	changeURLs map[string]string
)

func changeURLIndex() map[string]string {
	changeOnce.Do(func() {
		changeURLs = map[string]string{}
		result, err := http.ExecuteRequest(changeURLsSource,
			http.Timeout(changeFetchTimeout),
			http.Headers(map[string]string{"User-Agent": pwnedUserAgent}),
		)
		if err != nil || result == nil || result.StatusCode != 200 {
			return
		}
		var index map[string]string
		if json.Unmarshal(result.Body, &index) == nil && len(index) > 0 {
			changeURLs = index
		}
	})
	return changeURLs
}

func knownChangeURL(host string) string {
	index := changeURLIndex()
	for h := host; strings.Contains(h, "."); {
		if known, ok := index[h]; ok {
			return known
		}
		cut := strings.Index(h, ".")
		h = h[cut+1:]
	}
	return ""
}

func changePasswordURL(domain string) string {
	host := strings.ToLower(strings.TrimSpace(domain))
	host = strings.TrimSuffix(host, ".")
	if host == "" || strings.ContainsAny(host, " /?#\\") {
		return ""
	}
	if known := knownChangeURL(host); known != "" {
		return known
	}
	return "https://" + host + "/"
}
