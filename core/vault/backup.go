package vault

import (
	"cmp"
	"crypto/rand"
	"errors"
	"slices"
	"strings"
	"time"

	"github.com/Laky-64/appleservices/keychain"
	"golang.org/x/crypto/chacha20poly1305"
)

const (
	backupMagic   = "renvault-backup"
	backupVersion = 1
)

var (
	errBackupFormat   = errors.New("vault: this file is not a RenVault backup")
	errBackupPassword = errors.New("vault: wrong backup password")
)

type backupFile struct {
	Magic   string      `cbor:"magic"`
	Version int         `cbor:"version"`
	Salt    []byte      `cbor:"salt"`
	Argon   argonParams `cbor:"argon"`
	Nonce   []byte      `cbor:"nonce"`
	Cipher  []byte      `cbor:"cipher"`
}

type backupData struct {
	Created   time.Time              `cbor:"created"`
	Web       []keychain.WebPassword `cbor:"web,omitempty"`
	Name      string                 `cbor:"name,omitempty"`
	Photo     []byte                 `cbor:"photo,omitempty"`
	PhotoType string                 `cbor:"photo_type,omitempty"`
	Names     []NameSwap             `cbor:"names,omitempty"`
}

type ImportResult struct {
	Added   int
	Updated int
	Skipped int
}

func backupAD(f backupFile) []byte { return adOf(f.Magic, f.Version, f.Salt, f.Argon) }

func (v *Vault) ExportBackup(password string) ([]byte, error) {
	if password == "" {
		return nil, errors.New("vault: the backup needs a password")
	}
	v.mu.Lock()
	if v.p == nil || v.k == nil {
		v.mu.Unlock()
		return nil, errLocked
	}
	v.resetTimerLocked()
	data := backupData{
		Created:   time.Now().UTC(),
		Web:       slices.DeleteFunc(slices.Clone(v.p.Web), func(w keychain.WebPassword) bool { return w.IsDeleted }),
		Name:      v.p.ProfileName,
		Photo:     v.p.ProfilePhoto,
		PhotoType: v.p.ProfilePhotoType,
		Names:     slices.Clone(v.p.Names),
	}
	v.mu.Unlock()

	plain, err := cborEnc.Marshal(data)
	if err != nil {
		return nil, err
	}
	defer zero(plain)
	salt, err := newSalt()
	if err != nil {
		return nil, err
	}
	f := backupFile{Magic: backupMagic, Version: backupVersion, Salt: salt, Argon: defaultArgon()}
	kek := deriveKEK([]byte(password), salt, f.Argon)
	defer zero(kek)
	aead, err := chacha20poly1305.NewX(kek)
	if err != nil {
		return nil, err
	}
	f.Nonce = make([]byte, aead.NonceSize())
	if _, err := rand.Read(f.Nonce); err != nil {
		return nil, err
	}
	f.Cipher = aead.Seal(nil, f.Nonce, plain, backupAD(f))
	return cborEnc.Marshal(f)
}

func openBackup(raw []byte, password string) (backupData, error) {
	var data backupData
	var f backupFile
	if err := cborDec.Unmarshal(raw, &f); err != nil || f.Magic != backupMagic {
		return data, errBackupFormat
	}
	if f.Version != backupVersion {
		return data, errors.New("vault: this backup needs a newer RenVault")
	}
	if f.Argon.Memory > 1<<20 || f.Argon.Time > 16 || f.Argon.KeyLen != 32 || f.Argon.Threads == 0 {
		return data, errBackupFormat
	}
	kek := deriveKEK([]byte(password), f.Salt, f.Argon)
	defer zero(kek)
	aead, err := chacha20poly1305.NewX(kek)
	if err != nil {
		return data, err
	}
	if len(f.Nonce) != aead.NonceSize() {
		return data, errBackupFormat
	}
	plain, err := aead.Open(nil, f.Nonce, f.Cipher, backupAD(f))
	if err != nil {
		return data, errBackupPassword
	}
	defer zero(plain)
	if err := cborDec.Unmarshal(plain, &data); err != nil {
		return data, errBackupFormat
	}
	return data, nil
}

func (v *Vault) ImportBackup(raw []byte, password string) (ImportResult, error) {
	data, err := openBackup(raw, password)
	if err != nil {
		return ImportResult{}, err
	}
	res, err := v.importWeb(data.Web)
	if err != nil {
		return res, err
	}
	return res, v.mergeProfile(data)
}

func (v *Vault) mergeProfile(data backupData) error {
	v.mu.Lock()
	if v.p == nil {
		v.mu.Unlock()
		return errLocked
	}
	local := v.sh != nil
	name := v.p.ProfileName
	photo := len(v.p.ProfilePhoto) == 0 && len(data.Photo) > 0
	names := slices.Clone(v.p.Names)
	v.mu.Unlock()

	if local && ((name == "" && data.Name != "") || photo) {
		if err := v.SetProfile(cmp.Or(name, data.Name), data.Photo, photo); err != nil {
			return err
		}
	}
	for _, n := range data.Names {
		if !slices.ContainsFunc(names, func(o NameSwap) bool { return strings.EqualFold(o.From, n.From) }) {
			names = append(names, n)
		}
	}
	return v.SetNames(names)
}

