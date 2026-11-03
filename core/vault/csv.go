package vault

import (
	"bytes"
	"cmp"
	"encoding/csv"
	"encoding/json"
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

type ImportPreview struct {
	Source   string
	Entries  int
	Codes    int
	Passkeys int
	Skipped  int
	Locked   bool
}

type csvSignature struct {
	source  string
	columns []string
	only    []string
}

var csvSignatures = []csvSignature{
	{"renvault", []string{"name", "url", "username", "password", "note", "otpauth"}, nil},
	{"bitwarden", []string{"folder", "login_uri"}, nil},
	{"protonpass", []string{"vault", "createtime"}, nil},
	{"1password", []string{"otpauth", "archived"}, nil},
	{"firefox", []string{"httprealm", "formactionorigin"}, nil},
	{"lastpass", []string{"grouping", "extra"}, nil},
	{"dashlane", []string{"username2", "category"}, nil},
	{"keepassxc", []string{"group", "totp"}, nil},
	{"nordpass", []string{"cardholdername", "additional_urls"}, nil},
	{"apple", []string{"title", "otpauth"}, nil},
	{"chrome", []string{"name", "url", "username", "password"}, []string{"name", "url", "username", "password", "note"}},
}

var csvExportHeader = []string{"name", "url", "username", "password", "note", "otpauth"}

func (v *Vault) ImportCSV(raw []byte) (ImportResult, error) {
	parsed, err := parseCSV(raw)
	if err != nil {
		return ImportResult{}, err
	}
	result, err := v.importWeb(parsed.entries)
	result.Skipped += parsed.skipped
	return result, err
}

func InspectCSV(raw []byte) (ImportPreview, error) {
	parsed, err := parseCSV(raw)
	if err != nil {
		return ImportPreview{}, err
	}
	return previewOf(parsed), nil
}

func (v *Vault) ExportCSV() ([]byte, error) {
	v.mu.Lock()
	if v.p == nil || v.k == nil {
		v.mu.Unlock()
		return nil, errLocked
	}
	v.resetTimerLocked()
	web := slices.DeleteFunc(slices.Clone(v.p.Web), func(w keychain.WebPassword) bool { return w.IsDeleted })
	v.mu.Unlock()

	slices.SortStableFunc(web, func(a, b keychain.WebPassword) int {
		return strings.Compare(strings.ToLower(csvName(a)), strings.ToLower(csvName(b)))
	})
	var out bytes.Buffer
	w := csv.NewWriter(&out)
	if err := w.Write(csvExportHeader); err != nil {
		return nil, err
	}
	for _, e := range web {
		var links []string
		if e.Website {
			for _, domain := range append([]string{e.Domain}, e.Domains...) {
				if link := "https://" + domain; domain != "" && !slices.Contains(links, link) {
					links = append(links, link)
				}
			}
		}
		row := []string{csvName(e), strings.Join(links, ","), e.Username, e.Password, e.Note, e.TOTP}
		if err := w.Write(row); err != nil {
			return nil, err
		}
	}
	w.Flush()
	return out.Bytes(), w.Error()
}

func csvName(e keychain.WebPassword) string {
	if e.Website {
		return cmp.Or(e.Name, e.Domain)
	}
	return cmp.Or(e.Name, e.Username)
}

type parsedImport struct {
	source   string
	entries  []keychain.WebPassword
	passkeys []passkeyEntry
	skipped  int
}

func previewOf(parsed parsedImport) ImportPreview {
	preview := ImportPreview{Source: parsed.source, Entries: len(parsed.entries), Passkeys: len(parsed.passkeys), Skipped: parsed.skipped}
	for _, e := range parsed.entries {
		if e.TOTP != "" {
			preview.Codes++
		}
	}
	return preview
}

func csvSourceOf(names []string) string {
	for _, signature := range csvSignatures {
		if slices.ContainsFunc(signature.columns, func(column string) bool { return !slices.Contains(names, column) }) {
			continue
		}
		if signature.only != nil && slices.ContainsFunc(names, func(name string) bool { return !slices.Contains(signature.only, name) }) {
			continue
		}
		return signature.source
	}
	return ""
}

func parseCSV(raw []byte) (parsedImport, error) {
	raw = bytes.TrimPrefix(raw, []byte("\xef\xbb\xbf"))
	r := csv.NewReader(bytes.NewReader(raw))
	r.FieldsPerRecord = -1
	r.LazyQuotes = true
	head, err := r.Read()
	if err != nil {
		return parsedImport{}, errCSVFormat
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
		return parsedImport{}, errCSVFormat
	}
	cell := func(row []string, field string) string {
		i, ok := at[field]
		if !ok || i >= len(row) {
			return ""
		}
		return strings.TrimSpace(row[i])
	}

	parsed := parsedImport{source: csvSourceOf(names)}
	for {
		row, err := r.Read()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return parsedImport{}, errCSVFormat
		}
		if !slices.Contains(csvLoginTypes, strings.ToLower(cell(row, "type"))) {
			parsed.skipped++
			continue
		}
		links := append(splitLinks(cell(row, "url")), moreLinks(cell(row, "more"))...)
		if len(links) == 1 && strings.EqualFold(strings.TrimRight(strings.TrimPrefix(strings.TrimPrefix(links[0], "http://"), "https://"), "/"), lastPassNote) {
			parsed.skipped++
			continue
		}
		username := cell(row, "username")
		if username == "" {
			username = cell(row, "email")
		}
		modified := time.Time{}
		if ms, err := strconv.ParseInt(cell(row, "changed"), 10, 64); err == nil && ms > 0 {
			modified = time.UnixMilli(ms).UTC()
		} else if when, ok := parseWhen(cell(row, "modified")); ok {
			modified = when
		}
		entry, ok := webEntry(cell(row, "title"), links, username, cell(row, "password"), cell(row, "note"), cell(row, "totp"), modified)
		if !ok {
			parsed.skipped++
			continue
		}
		parsed.entries = append(parsed.entries, entry)
	}
	return parsed, nil
}

func webEntry(title string, links []string, username, password, note, totp string, modified time.Time) (keychain.WebPassword, bool) {
	if password == "" {
		return keychain.WebPassword{}, false
	}
	var domains []string
	for _, link := range links {
		if host := siteOf(link); host != "" && !slices.Contains(domains, host) {
			domains = append(domains, host)
		}
	}
	domain := ""
	if len(domains) > 0 {
		domain = domains[0]
	}
	if domain == "" && title == "" {
		return keychain.WebPassword{}, false
	}
	return keychain.WebPassword{
		Name:     title,
		Domain:   domain,
		Domains:  domains,
		Website:  domain != "",
		Username: username,
		Password: password,
		Note:     note,
		TOTP:     totp,
		Modified: modified,
	}, true
}

func moreLinks(raw string) []string {
	if !strings.HasPrefix(raw, "[") {
		return splitLinks(raw)
	}
	var list []struct {
		URL string `json:"url"`
	}
	if err := json.Unmarshal([]byte(raw), &list); err != nil {
		var plain []string
		if json.Unmarshal([]byte(raw), &plain) == nil {
			return plain
		}
		return nil
	}
	links := make([]string, 0, len(list))
	for _, item := range list {
		links = append(links, item.URL)
	}
	return links
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
