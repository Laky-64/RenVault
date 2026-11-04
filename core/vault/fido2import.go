package vault

import (
	"cmp"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"strings"
	"time"
)

const bitwardenBase64Prefix = "b64."

func bitwardenPasskeyEntry(c bitwardenPasskey, title, note string) (passkeyEntry, bool) {
	rp := normalizeRP(c.RpID)
	if rp == "" || (c.KeyAlgorithm != "" && !strings.EqualFold(c.KeyAlgorithm, "ECDSA")) || (c.KeyCurve != "" && !strings.EqualFold(c.KeyCurve, "P-256")) {
		return passkeyEntry{}, false
	}
	scalar, ok := bitwardenScalar(c.KeyValue)
	if !ok {
		return passkeyEntry{}, false
	}
	id, ok := bitwardenCredentialID(c.CredentialID)
	if !ok {
		zero(scalar)
		return passkeyEntry{}, false
	}
	handle, _ := decodeBase64Any(c.UserHandle)
	created, _ := time.Parse(time.RFC3339Nano, c.CreationDate)
	siteTitle := strings.TrimSpace(c.RpName)
	return passkeyEntry{
		Title:        cmp.Or(namedTitle(title, rp), siteTitle, rp),
		SiteTitle:    siteTitle,
		RelyingParty: rp,
		UserName:     strings.TrimSpace(c.UserName),
		DisplayName:  strings.TrimSpace(c.UserDisplayName),
		Note:         note,
		CredentialID: id,
		UserHandle:   handle,
		PrivateKeyD:  scalar,
		Created:      created.UTC(),
		Modified:     created.UTC(),
	}, true
}

func namedTitle(title, rp string) string {
	if strings.EqualFold(strings.TrimPrefix(title, "www."), rp) {
		return ""
	}
	return title
}

func bitwardenScalar(value string) ([]byte, bool) {
	der, ok := decodeBase64Any(value)
	if !ok {
		return nil, false
	}
	defer zero(der)
	parsed, err := x509.ParsePKCS8PrivateKey(der)
	if err != nil {
		return nil, false
	}
	key, ok := parsed.(*ecdsa.PrivateKey)
	if !ok || key.Curve != elliptic.P256() {
		return nil, false
	}
	private, err := key.ECDH()
	if err != nil {
		return nil, false
	}
	scalar := private.Bytes()
	return scalar, len(scalar) == p256ScalarLen
}

func bitwardenCredentialID(value string) ([]byte, bool) {
	value = strings.TrimSpace(value)
	if rest, ok := strings.CutPrefix(value, bitwardenBase64Prefix); ok {
		id, ok := decodeBase64Any(rest)
		return id, ok && len(id) > 0
	}
	if guid, err := hex.DecodeString(strings.ReplaceAll(value, "-", "")); err == nil && len(guid) == 16 {
		return guid, true
	}
	id, ok := decodeBase64Any(value)
	return id, ok && len(id) > 0
}

func decodeBase64Any(value string) ([]byte, bool) {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil, false
	}
	for _, encoding := range []*base64.Encoding{base64.RawURLEncoding, base64.URLEncoding, base64.RawStdEncoding, base64.StdEncoding} {
		if data, err := encoding.DecodeString(value); err == nil {
			return data, true
		}
	}
	return nil, false
}
