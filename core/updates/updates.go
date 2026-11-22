package updates

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/Laky-64/renvault-core/app"
	"github.com/ProtonMail/go-crypto/openpgp"
	"net/http"
)

const (
	releasesURL  = "https://api.github.com/repos/Laky-64/RenVault/releases?per_page=40"
	sumsName     = "SHA256SUMS"
	cacheBody    = "releases.json"
	cacheTag     = "releases.etag"
	signatureExt = ".asc"
	checkTimeout = 30 * time.Second
	sumsLimit    = 1 << 20
	listLimit    = 8 << 20
)

//go:embed signing.asc
var signingKey []byte

var (
	ErrNoAsset    = errors.New("updates: no package for this platform")
	ErrUnsigned   = errors.New("updates: release is not signed")
	ErrBadSum     = errors.New("updates: checksum mismatch")
	ErrBadVersion = errors.New("updates: unknown version")
	ErrLimited    = errors.New("updates: rate limited")
)

type Notes struct {
	Version string
	Body    string
}

type Release struct {
	Version   string
	Tag       string
	Page      string
	Published time.Time
	Asset     string
	URL       string
	Size      int64
	Sums      string
	Signature string
	Notes     []Notes
}

type ghAsset struct {
	Name string `json:"name"`
	URL  string `json:"browser_download_url"`
	Size int64  `json:"size"`
}

type ghRelease struct {
	Tag        string    `json:"tag_name"`
	Body       string    `json:"body"`
	Page       string    `json:"html_url"`
	Draft      bool      `json:"draft"`
	Prerelease bool      `json:"prerelease"`
	Published  time.Time `json:"published_at"`
	Assets     []ghAsset `json:"assets"`
}

func Check(ctx context.Context, current, platform string, prerelease bool, dir string) (*Release, error) {
	mine, ok := parse(current)
	if !ok {
		return nil, ErrBadVersion
	}
	ctx, cancel := context.WithTimeout(ctx, checkTimeout)
	defer cancel()
	data, err := releases(ctx, dir)
	if err != nil {
		return nil, err
	}
	var list []ghRelease
	if err := json.Unmarshal(data, &list); err != nil {
		return nil, err
	}
	type candidate struct {
		release ghRelease
		version semver
	}
	var newer []candidate
	for _, r := range list {
		if r.Draft || (r.Prerelease && !prerelease) {
			continue
		}
		v, ok := parse(r.Tag)
		if !ok || v.compare(mine) <= 0 {
			continue
		}
		newer = append(newer, candidate{r, v})
	}
	if len(newer) == 0 {
		return nil, nil
	}
	slices.SortFunc(newer, func(a, b candidate) int { return b.version.compare(a.version) })
	latest := newer[0].release
	version := strings.TrimPrefix(latest.Tag, "v")
	name := fmt.Sprintf("%s-%s-%s.%s", app.Name, platformName(platform), version, platformExt(platform))
	out := &Release{Version: version, Tag: latest.Tag, Page: latest.Page, Published: latest.Published}
	for _, a := range latest.Assets {
		switch a.Name {
		case name:
			out.Asset, out.URL, out.Size = a.Name, a.URL, a.Size
		case sumsName:
			out.Sums = a.URL
		case sumsName + signatureExt:
			out.Signature = a.URL
		}
	}
	for _, c := range newer {
		out.Notes = append(out.Notes, Notes{Version: strings.TrimPrefix(c.release.Tag, "v"), Body: strings.TrimSpace(c.release.Body)})
	}
	return out, nil
}

func Download(ctx context.Context, r Release, dir string, progress func(done, total int64)) (string, error) {
	if r.URL == "" {
		return "", ErrNoAsset
	}
	if r.Sums == "" || r.Signature == "" {
		return "", ErrUnsigned
	}
	sums, err := fetch(ctx, r.Sums, sumsLimit)
	if err != nil {
		return "", err
	}
	signature, err := fetch(ctx, r.Signature, sumsLimit)
	if err != nil {
		return "", err
	}
	if err := verify(sums, signature); err != nil {
		return "", err
	}
	want, ok := sumOf(sums, r.Asset)
	if !ok {
		return "", ErrUnsigned
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", err
	}
	path := filepath.Join(dir, filepath.Base(r.Asset))
	if got, err := hashFile(path); err == nil && got == want {
		return path, nil
	}
	partial := path + ".part"
	if err := save(ctx, r.URL, partial, r.Size, progress); err != nil {
		_ = os.Remove(partial)
		return "", err
	}
	got, err := hashFile(partial)
	if err != nil {
		_ = os.Remove(partial)
		return "", err
	}
	if got != want {
		_ = os.Remove(partial)
		return "", ErrBadSum
	}
	if err := os.Rename(partial, path); err != nil {
		return "", err
	}
	return path, nil
}

func Clean(dir string) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return
	}
	for _, e := range entries {
		if strings.HasPrefix(e.Name(), app.Name+"-") {
			_ = os.Remove(filepath.Join(dir, e.Name()))
		}
	}
}

