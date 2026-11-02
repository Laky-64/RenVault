package icons

import (
	"encoding/json"
	"errors"
	"fmt"
	"image"
	"io"
	nethttp "net/http"
	"net/url"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	siteTimeout    = 4 * time.Second
	headLimit      = 256 << 10
	manifestLimit  = 64 << 10
	imageLimit     = 1 << 20
	goodEnough     = 128
	imageAttempts  = 4
	imageKeep      = 2
	imageBatch     = 2
	headChunk      = 16 << 10
	clearSide      = 96
	touchIconSize  = 180
	unknownSize    = 32
	siteUserAgent  = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
	siteAcceptHTML = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
	siteAcceptPic  = "image/png,image/x-icon,image/webp,image/gif,image/jpeg;q=0.9,*/*;q=0.5"
	siteLanguage   = "it-IT,it;q=0.9,en;q=0.8"
)

var (
	errNoSiteIcon = errors.New("icons: the site declares no usable icon")
	linkTag       = regexp.MustCompile(`(?is)<link\b[^>]*>`)
	metaTag       = regexp.MustCompile(`(?is)<meta\b[^>]*itemprop\s*=\s*["']?image[^>]*>`)
	headEnd       = regexp.MustCompile(`(?i)</head>`)
	iconRel       = regexp.MustCompile(`(?i)rel\s*=\s*["']?[^"'>]*icon`)
	attribute     = regexp.MustCompile(`(?is)([a-z-]+)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))`)
	siteClient    = &nethttp.Client{Timeout: siteTimeout}
)

type candidate struct {
	url    string
	size   int
	square bool
}

func siteIcon(domain string) (image.Image, error) {
	img, err := siteIconAt(domain, "https")
	if _, port := splitPort(domain); err != nil && (port != "" || localHost(domain)) {
		return siteIconAt(domain, "http")
	}
	return img, err
}

func siteIconAt(domain, scheme string) (image.Image, error) {
	root := &url.URL{Scheme: scheme, Host: domain, Path: "/"}
	var icons []candidate
	page, final, err := fetchLimited(root.String(), siteAcceptHTML, headLimit)
	if _, unreachable := errors.AsType[*url.Error](err); unreachable {
		return nil, err
	}
	if err == nil {
		root = final
		found, manifest := parseLinks(page, final)
		icons = found
		if best(icons) < goodEnough && manifest != "" {
			icons = append(icons, manifestIcons(manifest)...)
		}
	}
	slices.SortStableFunc(icons, func(a, b candidate) int { return b.size - a.size })
	icons = append(icons,
		candidate{url: root.ResolveReference(&url.URL{Path: "/apple-touch-icon.png"}).String(), size: touchIconSize},
		candidate{url: root.ResolveReference(&url.URL{Path: "/favicon.ico"}).String(), size: unknownSize},
	)

	var queue []string
	seen := map[string]bool{}
	square := map[string]bool{}
	for _, c := range icons {
		if !seen[c.url] {
			seen[c.url] = true
			square[c.url] = c.square
			queue = append(queue, c.url)
		}
	}
	if len(queue) > imageAttempts {
		queue = queue[:imageAttempts]
	}

	var found []image.Image
	for start := 0; start < len(queue) && len(found) < imageKeep; start += imageBatch {
		batch := queue[start:min(start+imageBatch, len(queue))]
		results := make([]image.Image, len(batch))
		var wg sync.WaitGroup
		for i, address := range batch {
			wg.Add(1)
			go func() {
				defer wg.Done()
				data, _, err := fetchLimited(address, siteAcceptPic, imageLimit)
				if err != nil {
					return
				}
				if img, err := decode(data); err == nil && (!square[address] || squarish(img)) {
					results[i] = img
				}
			}()
		}
		wg.Wait()
		for _, img := range results {
			if img != nil {
				found = append(found, img)
			}
		}
	}
	return pick(found)
}

func squarish(img image.Image) bool {
	w, h := img.Bounds().Dx(), img.Bounds().Dy()
	return w > 0 && h > 0 && max(w, h)*10 <= min(w, h)*11
}

func pick(found []image.Image) (image.Image, error) {
	var chosen image.Image
	for _, img := range found {
		if transparent(img) && min(img.Bounds().Dx(), img.Bounds().Dy()) >= clearSide {
			if chosen == nil || !transparent(chosen) || img.Bounds().Dx() > chosen.Bounds().Dx() {
				chosen = img
			}
			continue
		}
		if chosen == nil || (!transparent(chosen) && img.Bounds().Dx() > chosen.Bounds().Dx()) {
			chosen = img
		}
	}
	if chosen == nil {
		return nil, errNoSiteIcon
	}
	return chosen, nil
}

func transparent(img image.Image) bool {
	b := img.Bounds()
	for _, p := range []image.Point{{b.Min.X, b.Min.Y}, {b.Max.X - 1, b.Min.Y}, {b.Min.X, b.Max.Y - 1}, {b.Max.X - 1, b.Max.Y - 1}, {b.Min.X, (b.Min.Y + b.Max.Y) / 2}, {b.Max.X - 1, (b.Min.Y + b.Max.Y) / 2}} {
		if _, _, _, a := img.At(p.X, p.Y).RGBA(); a < 0x8000 {
			continue
		}
		return false
	}
	return true
}

