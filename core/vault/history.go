package vault

import (
	"slices"
	"strings"
	"time"

	"github.com/Laky-64/appleservices/keychain"
)

const (
	historyKeep   = 10
	generatedKeep = 50
	generatedTTL  = 30 * 24 * time.Hour
)

type passwordVersion struct {
	Password string    `cbor:"password"`
	Until    time.Time `cbor:"until"`
}

type generatedEntry struct {
	Password string    `cbor:"password"`
	Domain   string    `cbor:"domain,omitempty"`
	At       time.Time `cbor:"at"`
}

type PasswordVersion struct {
	Password string
	Until    time.Time
}

type GeneratedPassword struct {
	Password string
	Domain   string
	At       time.Time
	Saved    bool
}

func rememberPassword(p *payload, key, old, next string) {
	if old == "" || old == next {
		return
	}
	list := p.History[key]
	if len(list) > 0 && list[0].Password == old {
		return
	}
	if p.History == nil {
		p.History = map[string][]passwordVersion{}
	}
	list = slices.DeleteFunc(slices.Clone(list), func(v passwordVersion) bool { return v.Password == old || v.Password == next })
	list = append([]passwordVersion{{Password: old, Until: time.Now().UTC()}}, list...)
	if len(list) > historyKeep {
		list = list[:historyKeep]
	}
	p.History[key] = list
}

func moveHistory(p *payload, from, to string) {
	if from == to {
		return
	}
	list, ok := p.History[from]
	if !ok {
		return
	}
	delete(p.History, from)
	p.History[to] = append(p.History[to], list...)
}

func trackHistory(p *payload, before []keychain.WebPassword) {
	previous := make(map[string]string, len(before))
	for _, w := range before {
		previous[webKey(w)] = w.Password
	}
	live := make(map[string]bool, len(p.Web))
	for _, w := range p.Web {
		key := webKey(w)
		live[key] = true
		if old, ok := previous[key]; ok {
			rememberPassword(p, key, old, w.Password)
		}
	}
	for key := range p.History {
		if !live[key] {
			delete(p.History, key)
		}
	}
}

func (v *Vault) PasswordHistory(id string) ([]PasswordVersion, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil || v.k == nil {
		return nil, errLocked
	}
	v.resetTimerLocked()
	i := webIndex(v.p, id)
	if i < 0 {
		return nil, errNotFound
	}
	list := v.p.History[webKey(v.p.Web[i])]
	out := make([]PasswordVersion, 0, len(list))
	for _, version := range list {
		out = append(out, PasswordVersion{Password: version.Password, Until: version.Until})
	}
	return out, nil
}

func (v *Vault) RecordGenerated(password, domain string) error {
	if password == "" {
		return nil
	}
	return v.mutate(func(p *payload) error {
		p.Generated = slices.DeleteFunc(p.Generated, func(g generatedEntry) bool { return g.Password == password })
		p.Generated = append([]generatedEntry{{Password: password, Domain: strings.TrimSpace(domain), At: time.Now().UTC()}}, p.Generated...)
		pruneGenerated(p)
		return nil
	})
}

func (v *Vault) GeneratedPasswords() ([]GeneratedPassword, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil || v.k == nil {
		return nil, errLocked
	}
	v.resetTimerLocked()
	pruneGenerated(v.p)
	out := make([]GeneratedPassword, 0, len(v.p.Generated))
	for _, g := range v.p.Generated {
		saved := slices.ContainsFunc(v.p.Web, func(w keychain.WebPassword) bool { return !w.IsDeleted && w.Password == g.Password })
		out = append(out, GeneratedPassword{Password: g.Password, Domain: g.Domain, At: g.At, Saved: saved})
	}
	return out, nil
}

func (v *Vault) RemoveGenerated(password string) error {
	return v.mutate(func(p *payload) error {
		p.Generated = slices.DeleteFunc(p.Generated, func(g generatedEntry) bool { return g.Password == password })
		return nil
	})
}

func (v *Vault) ClearGenerated() error {
	return v.mutate(func(p *payload) error {
		p.Generated = nil
		return nil
	})
}

func pruneGenerated(p *payload) {
	cutoff := time.Now().Add(-generatedTTL)
	p.Generated = slices.DeleteFunc(p.Generated, func(g generatedEntry) bool { return g.At.Before(cutoff) })
	if len(p.Generated) > generatedKeep {
		p.Generated = p.Generated[:generatedKeep]
	}
}
