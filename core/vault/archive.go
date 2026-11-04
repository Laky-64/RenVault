package vault

import (
	"archive/zip"
	"bytes"
	"cmp"
	"encoding/json"
	"errors"
	"maps"
	"slices"
	"strings"
	"time"
)

const archiveEntryLimit = 64 << 20

var (
	errArchiveFormat   = errors.New("vault: this archive is not from a supported app")
	errArchivePassword = errors.New("vault: wrong archive password")
)

type archiveSource struct {
	source string
	entry  string
	pgp    bool
	parse  func([]byte) (parsedImport, error)
}

var archiveSources = []archiveSource{
	{"keyguard", "vault.json", false, parseBitwardenJSON},
	{"protonpass", "Proton Pass/data.json", false, parseProtonJSON},
	{"protonpass", "Proton Pass/data.pgp", true, parseProtonJSON},
}

type protonExport struct {
	Encrypted bool                   `json:"encrypted"`
	Vaults    map[string]protonVault `json:"vaults"`
}

type protonVault struct {
	Items []protonItem `json:"items"`
}

type protonItem struct {
	State      int        `json:"state"`
	ModifyTime int64      `json:"modifyTime"`
	Data       protonData `json:"data"`
}

type protonData struct {
	Type     string `json:"type"`
	Metadata struct {
		Name string `json:"name"`
		Note string `json:"note"`
	} `json:"metadata"`
	Content struct {
		Username     string   `json:"username"`
		ItemUsername string   `json:"itemUsername"`
		ItemEmail    string   `json:"itemEmail"`
		Password     string   `json:"password"`
		URLs         []string `json:"urls"`
		TotpURI      string   `json:"totpUri"`
	} `json:"content"`
	PlatformSpecific struct {
		Android struct {
			AllowedApps []struct {
				PackageName string `json:"packageName"`
			} `json:"allowedApps"`
		} `json:"android"`
	} `json:"platformSpecific"`
}

const (
	protonActiveState = 1
	protonSource      = "protonpass"
	pgpArmorHeader    = "-----BEGIN PGP MESSAGE-----"
)

type bitwardenExport struct {
	Encrypted bool            `json:"encrypted"`
	Items     []bitwardenItem `json:"items"`
}

type bitwardenItem struct {
	Type         int             `json:"type"`
	Name         string          `json:"name"`
	Notes        string          `json:"notes"`
	RevisionDate string          `json:"revisionDate"`
	DeletedDate  string          `json:"deletedDate"`
	Login        *bitwardenLogin `json:"login"`
}

type bitwardenLogin struct {
	Username string             `json:"username"`
	Password string             `json:"password"`
	Totp     string             `json:"totp"`
	Uris     []bitwardenURI     `json:"uris"`
	Fido2    []bitwardenPasskey `json:"fido2Credentials"`
}

type bitwardenPasskey struct {
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
	CreationDate    string `json:"creationDate"`
}

type bitwardenURI struct {
	URI string `json:"uri"`
}

const bitwardenLoginType = 1

func InspectArchive(raw []byte, password string) (ImportPreview, error) {
	parsed, locked, err := loadArchive(raw, password)
	if err != nil {
		return ImportPreview{}, err
	}
	if locked {
		return ImportPreview{Source: parsed.source, Locked: true}, nil
	}
	return previewOf(parsed), nil
}

func (v *Vault) ImportArchive(raw []byte, password string) (ImportResult, error) {
	parsed, locked, err := loadArchive(raw, password)
	if err != nil {
		return ImportResult{}, err
	}
	if locked {
		return ImportResult{}, errArchivePassword
	}
	defer zeroPasskeys(parsed.passkeys)
	result, err := v.importWeb(parsed.entries)
	result.Skipped += parsed.skipped
	if err != nil || len(parsed.passkeys) == 0 {
		return result, err
	}
	keys, err := v.importPasskeys(parsed.passkeys)
	result.Added += keys.Added
	result.Updated += keys.Updated
	result.Skipped += keys.Skipped
	return result, err
}

func loadArchive(raw []byte, password string) (parsedImport, bool, error) {
	if bytes.HasPrefix(bytes.TrimSpace(raw), []byte(pgpArmorHeader)) {
		if password == "" {
			return parsedImport{source: protonSource}, true, nil
		}
		plain, err := openPGPMessage(raw, password, archiveEntryLimit)
		if err != nil {
			return parsedImport{}, false, err
		}
		defer zero(plain)
		if bytes.HasPrefix(bytes.TrimSpace(plain), []byte("{")) {
			parsed, err := parseProtonJSON(plain)
			parsed.source = protonSource
			return parsed, false, err
		}
		parsed, locked, err := loadArchive(plain, "")
		if locked {
			return parsedImport{}, false, errArchiveFormat
		}
		return parsed, false, err
	}
	source, file, err := findArchiveSource(raw)
	if err != nil {
		return parsedImport{}, false, err
	}
	if (source.pgp || isEncrypted(file)) && password == "" {
		return parsedImport{source: source.source}, true, nil
	}
	parsed, err := readArchive(source, file, password)
	return parsed, false, err
}

