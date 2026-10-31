package vault

import (
	"cmp"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptrace"
	"net/url"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"time"

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

type AnisetteServer struct {
	Name      string
	Address   string
	Kind      int
	Latency   time.Duration
	Reachable bool
}

func (v *Vault) AnisetteServers(ctx context.Context) []AnisetteServer {
	list := anisette.NewServerList(filepath.Join(v.base, "anisette-servers.json"), anisette.DefaultServerListURL).Servers()
	out := make([]AnisetteServer, len(list))
	client := &http.Client{Timeout: 5 * time.Second}
	var wg sync.WaitGroup
	for i, s := range list {
		out[i] = AnisetteServer{Name: s.Name, Address: strings.TrimRight(s.Address, "/"), Kind: s.Kind}
		wg.Go(func() {
			out[i].Latency, out[i].Reachable = probeAnisette(ctx, client, out[i].Address)
		})
	}
	wg.Wait()
	slices.SortStableFunc(out, func(a, b AnisetteServer) int {
		if a.Reachable != b.Reachable {
			if a.Reachable {
				return -1
			}
			return 1
		}
		if !a.Reachable {
			return 0
		}
		if a.Kind != b.Kind {
			return cmp.Compare(b.Kind, a.Kind)
		}
		if sa, sb := strings.HasPrefix(a.Address, "https://"), strings.HasPrefix(b.Address, "https://"); sa != sb {
			if sa {
				return -1
			}
			return 1
		}
		return cmp.Compare(a.Latency, b.Latency)
	})
	return out
}

func probeAnisette(ctx context.Context, client *http.Client, address string) (time.Duration, bool) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, address+"/v3/client_info", nil)
	if err != nil {
		return 0, false
	}
	var sent, answered time.Time
	req = req.WithContext(httptrace.WithClientTrace(req.Context(), &httptrace.ClientTrace{
		WroteRequest:         func(httptrace.WroteRequestInfo) { sent = time.Now() },
		GotFirstResponseByte: func() { answered = time.Now() },
	}))
	resp, err := client.Do(req)
	if err != nil {
		return 0, false
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 64<<10))
	return answered.Sub(sent), resp.StatusCode == http.StatusOK
}
