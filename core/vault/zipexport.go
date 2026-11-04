package vault

import (
	"cmp"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"errors"
	"slices"
	"strings"
	"time"

	"github.com/Laky-64/appleservices/keychain"
)

const (
	archiveVaultEntry = "vault.json"
	archiveComment    = "RenVault"
)

type bitwardenExportItem struct {
	ID             string               `json:"id"`
	OrganizationID *string              `json:"organizationId"`
	FolderID       *string              `json:"folderId"`
	Type           int                  `json:"type"`
	Reprompt       int                  `json:"reprompt"`
	Name           string               `json:"name"`
	Notes          *string              `json:"notes"`
	Favorite       bool                 `json:"favorite"`
	Login          bitwardenExportLogin `json:"login"`
	CollectionIDs  *[]string            `json:"collectionIds"`
	RevisionDate   string               `json:"revisionDate"`
	CreationDate   string               `json:"creationDate"`
}

type bitwardenExportLogin struct {
	Username *string                  `json:"username"`
	Password *string                  `json:"password"`
	Totp     *string                  `json:"totp"`
	Uris     []bitwardenExportURI     `json:"uris"`
	Fido2    []bitwardenExportPasskey `json:"fido2Credentials"`
}

type bitwardenExportURI struct {
	Match *int   `json:"match"`
	URI   string `json:"uri"`
}

type bitwardenExportPasskey struct {
	CredentialID    string `json:"credentialId"`
	KeyType         string `json:"keyType"`
	KeyAlgorithm    string `json:"keyAlgorithm"`
	KeyCurve        string `json:"keyCurve"`
	KeyValue        string `json:"keyValue"`
	RpID            string `json:"rpId"`
	RpName          string `json:"rpName"`
	UserHandle      string `json:"userHandle"`
	UserName        string `json:"userName"`
	UserDisplayName string `json:"userDisplayName"`
	Counter         string `json:"counter"`
	Discoverable    string `json:"discoverable"`
	CreationDate    string `json:"creationDate"`
}

func (v *Vault) ExportArchive(password string) ([]byte, error) {
	if password == "" {
		return nil, errors.New("vault: the archive needs a password")
	}
	v.mu.Lock()
	if v.p == nil || v.k == nil {
		v.mu.Unlock()
		return nil, errLocked
	}
	v.resetTimerLocked()
	web := slices.DeleteFunc(slices.Clone(v.p.Web), func(w keychain.WebPassword) bool { return w.IsDeleted })
	passkeys := activePasskeyCopies(v.p.Passkey)
	v.mu.Unlock()
	defer zeroPasskeys(passkeys)

	items := make([]bitwardenExportItem, 0, len(web)+len(passkeys))
	attached := make([]bool, len(passkeys))
	for _, w := range web {
		item := bitwardenWebItem(w)
		for i, p := range passkeys {
			if attached[i] || !passkeyBelongs(p, w) {
				continue
			}
			if credential, ok := bitwardenPasskeyOf(p); ok {
				item.Login.Fido2 = append(item.Login.Fido2, credential)
				attached[i] = true
			}
		}
		items = append(items, item)
	}
	for i, p := range passkeys {
		if attached[i] {
			continue
		}
		credential, ok := bitwardenPasskeyOf(p)
		if !ok {
			continue
		}
		items = append(items, bitwardenPasskeyItem(p, credential))
	}

	plain, err := json.MarshalIndent(map[string]any{
		"encrypted": false,
		"folders":   []any{},
		"items":     items,
	}, "", "  ")
	if err != nil {
		return nil, err
	}
	defer zero(plain)
	return writeEncryptedZip(archiveVaultEntry, plain, password, archiveComment)
}

func bitwardenWebItem(w keychain.WebPassword) bitwardenExportItem {
	var uris []bitwardenExportURI
	seen := map[string]bool{}
	add := func(uri string) {
		if uri != "" && !seen[uri] {
			seen[uri] = true
			uris = append(uris, bitwardenExportURI{URI: uri})
		}
	}
	for _, domain := range append([]string{w.Domain}, w.Domains...) {
		switch {
		case isAppLink(domain):
			add(domain)
		case w.Website && domain != "":
			add("https://" + domain)
		}
	}
	created := cmp.Or(w.Created, w.Modified)
	return bitwardenExportItem{
		ID:           newLocalID(),
		Type:         bitwardenLoginType,
		Name:         csvName(w),
		Notes:        optional(w.Note),
		RevisionDate: exportTime(w.Modified),
		CreationDate: exportTime(created),
		Login: bitwardenExportLogin{
			Username: optional(w.Username),
			Password: optional(w.Password),
			Totp:     optional(w.TOTP),
			Uris:     uris,
			Fido2:    []bitwardenExportPasskey{},
		},
	}
}

func bitwardenPasskeyItem(p passkeyEntry, credential bitwardenExportPasskey) bitwardenExportItem {
	return bitwardenExportItem{
		ID:           newLocalID(),
		Type:         bitwardenLoginType,
		Name:         cmp.Or(p.CustomTitle, p.Title, p.SiteTitle, p.RelyingParty),
		Notes:        optional(p.Note),
		RevisionDate: exportTime(cmp.Or(p.Modified, p.Created)),
		CreationDate: exportTime(p.Created),
		Login: bitwardenExportLogin{
			Username: optional(p.UserName),
			Uris:     []bitwardenExportURI{{URI: "https://" + p.RelyingParty}},
			Fido2:    []bitwardenExportPasskey{credential},
		},
	}
}

func passkeyBelongs(p passkeyEntry, w keychain.WebPassword) bool {
	if !w.Website || !strings.EqualFold(strings.TrimSpace(p.UserName), strings.TrimSpace(w.Username)) {
		return false
	}
	rp := normalizeRP(p.RelyingParty)
	return slices.ContainsFunc(append([]string{w.Domain}, w.Domains...), func(domain string) bool {
		domain = strings.ToLower(strings.TrimPrefix(domain, "www."))
		return domain == rp || strings.HasSuffix(rp, "."+domain) || strings.HasSuffix(domain, "."+rp)
	})
}

func bitwardenPasskeyOf(p passkeyEntry) (bitwardenExportPasskey, bool) {
	if len(p.PrivateKeyD) != p256ScalarLen || len(p.CredentialID) == 0 {
		return bitwardenExportPasskey{}, false
	}
	key, err := ecdsa.ParseRawPrivateKey(elliptic.P256(), p.PrivateKeyD)
	if err != nil {
		return bitwardenExportPasskey{}, false
	}
	der, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return bitwardenExportPasskey{}, false
	}
	defer zero(der)
	return bitwardenExportPasskey{
		CredentialID:    bitwardenBase64Prefix + base64.RawURLEncoding.EncodeToString(p.CredentialID),
		KeyType:         "public-key",
		KeyAlgorithm:    "ECDSA",
		KeyCurve:        "P-256",
		KeyValue:        base64.RawURLEncoding.EncodeToString(der),
		RpID:            p.RelyingParty,
		RpName:          cmp.Or(p.SiteTitle, p.RelyingParty),
		UserHandle:      base64.RawURLEncoding.EncodeToString(p.UserHandle),
		UserName:        p.UserName,
		UserDisplayName: cmp.Or(p.DisplayName, p.UserName),
		Counter:         "0",
		Discoverable:    "true",
		CreationDate:    exportTime(p.Created),
	}, true
}

func optional(value string) *string {
	if value == "" {
		return nil
	}
	return &value
}

func exportTime(t time.Time) string {
	if t.IsZero() {
		t = time.Now()
	}
	return t.UTC().Format("2006-01-02T15:04:05.000Z")
}
