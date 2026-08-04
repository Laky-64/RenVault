package vault

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/Laky-64/appleservices/keychain"
)

type Kind string

const (
	KindWeb     Kind = "web"
	KindWiFi    Kind = "wifi"
	KindPasskey Kind = "passkey"
)

type WebMeta struct {
	ID          string           `json:"id"`
	Title       string           `json:"title"`
	Username    string           `json:"username"`
	Domain      string           `json:"domain"`
	Domains     []string         `json:"domains"`
	Website     bool             `json:"website"`
	HasTOTP     bool             `json:"hasTotp"`
	Note        string           `json:"note"`
	IsDeleted   bool             `json:"isDeleted"`
	Groups      []keychain.Group `json:"groups"`
	Shared      bool             `json:"shared"`
	Pwned       bool             `json:"pwned"`
	HasPassword bool             `json:"hasPassword"`
	Created     time.Time        `json:"created"`
	Modified    time.Time        `json:"modified"`
	Deleted     time.Time        `json:"deleted"`
}

type WiFiMeta struct {
	ID       string    `json:"id"`
	SSID     string    `json:"ssid"`
	Created  time.Time `json:"created"`
	Modified time.Time `json:"modified"`
}

type PasskeyMeta struct {
	ID           string    `json:"id"`
	Title        string    `json:"title"`
	RelyingParty string    `json:"relyingParty"`
	UserName     string    `json:"username"`
	DisplayName  string    `json:"displayName"`
	CredentialID string    `json:"credentialId"`
	IsDeleted    bool      `json:"isDeleted"`
	Created      time.Time `json:"created"`
	Modified     time.Time `json:"modified"`
	Deleted      time.Time `json:"deleted"`
}

type passkeyEntry struct {
	Title        string    `json:"title"`
	RelyingParty string    `cbor:"rp"`
	UserName     string    `cbor:"username"`
	DisplayName  string    `cbor:"display_name"`
	CredentialID []byte    `cbor:"credential_id"`
	UserHandle   []byte    `cbor:"user_handle"`
	PrivateKeyD  []byte    `cbor:"private_key_d"`
	Created      time.Time `cbor:"created"`
	Modified     time.Time `cbor:"modified"`
	IsDeleted    bool      `cbor:"is_deleted,omitempty"`
	DeletedAt    time.Time `cbor:"deleted_at,omitempty"`
}

const p256ScalarLen = 32

func newPasskeyEntry(p keychain.Passkey) (passkeyEntry, error) {
	e := passkeyEntry{
		Title:        p.Title,
		RelyingParty: p.RelyingParty,
		UserName:     p.UserName,
		DisplayName:  p.DisplayName,
		CredentialID: p.CredentialID,
		UserHandle:   p.UserHandle,
		Created:      p.Created,
		Modified:     p.Modified,
		IsDeleted:    p.IsDeleted,
		DeletedAt:    p.DeletedAt,
	}
	if p.PrivateKey == nil {
		return passkeyEntry{}, errors.New("vault: passkey has no private key")
	}
	d, err := p.PrivateKey.Bytes()
	if err != nil {
		return passkeyEntry{}, err
	}
	e.PrivateKeyD = d
	return e, nil
}

func (e passkeyEntry) privateKey() (*ecdsa.PrivateKey, error) {
	if len(e.PrivateKeyD) != p256ScalarLen {
		return nil, errors.New("vault: passkey has no private key")
	}
	return ecdsa.ParseRawPrivateKey(elliptic.P256(), e.PrivateKeyD)
}

func (e passkeyEntry) passkey() (keychain.Passkey, error) {
	key, err := e.privateKey()
	if err != nil {
		return keychain.Passkey{}, err
	}
	return keychain.Passkey{
		Title:        e.Title,
		RelyingParty: e.RelyingParty,
		UserName:     e.UserName,
		DisplayName:  e.DisplayName,
		CredentialID: e.CredentialID,
		UserHandle:   e.UserHandle,
		PrivateKey:   key,
		Created:      e.Created,
		Modified:     e.Modified,
		IsDeleted:    e.IsDeleted,
		DeletedAt:    e.DeletedAt,
	}, nil
}

