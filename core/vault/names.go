package vault

import (
	"slices"
	"strings"
	"unicode/utf8"
)

const (
	maxNames      = 16
	maxNameLength = 128
)

type NameSwap struct {
	From string `cbor:"from" json:"from"`
	To   string `cbor:"to" json:"to"`
}

func sanitizeNames(in []NameSwap) []NameSwap {
	out := make([]NameSwap, 0, min(len(in), maxNames))
	seen := make(map[string]struct{}, len(in))
	for _, swap := range in {
		from := strings.TrimSpace(swap.From)
		to := strings.TrimSpace(swap.To)
		if from == "" || utf8.RuneCountInString(from) > maxNameLength || utf8.RuneCountInString(to) > maxNameLength {
			continue
		}
		key := strings.ToLower(from)
		if _, dup := seen[key]; dup {
			continue
		}
		seen[key] = struct{}{}
		out = append(out, NameSwap{From: from, To: to})
		if len(out) == maxNames {
			break
		}
	}
	return out
}

func (v *Vault) Names() ([]NameSwap, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return nil, errLocked
	}
	return slices.Clone(v.p.Names), nil
}

func (v *Vault) SetNames(names []NameSwap) error {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return errLocked
	}
	v.resetTimerLocked()
	next := sanitizeNames(names)
	if slices.Equal(v.p.Names, next) {
		return nil
	}
	v.p.Names = next
	if v.sh != nil {
		if err := v.localNamesLocked(next); err != nil {
			return err
		}
		go v.kick()
	}
	return v.saveLocked()
}
