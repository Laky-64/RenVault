package vault

import (
	"bytes"
	"encoding/csv"
	"errors"
	"io"
	"net/url"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/Laky-64/appleservices/keychain"
)

var errCSVFormat = errors.New("vault: this CSV has no password column")

var csvColumns = map[string][]string{
	"title":    {"title", "name"},
	"url":      {"url", "website", "login_uri", "uri", "login_url"},
	"username": {"username", "login_username", "user", "login", "email"},
	"password": {"password", "login_password"},
	"note":     {"notes", "note", "extra", "comments"},
	"totp":     {"otpauth", "totp", "login_totp", "otp"},
	"type":     {"type"},
	"changed":  {"timepasswordchanged"},
}

func (v *Vault) ImportCSV(raw []byte) (ImportResult, error) {
	entries, err := parseCSV(raw)
	if err != nil {
		return ImportResult{}, err
	}
	return v.importWeb(entries)
}

func parseCSV(raw []byte) ([]keychain.WebPassword, error) {
	raw = bytes.TrimPrefix(raw, []byte("\xef\xbb\xbf"))
	r := csv.NewReader(bytes.NewReader(raw))
	r.FieldsPerRecord = -1
	r.LazyQuotes = true
	head, err := r.Read()
	if err != nil {
		return nil, errCSVFormat
	}
	at := map[string]int{}
	for i, h := range head {
		h = strings.ToLower(strings.TrimSpace(h))
		for field, names := range csvColumns {
			if _, ok := at[field]; !ok && slices.Contains(names, h) {
				at[field] = i
			}
		}
	}
	if _, ok := at["password"]; !ok {
		return nil, errCSVFormat
	}
	cell := func(row []string, field string) string {
		i, ok := at[field]
		if !ok || i >= len(row) {
			return ""
		}
		return strings.TrimSpace(row[i])
	}

	var out []keychain.WebPassword
	for {
		row, err := r.Read()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return nil, errCSVFormat
		}
		if kind := cell(row, "type"); kind != "" && kind != "login" {
			continue
		}
		password := cell(row, "password")
		title := cell(row, "title")
		domain := siteOf(cell(row, "url"))
		if password == "" || (domain == "" && title == "") {
			continue
		}
		w := keychain.WebPassword{
			Name:     title,
			Domain:   domain,
			Website:  domain != "",
			Username: cell(row, "username"),
			Password: password,
			Note:     cell(row, "note"),
			TOTP:     cell(row, "totp"),
		}
		if ms, err := strconv.ParseInt(cell(row, "changed"), 10, 64); err == nil && ms > 0 {
			w.Modified = time.UnixMilli(ms).UTC()
		}
		out = append(out, w)
	}
	return out, nil
}

func siteOf(raw string) string {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return ""
	}
	if !strings.Contains(raw, "://") {
		raw = "https://" + raw
	}
	u, err := url.Parse(raw)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") {
		return ""
	}
	host := strings.TrimPrefix(strings.ToLower(u.Hostname()), "www.")
	if !keychain.IsWebsiteDomain(host) {
		return ""
	}
	return host
}
