package vault

import (
	"bytes"
	"cmp"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"slices"
	"strings"
	"time"

	"github.com/Laky-64/appleservices/keychain"
	"github.com/Laky-64/renvault-core/ctap"
	"github.com/fxamacker/cbor/v2"
)

type Registration struct {
	CredentialID      []byte `json:"credentialId"`
	AuthenticatorData []byte `json:"authenticatorData"`
}

const (
	flagAttestedData = 0x40
)

func normalizeRP(rp string) string {
	return strings.ToLower(strings.TrimSpace(rp))
}

func newCredential(rp, userName, displayName string, userHandle []byte) (passkeyEntry, error) {
	rp = normalizeRP(rp)
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return passkeyEntry{}, err
	}
	d, err := key.Bytes()
	if err != nil {
		return passkeyEntry{}, err
	}
	pub, err := key.PublicKey.Bytes()
	if err != nil {
		zero(d)
		return passkeyEntry{}, err
	}
	sum := sha1.Sum(pub)
	id := sum[:]
	now := time.Now().UTC()
	return passkeyEntry{
		Title:        rp,
		SiteTitle:    rp,
		RelyingParty: rp,
		UserName:     userName,
		DisplayName:  displayName,
		CredentialID: id,
		UserHandle:   append([]byte(nil), userHandle...),
		PrivateKeyD:  d,
		Created:      now,
		Modified:     now,
	}, nil
}

func coseKey(pub *ecdsa.PublicKey) ([]byte, error) {
	raw, err := pub.Bytes()
	if err != nil {
		return nil, err
	}
	if len(raw) != 1+2*p256ScalarLen {
		return nil, errors.New("vault: unexpected public key size")
	}
	mode, err := cbor.CTAP2EncOptions().EncMode()
	if err != nil {
		return nil, err
	}
	return mode.Marshal(map[int]any{
		1:  2,
		3:  -7,
		-1: 1,
		-2: raw[1 : 1+p256ScalarLen],
		-3: raw[1+p256ScalarLen:],
	})
}

func attestedAuthData(e passkeyEntry, userVerified bool) ([]byte, error) {
	key, err := e.privateKey()
	if err != nil {
		return nil, err
	}
	cose, err := coseKey(&key.PublicKey)
	if err != nil {
		return nil, err
	}
	rpHash := sha256.Sum256([]byte(e.RelyingParty))
	flags := byte(keychain.FlagUserPresent) | flagBackupEligible | flagBackupState | flagAttestedData
	if userVerified {
		flags |= keychain.FlagUserVerified
	}
	out := append(rpHash[:], flags)
	out = binary.BigEndian.AppendUint32(out, 0)
	out = append(out, ctap.AAGUID[:]...)
	out = binary.BigEndian.AppendUint16(out, uint16(len(e.CredentialID)))
	out = append(out, e.CredentialID...)
	return append(out, cose...), nil
}

func (v *Vault) CreatePasskey(rp, userName, displayName string, userHandle []byte, userVerified bool) (Registration, error) {
	if normalizeRP(rp) == "" || len(userHandle) == 0 {
		return Registration{}, errors.New("vault: a passkey needs a site and a user")
	}
	e, err := newCredential(rp, userName, displayName, userHandle)
	if err != nil {
		return Registration{}, err
	}
	defer zero(e.PrivateKeyD)
	authData, err := attestedAuthData(e, userVerified)
	if err != nil {
		return Registration{}, err
	}
	err = v.mutate(func(p *payload) error {
		if v.sh == nil {
			v.createICloudPasskey(p, e)
			return nil
		}
		key := v.sharedKeyLocked()
		now := time.Now().UTC()
		for i, cur := range p.Passkey {
			if cur.IsDeleted || normalizeRP(cur.RelyingParty) != e.RelyingParty || !bytes.Equal(cur.UserHandle, e.UserHandle) || i >= len(v.keyIDs) {
				continue
			}
			cur.IsDeleted, cur.DeletedAt = true, now
			if err := v.sh.putPasskey(key, v.keyIDs[i], cur); err != nil {
				return err
			}
		}
		if err := v.sh.putPasskey(key, "", e); err != nil {
			return err
		}
		v.rebuildShareLocked()
		return nil
	})
	if err != nil {
		return Registration{}, err
	}
	return Registration{CredentialID: slices.Clone(e.CredentialID), AuthenticatorData: authData}, nil
}

