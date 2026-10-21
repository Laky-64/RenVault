package vault

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha1"
	"encoding/hex"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/Laky-64/http"
)

const (
	pwnedRangeURL      = "https://api.pwnedpasswords.com/range/"
	pwnedUserAgent     = "RenVault"
	pwnedPrefixLen     = 5
	pwnedConcurrency   = 8
	pwnedRequestTries  = 3
	pwnedRequestTimout = 20 * time.Second
	pwnedTimeout       = 60 * time.Second
)

func pwnedHash(password string) string {
	sum := sha1.Sum([]byte(password))
	return strings.ToUpper(hex.EncodeToString(sum[:]))
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
