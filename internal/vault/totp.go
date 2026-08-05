package vault

import (
	"encoding/base32"
	"errors"
	"net/url"
	"strings"
)

func normalizeTOTP(input, domain, username string) (string, error) {
	input = strings.TrimSpace(input)
	if input == "" {
		return "", nil
	}
	if u, err := url.Parse(input); err == nil && u.Scheme == "otpauth" {
		if _, err := setupKey(u.Query().Get("secret")); err != nil {
			return "", err
		}
		return input, nil
	}
	secret, err := setupKey(input)
	if err != nil {
		return "", err
	}

	q := url.Values{}
	q.Set("secret", secret)
	label := username
	if domain != "" {
		q.Set("issuer", domain)
		if username == "" {
			label = domain
		} else {
			label = domain + ":" + username
		}
	}
	u := url.URL{Scheme: "otpauth", Host: "totp", Path: "/" + label, RawQuery: q.Encode()}
	return u.String(), nil
}

func setupKey(raw string) (string, error) {
	key := strings.Map(func(r rune) rune {
		switch r {
		case ' ', '-', '\t', '\n', '\r':
			return -1
		}
		return r
	}, raw)
	key = strings.TrimRight(strings.ToUpper(key), "=")
	if key == "" {
		return "", errors.New("vault: the verification code setup key is empty")
	}
	if _, err := base32.StdEncoding.WithPadding(base32.NoPadding).DecodeString(key); err != nil {
		return "", errors.New("vault: the verification code setup key is not valid Base32")
	}
	return key, nil
}