type ProfileMeta struct {
	Name     string `json:"name"`
	HasPhoto bool   `json:"hasPhoto"`
}

func hashID(prefix Kind, material ...string) string {
	data := append([]string{string(prefix)}, material...)
	sum := sha256.Sum256([]byte(strings.Join(data, "|")))
	return string(prefix) + ":" + hex.EncodeToString(sum[:])
}

func webID(w keychain.WebPassword) string {
	return hashID(
		KindWeb,
		w.Domain,
		w.Username,
		w.Name,
		strconv.FormatInt(w.Created.UnixNano(), 10),
		strconv.FormatInt(w.Modified.UnixNano(), 10),
	)
}

func wifiID(w keychain.WiFiPassword) string {
	return hashID(
		KindWiFi,
		w.SSID,
		strconv.FormatInt(w.Created.UnixNano(), 10),
		strconv.FormatInt(w.Modified.UnixNano(), 10),
	)
}

func passkeyID(p passkeyEntry) string {
	return hashID(
		KindPasskey,
		p.RelyingParty,
		p.UserName,
		hex.EncodeToString(p.CredentialID),
		strconv.FormatInt(p.Created.UnixNano(), 10),
		strconv.FormatInt(p.Modified.UnixNano(), 10),
	)
}

func webMetas(p payload) []WebMeta {
	out := make([]WebMeta, 0, len(p.Web))
	for _, w := range p.Web {
		id := webID(w)
		out = append(out, WebMeta{
			ID:          id,
			Title:       w.Name,
			Username:    w.Username,
			Domain:      w.Domain,
			Domains:     w.Domains,
			Website:     w.Website,
			HasTOTP:     w.TOTP != "",
			Note:        w.Note,
			IsDeleted:   w.IsDeleted,
			Groups:      w.Groups,
			Shared:      w.Shared,
			Pwned:       slices.Contains(p.Pwned, id),
			HasPassword: len(w.Password) > 0,
			Created:     w.Created,
			Modified:    w.Modified,
			Deleted:     w.DeletedAt,
		})
	}
	return out
}

func wifiMetas(p payload) []WiFiMeta {
	out := make([]WiFiMeta, 0, len(p.WiFi))
	for _, w := range p.WiFi {
		out = append(out, WiFiMeta{
			ID:       wifiID(w),
			SSID:     w.SSID,
			Created:  w.Created,
			Modified: w.Modified,
		})
	}
	return out
}

func passkeyMetas(p payload) []PasskeyMeta {
	out := make([]PasskeyMeta, 0, len(p.Passkey))
	for _, k := range p.Passkey {
		out = append(out, PasskeyMeta{
			ID:           passkeyID(k),
			Title:        k.Title,
			RelyingParty: k.RelyingParty,
			UserName:     k.UserName,
			DisplayName:  k.DisplayName,
			CredentialID: hex.EncodeToString(k.CredentialID),
			IsDeleted:    k.IsDeleted,
			Created:      k.Created,
			Modified:     k.Modified,
			Deleted:      k.DeletedAt,
		})
	}
	return out
}

func findWeb(p payload, id string) (keychain.WebPassword, bool) {
	for _, w := range p.Web {
		if webID(w) == id {
			return w, true
		}
	}
	return keychain.WebPassword{}, false
}

func findWiFi(p payload, id string) (keychain.WiFiPassword, bool) {
	for _, w := range p.WiFi {
		if wifiID(w) == id {
			return w, true
		}
	}
	return keychain.WiFiPassword{}, false
}

func findPasskey(p payload, id string) (passkeyEntry, bool) {
	for _, k := range p.Passkey {
		if passkeyID(k) == id {
			return k, true
		}
	}
	return passkeyEntry{}, false
}