func findArchiveSource(raw []byte) (archiveSource, *zip.File, error) {
	reader, err := zip.NewReader(bytes.NewReader(raw), int64(len(raw)))
	if err != nil {
		return archiveSource{}, nil, errArchiveFormat
	}
	for _, source := range archiveSources {
		index := slices.IndexFunc(reader.File, func(f *zip.File) bool { return f.Name == source.entry })
		if index >= 0 {
			return source, reader.File[index], nil
		}
	}
	return archiveSource{}, nil, errArchiveFormat
}

func readArchive(source archiveSource, file *zip.File, password string) (parsedImport, error) {
	data, err := readZipEntry(file, password, archiveEntryLimit)
	if err != nil {
		return parsedImport{}, err
	}
	if source.pgp {
		sealed := data
		data, err = openPGPMessage(sealed, password, archiveEntryLimit)
		zero(sealed)
		if err != nil {
			return parsedImport{}, err
		}
	}
	defer zero(data)
	parsed, err := source.parse(data)
	if err != nil {
		return parsedImport{}, err
	}
	parsed.source = source.source
	return parsed, nil
}

func parseBitwardenJSON(data []byte) (parsedImport, error) {
	var export bitwardenExport
	if err := json.Unmarshal(data, &export); err != nil || export.Encrypted {
		return parsedImport{}, errArchiveFormat
	}
	var parsed parsedImport
	for _, item := range export.Items {
		if item.Type != bitwardenLoginType || item.Login == nil || item.DeletedDate != "" {
			parsed.skipped++
			continue
		}
		links := make([]string, 0, len(item.Login.Uris))
		for _, uri := range item.Login.Uris {
			links = append(links, strings.TrimSpace(uri.URI))
		}
		modified, _ := time.Parse(time.RFC3339Nano, item.RevisionDate)
		for _, credential := range item.Login.Fido2 {
			passkey, ok := bitwardenPasskeyEntry(credential, strings.TrimSpace(item.Name), item.Notes)
			if !ok {
				parsed.skipped++
				continue
			}
			parsed.passkeys = append(parsed.passkeys, passkey)
		}
		if item.Login.Password == "" && len(item.Login.Fido2) > 0 {
			continue
		}
		entry, ok := webEntry(
			strings.TrimSpace(item.Name),
			links,
			strings.TrimSpace(item.Login.Username),
			item.Login.Password,
			item.Notes,
			strings.TrimSpace(item.Login.Totp),
			modified.UTC(),
		)
		if !ok {
			parsed.skipped++
			continue
		}
		parsed.entries = append(parsed.entries, entry)
	}
	return parsed, nil
}

func parseProtonJSON(data []byte) (parsedImport, error) {
	var export protonExport
	if err := json.Unmarshal(data, &export); err != nil || export.Encrypted || export.Vaults == nil {
		return parsedImport{}, errArchiveFormat
	}
	var parsed parsedImport
	for _, id := range slices.Sorted(maps.Keys(export.Vaults)) {
		for _, item := range export.Vaults[id].Items {
			if item.Data.Type != "login" || item.State != protonActiveState {
				parsed.skipped++
				continue
			}
			content := item.Data.Content
			username := cmp.Or(content.ItemUsername, content.Username, content.ItemEmail)
			modified := time.Time{}
			if item.ModifyTime > 0 {
				modified = time.Unix(item.ModifyTime, 0).UTC()
			}
			title := strings.TrimSpace(item.Data.Metadata.Name)
			links := slices.Clone(content.URLs)
			for _, app := range item.Data.PlatformSpecific.Android.AllowedApps {
				if pkg := strings.TrimSpace(app.PackageName); pkg != "" {
					links = append(links, AppLink(pkg))
				}
			}
			entry, ok := webEntry(
				title,
				links,
				strings.TrimSpace(username),
				content.Password,
				item.Data.Metadata.Note,
				strings.TrimSpace(content.TotpURI),
				modified,
			)
			if !ok {
				parsed.skipped++
				continue
			}
			parsed.entries = append(parsed.entries, entry)
		}
	}
	return parsed, nil
}
