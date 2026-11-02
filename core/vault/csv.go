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
	"url":      {"url", "login_uri", "website", "uri", "login_url", "urls"},
	"more":     {"additional_urls", "autofillurls"},
	"username": {"username", "login_username", "user", "login"},
	"email":    {"email"},
	"password": {"password", "login_password"},
	"note":     {"notes", "note", "extra", "comments"},
	"totp":     {"otpauth", "totp", "login_totp", "otp"},
	"type":     {"type"},
	"changed":  {"timepasswordchanged"},
	"modified": {"modifytime", "modified", "lastmodified", "last_modified"},
}

var csvLoginTypes = []string{"", "login", "password", "1"}

const lastPassNote = "sn"

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
	names := make([]string, len(head))
	for i, h := range head {
		names[i] = strings.ToLower(strings.TrimSpace(h))
	}
	at := map[string]int{}
	for field, aliases := range csvColumns {
		for _, alias := range aliases {
			if i := slices.Index(names, alias); i >= 0 {
				at[field] = i
				break
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
		if !slices.Contains(csvLoginTypes, strings.ToLower(cell(row, "type"))) {
			continue
		}
		links := splitLinks(cell(row, "url") + "\n" + cell(row, "more"))
		if len(links) == 1 && strings.EqualFold(strings.TrimRight(strings.TrimPrefix(strings.TrimPrefix(links[0], "http://"), "https://"), "/"), lastPassNote) {
			continue
		}
		var domains []string
		for _, link := range links {
			if host := siteOf(link); host != "" && !slices.Contains(domains, host) {
				domains = append(domains, host)
			}
		}
		password := cell(row, "password")
		username := cell(row, "username")
		if username == "" {
			username = cell(row, "email")
		}
		title := cell(row, "title")
		if password == "" {
			continue
		}
		domain := ""
		if len(domains) > 0 {
			domain = domains[0]
		}
		if domain == "" && title == "" {
			continue
		}
		w := keychain.WebPassword{
			Name:     title,
			Domain:   domain,
			Domains:  domains,
			Website:  domain != "",
			Username: username,
			Password: password,
			Note:     cell(row, "note"),
			TOTP:     cell(row, "totp"),
		}
		if ms, err := strconv.ParseInt(cell(row, "changed"), 10, 64); err == nil && ms > 0 {
			w.Modified = time.UnixMilli(ms).UTC()
		} else if when, ok := parseWhen(cell(row, "modified")); ok {
			w.Modified = when
		}
		out = append(out, w)
	}
	return out, nil
}

func splitLinks(raw string) []string {
	return strings.FieldsFunc(raw, func(r rune) bool {
		return strings.ContainsRune(",;| \t\r\n[]\"'", r)
	})
}

func parseWhen(raw string) (time.Time, bool) {
	if raw == "" {
		return time.Time{}, false
	}
	if n, err := strconv.ParseInt(raw, 10, 64); err == nil && n > 0 {
		if n > 1e12 {
			return time.UnixMilli(n).UTC(), true
		}
		return time.Unix(n, 0).UTC(), true
	}
	for _, layout := range []string{time.RFC3339Nano, time.RFC3339, "2006-01-02 15:04:05", "2006-01-02"} {
		if t, err := time.Parse(layout, raw); err == nil {
			return t.UTC(), true
		}
	}
	return time.Time{}, false
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
	host := strings.ToLower(u.Hostname())
	if port := u.Port(); port != "" && port != "80" && port != "443" {
		host += ":" + port
	}
	if !keychain.IsWebsiteDomain(strings.TrimPrefix(host, "www.")) {
		return ""
	}
	return host
}