func (v *Vault) DeletePasskey(id string) error {
	return v.trashPasskey(id, true)
}

func (v *Vault) RestorePasskey(id string) error {
	return v.trashPasskey(id, false)
}

func (v *Vault) trashPasskey(id string, trash bool) error {
	return v.mutate(func(p *payload) error {
		if v.sh == nil {
			return v.trashICloudPasskey(p, id, trash)
		}
		i := slices.IndexFunc(p.Passkey, func(e passkeyEntry) bool { return passkeyID(e) == id })
		rec := v.passkeyRecordOfLocked(id)
		if i < 0 || rec == "" {
			return errNotFound
		}
		e := p.Passkey[i]
		if e.IsDeleted == trash {
			return nil
		}
		e.IsDeleted = trash
		e.DeletedAt = time.Time{}
		if trash {
			e.DeletedAt = time.Now().UTC()
		}
		if err := v.sh.putPasskey(v.sharedKeyLocked(), rec, e); err != nil {
			return err
		}
		v.rebuildShareLocked()
		return nil
	})
}

func (v *Vault) PurgePasskey(id string) error {
	return v.mutate(func(p *payload) error {
		if v.sh == nil {
			return v.purgeICloudPasskey(p, id)
		}
		i := slices.IndexFunc(p.Passkey, func(e passkeyEntry) bool { return passkeyID(e) == id })
		rec := v.passkeyRecordOfLocked(id)
		if i < 0 || rec == "" || !p.Passkey[i].IsDeleted {
			return errNotFound
		}
		if err := v.sh.purge(v.sharedKeyLocked(), rec); err != nil {
			return err
		}
		v.rebuildShareLocked()
		return nil
	})
}

func passkeyOpKey(credentialID []byte) string {
	return string(KindPasskey) + ":" + hex.EncodeToString(credentialID)
}

func clonePasskey(e passkeyEntry) passkeyEntry {
	e.CredentialID = slices.Clone(e.CredentialID)
	e.UserHandle = slices.Clone(e.UserHandle)
	e.PrivateKeyD = slices.Clone(e.PrivateKeyD)
	return e
}

func passkeyIndex(p *payload, id string) int {
	return slices.IndexFunc(p.Passkey, func(e passkeyEntry) bool { return passkeyID(e) == id })
}

func queuePasskeyAdd(p *payload, e passkeyEntry) {
	queued := clonePasskey(e)
	p.Passkey = append(p.Passkey, clonePasskey(e))
	pushOp(p, outboxOp{
		Kind:         opPasskeyAdd,
		Domain:       passkeyOpKey(e.CredentialID),
		CredentialID: slices.Clone(e.CredentialID),
		Passkey:      &queued,
	})
}

func (v *Vault) createICloudPasskey(p *payload, e passkeyEntry) {
	for i := len(p.Passkey) - 1; i >= 0; i-- {
		cur := p.Passkey[i]
		if cur.IsDeleted || normalizeRP(cur.RelyingParty) != e.RelyingParty || !bytes.Equal(cur.UserHandle, e.UserHandle) {
			continue
		}
		v.trashICloudPasskeyAt(p, i)
	}
	queuePasskeyAdd(p, e)
}

func (v *Vault) trashICloudPasskeyAt(p *payload, i int) {
	e := p.Passkey[i]
	key := passkeyOpKey(e.CredentialID)
	if findOp(p, opPasskeyAdd, key, "", v.inflight) >= 0 {
		dropOps(p, key, "", v.inflight)
		zero(e.PrivateKeyD)
		p.Passkey = slices.Delete(p.Passkey, i, i+1)
		return
	}
	pushOp(p, outboxOp{Kind: opPasskeyDelete, Domain: key, CredentialID: slices.Clone(e.CredentialID)})
	p.Passkey[i].IsDeleted = true
	p.Passkey[i].DeletedAt = time.Now().UTC()
}