func best(icons []candidate) int {
	top := 0
	for _, c := range icons {
		top = max(top, c.size)
	}
	return top
}

func parseLinks(page []byte, base *url.URL) ([]candidate, string) {
	var icons []candidate
	manifest := ""
	for _, tag := range linkTag.FindAll(page, -1) {
		attrs := attributesOf(tag)
		href := attrs["href"]
		if href == "" || strings.HasPrefix(href, "data:") {
			continue
		}
		target, err := base.Parse(href)
		if err != nil || (target.Scheme != "https" && target.Scheme != "http") {
			continue
		}
		rel := strings.Fields(strings.ToLower(attrs["rel"]))
		switch {
		case slices.Contains(rel, "manifest"):
			manifest = target.String()
		case slices.Contains(rel, "apple-touch-icon") || slices.Contains(rel, "apple-touch-icon-precomposed"):
			icons = append(icons, candidate{url: target.String(), size: sizeOf(attrs["sizes"], touchIconSize)})
		case slices.Contains(rel, "icon"):
			if strings.Contains(strings.ToLower(attrs["type"]), "svg") || strings.HasSuffix(strings.ToLower(target.Path), ".svg") {
				continue
			}
			icons = append(icons, candidate{url: target.String(), size: sizeOf(attrs["sizes"], unknownSize)})
		}
	}
	for _, tag := range metaTag.FindAll(page, -1) {
		attrs := attributesOf(tag)
		content := attrs["content"]
		if content == "" || strings.HasPrefix(content, "data:") || strings.HasSuffix(strings.ToLower(content), ".svg") {
			continue
		}
		target, err := base.Parse(content)
		if err != nil || (target.Scheme != "https" && target.Scheme != "http") {
			continue
		}
		icons = append(icons, candidate{url: target.String(), size: goodEnough, square: true})
	}
	return icons, manifest
}

func attributesOf(tag []byte) map[string]string {
	attrs := map[string]string{}
	for _, m := range attribute.FindAllSubmatch(tag, -1) {
		value := string(m[3]) + string(m[4]) + string(m[5])
		attrs[strings.ToLower(string(m[1]))] = strings.TrimSpace(value)
	}
	return attrs
}

func sizeOf(sizes string, fallback int) int {
	top := 0
	for _, entry := range strings.Fields(strings.ToLower(sizes)) {
		w, _, ok := strings.Cut(entry, "x")
		if !ok {
			continue
		}
		if n, err := strconv.Atoi(w); err == nil {
			top = max(top, n)
		}
	}
	if top == 0 {
		return fallback
	}
	return top
}

func manifestIcons(address string) []candidate {
	data, final, err := fetchLimited(address, "application/manifest+json,application/json", manifestLimit)
	if err != nil {
		return nil
	}
	var manifest struct {
		Icons []struct {
			Src   string `json:"src"`
			Sizes string `json:"sizes"`
			Type  string `json:"type"`
		} `json:"icons"`
	}
	if json.Unmarshal(data, &manifest) != nil {
		return nil
	}
	var out []candidate
	for _, icon := range manifest.Icons {
		if strings.Contains(strings.ToLower(icon.Type), "svg") {
			continue
		}
		target, err := final.Parse(icon.Src)
		if err != nil {
			continue
		}
		out = append(out, candidate{url: target.String(), size: sizeOf(icon.Sizes, unknownSize)})
	}
	return out
}

func fetchLimited(address, accept string, limit int64) ([]byte, *url.URL, error) {
	request, err := nethttp.NewRequest(nethttp.MethodGet, address, nil)
	if err != nil {
		return nil, nil, err
	}
	request.Header.Set("User-Agent", siteUserAgent)
	request.Header.Set("Accept", accept)
	request.Header.Set("Accept-Language", siteLanguage)
	if accept == siteAcceptHTML {
		request.Header.Set("Sec-Fetch-Dest", "document")
		request.Header.Set("Sec-Fetch-Mode", "navigate")
		request.Header.Set("Sec-Fetch-Site", "none")
		request.Header.Set("Upgrade-Insecure-Requests", "1")
	} else {
		request.Header.Set("Sec-Fetch-Dest", "image")
		request.Header.Set("Sec-Fetch-Mode", "no-cors")
		request.Header.Set("Sec-Fetch-Site", "same-origin")
	}
	response, err := siteClient.Do(request)
	if err != nil {
		return nil, nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != nethttp.StatusOK {
		return nil, nil, fmt.Errorf("http status code %d", response.StatusCode)
	}
	if accept == siteAcceptHTML {
		data, err := readHead(response.Body, limit)
		return data, response.Request.URL, err
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, limit))
	if err != nil {
		return nil, nil, err
	}
	return data, response.Request.URL, nil
}

func readHead(body io.Reader, limit int64) ([]byte, error) {
	reader := io.LimitReader(body, limit)
	var page []byte
	chunk := make([]byte, headChunk)
	for {
		n, err := reader.Read(chunk)
		page = append(page, chunk[:n]...)
		if headEnd.Match(page) && declaresIcon(page) {
			return page, nil
		}
		if err == io.EOF {
			return page, nil
		}
		if err != nil {
			return page, err
		}
	}
}

func declaresIcon(page []byte) bool {
	for _, tag := range linkTag.FindAll(page, -1) {
		if iconRel.Match(tag) {
			return true
		}
	}
	return false
}
