package vault

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	"github.com/Laky-64/appleservices/anisette"
)

const DefaultAnisetteList = anisette.DefaultServerListURL

type anisetteFile struct {
	URL string `json:"url,omitempty"`
}

func anisettePath(base string) string {
	return filepath.Join(base, "anisette.json")
}

func loadAnisette(base string) string {
	data, err := os.ReadFile(anisettePath(base))
	if err != nil {
		return ""
	}
	var f anisetteFile
	if json.Unmarshal(data, &f) != nil {
		return ""
	}
	return f.URL
}

func normalizeAnisette(raw string) (string, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return "", nil
	}
	if !strings.Contains(raw, "://") {
		raw = "https://" + raw
	}
	parsed, err := url.Parse(raw)
	if err != nil || parsed.Host == "" || (parsed.Scheme != "https" && parsed.Scheme != "http") {
		return "", fmt.Errorf("vault: %q is not an anisette server address", raw)
	}
	return strings.TrimRight(parsed.String(), "/"), nil
}

func (v *Vault) AnisetteServer() string {
	return loadAnisette(v.base)
}

func (v *Vault) SetAnisetteServer(raw string) error {
	server, err := normalizeAnisette(raw)
	if err != nil {
		return err
	}
	if server == "" {
		if err := os.Remove(anisettePath(v.base)); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
	} else {
		data, err := json.Marshal(anisetteFile{URL: server})
		if err != nil {
			return err
		}
		if err := os.MkdirAll(v.base, 0o700); err != nil {
			return err
		}
		if err := writeFileAtomic(anisettePath(v.base), data, 0o600); err != nil {
			return err
		}
	}
	v.sess.SetAnisetteServer(server)
	return nil
}
