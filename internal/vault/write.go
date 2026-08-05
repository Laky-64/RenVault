package vault

import (
	"errors"
	"slices"
	"strings"
	"time"

	"github.com/Laky-64/appleservices"
	"github.com/Laky-64/appleservices/keychain"
)

var errNotFound = errors.New("vault: entry not found")

func (v *Vault) keychainSession() (*appleservices.KeychainVault, error) {
	v.mu.Lock()
	if v.p == nil || v.k == nil {
		v.mu.Unlock()
		return nil, errLocked
	}
	creds := v.p.Credentials
	peer := v.p.Peer
	session := v.p.Session
	v.mu.Unlock()

	v.store.set(session)
	v.sess.SetCredentials(creds)
	return v.sess.Keychain(peer)
}

func (v *Vault) mutate(fn func(p *payload) error) error {
	v.mu.Lock()
	if v.p == nil || v.k == nil {
		v.mu.Unlock()
		return errLocked
	}
	v.resetTimerLocked()
	if err := fn(v.p); err != nil {
		v.mu.Unlock()
		return err
	}
	if sess, ok := v.store.take(); ok {
		v.p.Session = sess
	}
	err := v.saveLocked()
	v.mu.Unlock()
	if err != nil {
		return err
	}
	v.kick()
	return nil
}

func webIndex(p *payload, id string) int {
	return slices.IndexFunc(p.Web, func(w keychain.WebPassword) bool { return webID(w) == id })
}

func repointPwned(p *payload, from, to string) {
	if from == to {
		return
	}
	for i := range p.Pwned {
		if p.Pwned[i] == from {
			p.Pwned[i] = to
		}
	}
	slices.Sort(p.Pwned)
}

func (v *Vault) AddPassword(title, website, username, password, note string) error {
	title = strings.TrimSpace(title)
	website = strings.TrimSpace(website)
	username = strings.TrimSpace(username)
	if website == "" && title == "" {
		return errors.New("vault: an entry needs a website or a title")
	}
	manual := website == ""
	domain := website
	name := website
	if manual {
		domain = newLocalID()
		name = title
	}
	now := time.Now().UTC()
	entry := keychain.WebPassword{
		Name:     name,
		Domain:   domain,
		Website:  !manual,
		Username: username,
		Password: password,
		Note:     note,
		Created:  now,
		Modified: now,
	}
	if !manual {
		entry.Domains = []string{domain}
	}
	return v.mutate(func(p *payload) error {
		p.Web = append(p.Web, entry)
		pushOp(p, outboxOp{
			Kind:     opAdd,
			Domain:   domain,
			Username: username,
			Password: password,
			Title:    title,
			Note:     note,
			Manual:   manual,
		})
		return nil
	})
}

func (v *Vault) EditPassword(id, website, username, password, note, totpURL string, dropTOTP bool) error {
	website = strings.TrimSpace(website)
	username = strings.TrimSpace(username)
	note = strings.TrimSpace(note)
	totpURL = strings.TrimSpace(totpURL)
	return v.mutate(func(p *payload) error {
		i := webIndex(p, id)
		if i < 0 {
			return errNotFound
		}
		w := p.Web[i]
		domain := website
		if domain == "" {
			domain = w.Domain
		}
		account := username
		if account == "" {
			account = w.Username
		}
		totp, err := normalizeTOTP(totpURL, domain, account)
		if err != nil {
			return err
		}
		rewrite := domain != w.Domain || account != w.Username || password != w.Password
		drop := dropTOTP && w.TOTP != ""
		setNote := note != w.Note
		if !rewrite && totp == "" && !drop && !setNote {
			return nil
		}
		pushEdit(p, outboxOp{
			Kind:        opEdit,
			Domain:      w.Domain,
			Username:    w.Username,
			NewDomain:   domain,
			NewUsername: account,
			Password:    password,
			Note:        note,
			SetNote:     setNote,
			TOTP:        totp,
			DropTOTP:    drop,
			Rewrite:     rewrite,
		}, v.inflight)
		e := &p.Web[i]
		if setNote {
			e.Note = note
		}
		if totp != "" {
			e.TOTP = totp
		} else if drop {
			e.TOTP = ""
		}
		if rewrite {
			e.Domains = domainsAfterRename(e.Domains, e.Domain, domain)
			e.Domain = domain
			e.Username = account
			e.Password = password
		}
		e.Modified = time.Now().UTC()
		repointPwned(p, webKey(w), webKey(*e))
		return nil
	})
}

func domainsAfterRename(domains []string, from, to string) []string {
	if from == to || len(domains) == 0 {
		return domains
	}
	out := make([]string, 0, len(domains))
	for _, d := range domains {
		if d == from {
			d = to
		}
		if !slices.Contains(out, d) {
			out = append(out, d)
		}
	}
	return out
}

func (v *Vault) DeletePassword(id string) error {
	return v.mutate(func(p *payload) error {
		return v.deleteWeb(p, id)
	})
}

func (v *Vault) DeletePasswords(ids []string) error {
	return v.mutate(func(p *payload) error {
		return v.eachWeb(p, ids, v.deleteWeb)
	})
}

func (v *Vault) eachWeb(p *payload, ids []string, run func(*payload, string) error) error {
	for _, id := range ids {
		if err := run(p, id); err != nil && !errors.Is(err, errNotFound) {
			return err
		}
	}
	return nil
}

func (v *Vault) deleteWeb(p *payload, id string) error {
	i := webIndex(p, id)
	if i < 0 {
		return errNotFound
	}
	w := p.Web[i]
	if w.IsDeleted {
		return nil
	}
	if findOp(p, opAdd, w.Domain, w.Username, v.inflight) >= 0 {
		dropOps(p, w.Domain, w.Username, v.inflight)
		p.Web = slices.Delete(p.Web, i, i+1)
		return nil
	}
	pushOp(p, outboxOp{Kind: opDelete, Domain: w.Domain, Username: w.Username})
	p.Web[i].IsDeleted = true
	p.Web[i].DeletedAt = time.Now().UTC()
	return nil
}

func (v *Vault) RestorePassword(id string) error {
	return v.mutate(func(p *payload) error {
		i := webIndex(p, id)
		if i < 0 {
			return errNotFound
		}
		w := p.Web[i]
		if !w.IsDeleted {
			return nil
		}
		if at := findOp(p, opDelete, w.Domain, w.Username, v.inflight); at >= 0 {
			p.Outbox = slices.Delete(p.Outbox, at, at+1)
		} else {
			pushOp(p, outboxOp{Kind: opRestore, Domain: w.Domain, Username: w.Username})
		}
		p.Web[i].IsDeleted = false
		p.Web[i].DeletedAt = time.Time{}
		return nil
	})
}

func (v *Vault) PurgePassword(id string) error {
	return v.mutate(func(p *payload) error {
		return v.purgeWeb(p, id)
	})
}

func (v *Vault) PurgePasswords(ids []string) error {
	return v.mutate(func(p *payload) error {
		return v.eachWeb(p, ids, v.purgeWeb)
	})
}

func (v *Vault) purgeWeb(p *payload, id string) error {
	i := webIndex(p, id)
	if i < 0 {
		return errNotFound
	}
	w := p.Web[i]
	if !w.IsDeleted {
		return errNotFound
	}
	pushOp(p, outboxOp{Kind: opPurge, Domain: w.Domain, Username: w.Username})
	p.Web = slices.Delete(p.Web, i, i+1)
	p.Pwned = slices.DeleteFunc(p.Pwned, func(s string) bool { return s == webKey(w) })
	return nil
}