func importKey(w keychain.WebPassword) string {
	if w.Website {
		return "site\x00" + strings.ToLower(w.Domain) + "\x00" + w.Username
	}
	return "note\x00" + strings.ToLower(strings.TrimSpace(w.Name)) + "\x00" + w.Username
}

func sameEntry(a, b keychain.WebPassword) bool {
	return a.Password == b.Password && a.Note == b.Note && a.TOTP == b.TOTP && a.Name == b.Name &&
		slices.Equal(associatedSites(a.Domains, a.Domain), associatedSites(b.Domains, b.Domain))
}

func (v *Vault) importWeb(entries []keychain.WebPassword) (ImportResult, error) {
	var res ImportResult
	err := v.mutate(func(p *payload) error {
		known := make(map[string]int, len(p.Web))
		for i, w := range p.Web {
			if !w.IsDeleted {
				known[importKey(w)] = i
			}
		}
		added := map[string]bool{}
		key := v.sharedKeyLocked()
		local := v.sh != nil
		dirty := false
		for _, e := range entries {
			if e.IsDeleted || (e.Domain == "" && strings.TrimSpace(e.Name) == "") {
				res.Skipped++
				continue
			}
			e = cleanImport(e)
			if added[importKey(e)] {
				res.Skipped++
				continue
			}
			if i, ok := known[importKey(e)]; ok {
				cur := p.Web[i]
				if sameEntry(cur, e) || !e.Modified.After(cur.Modified) {
					res.Skipped++
					continue
				}
				next := cur
				next.Name, next.Password, next.Note, next.TOTP = e.Name, e.Password, e.Note, e.TOTP
				if next.Website {
					next.Domains = append([]string{next.Domain}, associatedSites(append(cur.Domains, e.Domains...), next.Domain)...)
				}
				next.Modified = time.Now().UTC()
				if local {
					rec := v.recordOfLocked(webID(cur))
					if rec == "" {
						res.Skipped++
						continue
					}
					if err := v.sh.putWeb(key, rec, next); err != nil {
						return err
					}
					dirty = true
				} else {
					pushEdit(p, outboxOp{
						Kind:        opEdit,
						Domain:      cur.Domain,
						Username:    cur.Username,
						NewDomain:   cur.Domain,
						NewUsername: cur.Username,
						Password:    next.Password,
						Rewrite:     next.Password != cur.Password,
						Title:       next.Name,
						SetTitle:    next.Name != cur.Name,
						Note:        next.Note,
						SetNote:     next.Note != cur.Note,
						Domains:     associatedSites(next.Domains, next.Domain),
						SetDomains:  true,
						TOTP:        next.TOTP,
						DropTOTP:    next.TOTP == "" && cur.TOTP != "",
					}, v.inflight)
					p.Web[i] = next
				}
				res.Updated++
				continue
			}

			now := time.Now().UTC()
			if e.Created.IsZero() {
				e.Created = now
			}
			e.Modified = now
			if !e.Website {
				e.Domain = newLocalID()
				e.Domains = nil
			}
			if local {
				if err := v.sh.putWeb(key, "", e); err != nil {
					return err
				}
				dirty = true
			} else {
				p.Web = append(p.Web, e)
				pushOp(p, outboxOp{
					Kind:     opAdd,
					Domain:   e.Domain,
					Username: e.Username,
					Password: e.Password,
					Title:    e.Name,
					Note:     e.Note,
					Manual:   !e.Website,
				})
				extra := associatedSites(e.Domains, e.Domain)
				if e.TOTP != "" || len(extra) > 0 {
					pushOp(p, outboxOp{
						Kind:        opEdit,
						Domain:      e.Domain,
						Username:    e.Username,
						NewDomain:   e.Domain,
						NewUsername: e.Username,
						Password:    e.Password,
						Domains:     extra,
						SetDomains:  len(extra) > 0,
						TOTP:        e.TOTP,
					})
				}
			}
			added[importKey(e)] = true
			res.Added++
		}
		if dirty {
			v.rebuildShareLocked()
		}
		return nil
	})
	return res, err
}

func cleanImport(e keychain.WebPassword) keychain.WebPassword {
	e.Name = strings.TrimSpace(e.Name)
	e.Username = strings.TrimSpace(e.Username)
	e.Domain = strings.ToLower(strings.TrimSpace(e.Domain))
	e.Website = e.Website && keychain.IsWebsiteDomain(e.Domain)
	if e.Website {
		if e.Name == "" {
			e.Name = e.Domain
		}
		e.Domains = append([]string{e.Domain}, associatedSites(e.Domains, e.Domain)...)
	}
	if totp, err := normalizeTOTP(e.TOTP, e.Domain, e.Username); err == nil {
		e.TOTP = totp
	} else {
		e.TOTP = ""
	}
	e.IsDeleted, e.DeletedAt = false, time.Time{}
	return e
}