func releases(ctx context.Context, dir string) ([]byte, error) {
	body := filepath.Join(dir, cacheBody)
	tag := filepath.Join(dir, cacheTag)
	cached, _ := os.ReadFile(body)
	etag, _ := os.ReadFile(tag)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, releasesURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", app.Name)
	req.Header.Set("Accept", "application/vnd.github+json")
	if len(cached) > 0 && len(etag) > 0 {
		req.Header.Set("If-None-Match", strings.TrimSpace(string(etag)))
	}
	response, err := http.DefaultClient.Do(req)
	if err != nil {
		if len(cached) > 0 {
			return cached, nil
		}
		return nil, err
	}
	defer response.Body.Close()
	switch response.StatusCode {
	case http.StatusOK:
		data, err := io.ReadAll(io.LimitReader(response.Body, listLimit))
		if err != nil {
			return nil, err
		}
		if dir != "" && os.MkdirAll(dir, 0o700) == nil {
			_ = os.WriteFile(body, data, 0o600)
			_ = os.WriteFile(tag, []byte(response.Header.Get("ETag")), 0o600)
		}
		return data, nil
	case http.StatusNotModified:
		return cached, nil
	case http.StatusForbidden, http.StatusTooManyRequests:
		if len(cached) > 0 {
			return cached, nil
		}
		return nil, ErrLimited
	default:
		if len(cached) > 0 {
			return cached, nil
		}
		return nil, fmt.Errorf("updates: releases returned %d", response.StatusCode)
	}
}

func platformName(platform string) string {
	name, _, _ := strings.Cut(platform, ".")
	return name
}

func platformExt(platform string) string {
	_, ext, _ := strings.Cut(platform, ".")
	return ext
}

func verify(sums, signature []byte) error {
	keys, err := openpgp.ReadArmoredKeyRing(bytes.NewReader(signingKey))
	if err != nil {
		return err
	}
	if _, err := openpgp.CheckArmoredDetachedSignature(keys, bytes.NewReader(sums), bytes.NewReader(signature), nil); err != nil {
		return ErrUnsigned
	}
	return nil
}

func sumOf(sums []byte, name string) (string, bool) {
	scanner := bufio.NewScanner(bytes.NewReader(sums))
	for scanner.Scan() {
		sum, file, ok := strings.Cut(strings.TrimSpace(scanner.Text()), " ")
		if !ok {
			continue
		}
		file = strings.TrimPrefix(strings.TrimSpace(file), "*")
		if file == name && len(sum) == sha256.Size*2 {
			return strings.ToLower(sum), true
		}
	}
	return "", false
}

func hashFile(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

func request(ctx context.Context, url string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", app.Name)
	req.Header.Set("Accept", "application/vnd.github+json")
	response, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != http.StatusOK {
		response.Body.Close()
		return nil, fmt.Errorf("updates: %s returned %d", url, response.StatusCode)
	}
	return response, nil
}

func fetch(ctx context.Context, url string, limit int64) ([]byte, error) {
	response, err := request(ctx, url)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	return io.ReadAll(io.LimitReader(response.Body, limit))
}

func save(ctx context.Context, url, path string, size int64, progress func(done, total int64)) error {
	response, err := request(ctx, url)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	total := response.ContentLength
	if total <= 0 {
		total = size
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	defer f.Close()
	buffer := make([]byte, 256<<10)
	var done int64
	last := time.Time{}
	for {
		n, err := response.Body.Read(buffer)
		if n > 0 {
			if _, werr := f.Write(buffer[:n]); werr != nil {
				return werr
			}
			done += int64(n)
			if progress != nil && time.Since(last) > 100*time.Millisecond {
				last = time.Now()
				progress(done, total)
			}
		}
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return err
		}
	}
	if progress != nil {
		progress(done, total)
	}
	return f.Sync()
}

type semver struct {
	core [3]int
	pre  []string
}

func parse(text string) (semver, bool) {
	text = strings.TrimPrefix(strings.TrimSpace(text), "v")
	main, pre, _ := strings.Cut(text, "-")
	parts := strings.Split(main, ".")
	if len(parts) != 3 {
		return semver{}, false
	}
	var v semver
	for i, p := range parts {
		n, err := strconv.Atoi(p)
		if err != nil {
			return semver{}, false
		}
		v.core[i] = n
	}
	if pre != "" {
		v.pre = strings.Split(pre, ".")
	}
	return v, true
}

func (a semver) compare(b semver) int {
	for i := range a.core {
		if c := a.core[i] - b.core[i]; c != 0 {
			return c
		}
	}
	switch {
	case len(a.pre) == 0 && len(b.pre) == 0:
		return 0
	case len(a.pre) == 0:
		return 1
	case len(b.pre) == 0:
		return -1
	}
	for i := 0; i < len(a.pre) && i < len(b.pre); i++ {
		x, xerr := strconv.Atoi(a.pre[i])
		y, yerr := strconv.Atoi(b.pre[i])
		switch {
		case xerr == nil && yerr == nil:
			if x != y {
				return x - y
			}
		case xerr == nil:
			return -1
		case yerr == nil:
			return 1
		default:
			if c := strings.Compare(a.pre[i], b.pre[i]); c != 0 {
				return c
			}
		}
	}
	return len(a.pre) - len(b.pre)
}