func (v *Vault) trashICloudPasskey(p *payload, id string, trash bool) error {
	i := passkeyIndex(p, id)
	if i < 0 {
		return errNotFound
	}
	e := p.Passkey[i]
	if e.IsDeleted == trash {
		return nil
	}
	if trash {
		v.trashICloudPasskeyAt(p, i)
		return nil
	}
	key := passkeyOpKey(e.CredentialID)
	if at := findOp(p, opPasskeyDelete, key, "", v.inflight); at >= 0 {
		p.Outbox = slices.Delete(p.Outbox, at, at+1)
	} else {
		pushOp(p, outboxOp{Kind: opPasskeyRestore, Domain: key, CredentialID: slices.Clone(e.CredentialID)})
	}
	p.Passkey[i].IsDeleted = false
	p.Passkey[i].DeletedAt = time.Time{}
	return nil
}

func (v *Vault) purgeICloudPasskey(p *payload, id string) error {
	i := passkeyIndex(p, id)
	if i < 0 || !p.Passkey[i].IsDeleted {
		return errNotFound
	}
	e := p.Passkey[i]
	pushOp(p, outboxOp{Kind: opPasskeyPurge, Domain: passkeyOpKey(e.CredentialID), CredentialID: slices.Clone(e.CredentialID)})
	zero(e.PrivateKeyD)
	p.Passkey = slices.Delete(p.Passkey, i, i+1)
	return nil
}

func (v *Vault) DeletePasskeys(ids []string) error {
	return eachPasskey(ids, v.DeletePasskey)
}

func (v *Vault) PurgePasskeys(ids []string) error {
	return eachPasskey(ids, v.PurgePasskey)
}

func eachPasskey(ids []string, run func(string) error) error {
	var first error
	for _, id := range ids {
		if err := run(id); err != nil && first == nil {
			first = err
		}
	}
	return first
}

func (v *Vault) EditPasskey(id, title, userName, note string, sites []string) (string, error) {
	title = strings.TrimSpace(title)
	userName = strings.TrimSpace(userName)
	var next string
	err := v.mutate(func(p *payload) error {
		i := passkeyIndex(p, id)
		if i < 0 || p.Passkey[i].IsDeleted {
			return errNotFound
		}
		e := clonePasskey(p.Passkey[i])
		defer zero(e.PrivateKeyD)
		extra := passkeySites(e.RelyingParty, sites)
		e.UserName, e.Note, e.Sites = userName, note, extra
		e.CustomTitle = title
		e.Title = cmp.Or(title, e.SiteTitle, e.RelyingParty)
		if v.sh != nil {
			rec := v.passkeyRecordOfLocked(id)
			if rec == "" {
				return errNotFound
			}
			if err := v.sh.putPasskey(v.sharedKeyLocked(), rec, e); err != nil {
				return err
			}
			v.rebuildShareLocked()
		} else {
			cur := &p.Passkey[i]
			cur.Title, cur.CustomTitle = e.Title, e.CustomTitle
			cur.UserName, cur.Note, cur.Sites, cur.Modified = userName, note, extra, time.Now().UTC()
			pushOp(p, outboxOp{
				Kind:         opPasskeyEdit,
				Domain:       passkeyOpKey(e.CredentialID),
				CredentialID: slices.Clone(e.CredentialID),
				NewUsername:  userName,
				Title:        title,
				SetTitle:     true,
				Note:         note,
				SetNote:      true,
				Domains:      slices.Clone(extra),
				SetDomains:   true,
			})
		}
		for _, k := range p.Passkey {
			if bytes.Equal(k.CredentialID, e.CredentialID) && !k.IsDeleted {
				next = passkeyID(k)
			}
		}
		return nil
	})
	return next, err
}

func passkeySites(relyingParty string, sites []string) []string {
	rp := normalizeRP(relyingParty)
	var out []string
	for _, site := range sites {
		site = normalizeRP(site)
		if site == "" || site == rp || slices.Contains(out, site) {
			continue
		}
		out = append(out, site)
	}
	return out
}
