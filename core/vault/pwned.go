package vault

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha1"
	"encoding/hex"
	"fmt"
	"github.com/Laky-64/renvault-core/app"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/Laky-64/http"
)

const (
	pwnedRangeURL      = "https://api.pwnedpasswords.com/range/"
	pwnedUserAgent     = app.Name
	pwnedPrefixLen     = 5
	pwnedConcurrency   = 8
	pwnedRequestTries  = 3
	pwnedRequestTimout = 20 * time.Second
	pwnedTimeout       = 60 * time.Second
	pwnedPrintLen      = 16
)

func pwnedHash(password string) string {
	sum := sha1.Sum([]byte(password))
	return strings.ToUpper(hex.EncodeToString(sum[:]))
}

func pwnedPrint(password string) string {
	return pwnedHash(password)[:pwnedPrintLen]
}

func isLeaked(p *payload, password string) bool {
	return password != "" && slices.Contains(p.Leaked, pwnedPrint(password))
}

func livePasswords(p *payload) map[string]string {
	out := map[string]string{}
	for _, w := range p.Web {
		if !w.IsDeleted && w.Password != "" {
			out[pwnedPrint(w.Password)] = pwnedHash(w.Password)
		}
	}
	return out
}

func leakedCount(p *payload) int {
	n := 0
	for _, w := range p.Web {
		if !w.IsDeleted && isLeaked(p, w.Password) {
			n++
		}
	}
	return n
}

func recordPwned(p *payload, checked map[string]string, hit map[string]struct{}) {
	for mark, hash := range checked {
		if _, ok := hit[hash]; ok {
			p.Leaked = append(p.Leaked, mark)
			p.Clean = slices.DeleteFunc(p.Clean, func(c string) bool { return c == mark })
		} else if !slices.Contains(p.Leaked, mark) {
			p.Clean = append(p.Clean, mark)
		}
	}
	slices.Sort(p.Leaked)
	p.Leaked = slices.Compact(p.Leaked)
	slices.Sort(p.Clean)
	p.Clean = slices.Compact(p.Clean)
}

func (v *Vault) checkFresh() {
	if !v.freshMu.TryLock() {
		return
	}
	defer v.freshMu.Unlock()
	v.mu.Lock()
	if v.p == nil || v.p.PwnedAt.IsZero() {
		v.mu.Unlock()
		return
	}
	origP := v.p
	pending := map[string]string{}
	for mark, hash := range livePasswords(v.p) {
		if !slices.Contains(v.p.Leaked, mark) && !slices.Contains(v.p.Clean, mark) {
			pending[mark] = hash
		}
	}
	v.mu.Unlock()
	if len(pending) == 0 {
		return
	}
	hashes := make([]string, 0, len(pending))
	for _, h := range pending {
		hashes = append(hashes, h)
	}
	ctx, cancel := context.WithTimeout(context.Background(), pwnedTimeout)
	defer cancel()
	hit, err := lookupPwned(ctx, hashes)
	if err != nil {
		return
	}
	v.mu.Lock()
	if v.p != origP {
		v.mu.Unlock()
		return
	}
	recordPwned(v.p, pending, hit)
	_ = v.saveLocked()
	v.mu.Unlock()
	v.notify()
}

func fetchPwnedRange(prefix string) (map[string]struct{}, error) {
	result, err := http.ExecuteRequest(pwnedRangeURL+prefix,
		http.Method("GET"),
		http.Headers(map[string]string{
			"Add-Padding": "true",
			"User-Agent":  pwnedUserAgent,
		}),
		http.Retries(pwnedRequestTries),
		http.Timeout(pwnedRequestTimout),
	)
	if result != nil && result.StatusCode != 200 {
		return nil, fmt.Errorf("pwned: range %s status %d", prefix, result.StatusCode)
	}
	if err != nil {
		return nil, fmt.Errorf("pwned: range %s: %w", prefix, err)
	}
	if result == nil {
		return nil, fmt.Errorf("pwned: range %s: no response", prefix)
	}

	out := make(map[string]struct{})
	sc := bufio.NewScanner(bytes.NewReader(result.Body))
	for sc.Scan() {
		suffix, countStr, ok := strings.Cut(strings.TrimSpace(sc.Text()), ":")
		if !ok {
			continue
		}
		n, err := strconv.Atoi(countStr)
		if err != nil || n == 0 {
			continue
		}
		out[prefix+strings.ToUpper(suffix)] = struct{}{}
	}
	if err := sc.Err(); err != nil {
		return nil, fmt.Errorf("pwned: range %s: %w", prefix, err)
	}
	return out, nil
}

func lookupPwned(ctx context.Context, hashes []string) (map[string]struct{}, error) {
	prefixes := make(map[string]struct{})
	for _, h := range hashes {
		if len(h) < pwnedPrefixLen {
			continue
		}
		prefixes[h[:pwnedPrefixLen]] = struct{}{}
	}
	if len(prefixes) == 0 {
		return map[string]struct{}{}, nil
	}

	work := make(chan string)
	var (
		mu      sync.Mutex
		found   = make(map[string]struct{})
		firstEr error
		wg      sync.WaitGroup
	)
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	workers := min(pwnedConcurrency, len(prefixes))
	for range workers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for prefix := range work {
				if ctx.Err() != nil {
					continue
				}
				res, err := fetchPwnedRange(prefix)
				mu.Lock()
				if err != nil {
					if firstEr == nil {
						firstEr = err
						cancel()
					}
				} else {
					for h := range res {
						found[h] = struct{}{}
					}
				}
				mu.Unlock()
			}
		}()
	}
	for prefix := range prefixes {
		select {
		case work <- prefix:
		case <-ctx.Done():
		}
	}
	close(work)
	wg.Wait()

	if firstEr != nil {
		return nil, firstEr
	}
	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("pwned: %w", err)
	}
	hit := make(map[string]struct{})
	for _, h := range hashes {
		if _, ok := found[h]; ok {
			hit[h] = struct{}{}
		}
	}
	return hit, nil
}
