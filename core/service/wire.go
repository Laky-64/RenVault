package service

import (
	"strings"
	"time"

	"github.com/Laky-64/appleservices/keychain"
	"github.com/Laky-64/renvault-core/apple"
	"github.com/Laky-64/renvault-core/portal"
	"github.com/Laky-64/renvault-core/tray"
	"github.com/Laky-64/renvault-core/vault"
)

type initRequest struct {
	Base   string `cbor:"base"`
	Secret []byte `cbor:"secret"`
}

type secretRequest struct {
	Secret string `cbor:"secret"`
}

type identifierRequest struct {
	ID string `cbor:"id"`
}

type identifiersRequest struct {
	IDs []string `cbor:"ids"`
}

type domainRequest struct {
	Domain string `cbor:"domain"`
}

type sortRequest struct {
	Field     string `cbor:"field"`
	Ascending bool   `cbor:"ascending"`
}

type nameSwap struct {
	From string `cbor:"from"`
	To   string `cbor:"to"`
}

type namesPayload struct {
	Names []nameSwap `cbor:"names"`
}

type autoLockRequest struct {
	Minutes int `cbor:"minutes"`
}

type startLoginRequest struct {
	AppleID  string `cbor:"apple_id"`
	Password string `cbor:"password"`
}

type urlRequest struct {
	URL string `cbor:"url"`
}

type anisetteRequest struct {
	URL string `cbor:"url"`
}

type anisetteResponse struct {
	URL string `cbor:"url"`
}

type submitCodeRequest struct {
	Code string `cbor:"code"`
}

type recoverRequest struct {
	Index    int    `cbor:"index"`
	Passcode string `cbor:"passcode"`
}

type addPasswordRequest struct {
	Title    string `cbor:"title"`
	Website  string `cbor:"website"`
	Username string `cbor:"username"`
	Password string `cbor:"password"`
	Note     string `cbor:"note"`
}

type editPasskeyRequest struct {
	ID       string   `cbor:"id"`
	Title    string   `cbor:"title"`
	Username string   `cbor:"username"`
	Note     string   `cbor:"note"`
	Sites    []string `cbor:"sites"`
}

type editPasswordRequest struct {
	ID       string   `cbor:"id"`
	Title    string   `cbor:"title"`
	Website  string   `cbor:"website"`
	Username string   `cbor:"username"`
	Password string   `cbor:"password"`
	Note     string   `cbor:"note"`
	TOTPURL  string   `cbor:"totp_url"`
	Domains  []string `cbor:"domains"`
	DropTOTP bool     `cbor:"drop_totp"`
}

type stateResponse struct {
	Configured bool `cbor:"configured"`
	Unlocked   bool `cbor:"unlocked"`
}

type startLoginResponse struct {
	NeedsCode bool `cbor:"needs_code"`
}

type identifierResponse struct {
	ID string `cbor:"id"`
}

type createPasskeyRequest struct {
	RelyingParty string `cbor:"relying_party"`
	Username     string `cbor:"username"`
	DisplayName  string `cbor:"display_name"`
	UserHandle   []byte `cbor:"user_handle"`
}

type createPasskeyResponse struct {
	CredentialID      []byte `cbor:"credential_id"`
	AuthenticatorData []byte `cbor:"authenticator_data"`
}

type signPasskeyRequest struct {
	ID             string `cbor:"id"`
	ClientDataHash []byte `cbor:"client_data_hash"`
}

type signPasskeyResponse struct {
	AuthenticatorData []byte `cbor:"authenticator_data"`
	Signature         []byte `cbor:"signature"`
	UserHandle        []byte `cbor:"user_handle"`
}

type secretResponse struct {
	Secret string `cbor:"secret"`
}

type changedResponse struct {
	Changed bool `cbor:"changed"`
}

type minutesResponse struct {
	Minutes []int `cbor:"minutes"`
}

type bytesResponse struct {
	Data []byte `cbor:"data"`
}

type settingsResponse struct {
	SortField       string `cbor:"sort_field"`
	SortAscending   bool   `cbor:"sort_ascending"`
	AutoLockMinutes int    `cbor:"auto_lock_minutes"`
}

type profileResponse struct {
	Name     string `cbor:"name"`
	AppleID  string `cbor:"apple_id"`
	HasPhoto bool   `cbor:"has_photo"`
	Local    bool   `cbor:"local"`
}

type localSetupRequest struct {
	Secret string `cbor:"secret"`
	Name   string `cbor:"name"`
	Device string `cbor:"device"`
	Photo  []byte `cbor:"photo"`
}

type profileRequest struct {
	Name     string `cbor:"name"`
	Photo    []byte `cbor:"photo"`
	SetPhoto bool   `cbor:"set_photo"`
}

type deviceEntry struct {
	ID       string `cbor:"id"`
	Name     string `cbor:"name"`
	Self     bool   `cbor:"self"`
	SyncedAt int64  `cbor:"synced_at"`
}

type pairResponse struct {
	Code    string `cbor:"code"`
	Payload string `cbor:"payload"`
	Size    int    `cbor:"size"`
	Modules []byte `cbor:"modules"`
}

type pickRequest struct {
	Title  string `cbor:"title"`
	Filter string `cbor:"filter"`
}

type fileFilter struct {
	Name       string   `cbor:"name"`
	Extensions []string `cbor:"extensions"`
	Types      []string `cbor:"types"`
}

func (f fileFilter) portal() portal.Filter {
	out := portal.Filter{Name: f.Name, Types: f.Types}
	for _, ext := range f.Extensions {
		out.Patterns = append(out.Patterns, "*."+strings.ToLower(ext), "*."+strings.ToUpper(ext))
	}
	return out
}

type trayItem struct {
	ID    string `cbor:"id"`
	Label string `cbor:"label"`
}

type trayIcon struct {
	Png []byte `cbor:"png"`
}

type trayRequest struct {
	Tooltip      string     `cbor:"tooltip"`
	Icons        []trayIcon `cbor:"icons"`
	IconsOnLight []trayIcon `cbor:"iconsOnLight"`
	Items        []trayItem `cbor:"items"`
}

func (r trayRequest) config() tray.Config {
	config := tray.Config{Tooltip: r.Tooltip}
	for _, icon := range r.Icons {
		config.Icons = append(config.Icons, icon.Png)
	}
	for _, icon := range r.IconsOnLight {
		config.IconsOnLight = append(config.IconsOnLight, icon.Png)
	}
	for _, item := range r.Items {
		config.Items = append(config.Items, tray.Item{ID: item.ID, Label: item.Label})
	}
	return config
}

type raiseRequest struct {
	Token string `cbor:"token"`
}

type trayEvent struct {
	Item string `cbor:"item"`
}

type pickFileRequest struct {
	Title  string     `cbor:"title"`
	Filter fileFilter `cbor:"filter"`
}

type saveFileRequest struct {
	Title  string     `cbor:"title"`
	Name   string     `cbor:"name"`
	Filter fileFilter `cbor:"filter"`
	Data   []byte     `cbor:"data"`
}

type savedResponse struct {
	Saved bool `cbor:"saved"`
}

type backupRequest struct {
	Password string `cbor:"password"`
	Data     []byte `cbor:"data"`
}

type importResponse struct {
	Added   int `cbor:"added"`
	Updated int `cbor:"updated"`
	Skipped int `cbor:"skipped"`
}

func importResponseOf(r vault.ImportResult) importResponse {
	return importResponse{Added: r.Added, Updated: r.Updated, Skipped: r.Skipped}
}

type joinRequest struct {
	Text string `cbor:"text"`
}

type nameResponse struct {
	Name string `cbor:"name"`
}

type netInfoResponse struct {
	Node string `cbor:"node"`
	Port int    `cbor:"port"`
}

type addressesRequest struct {
	IPs []string `cbor:"ips"`
}

type peerSeenRequest struct {
	Node string `cbor:"node"`
	Host string `cbor:"host"`
	Port int    `cbor:"port"`
}

type syncResponse struct {
	Pending  int    `cbor:"pending"`
	SyncedAt int64  `cbor:"synced_at"`
	Error    string `cbor:"error"`
}

type pwnedResponse struct {
	Checked   int   `cbor:"checked"`
	Pwned     int   `cbor:"pwned"`
	CheckedAt int64 `cbor:"checked_at"`
}

type groupEntry struct {
	ID   string `cbor:"id"`
	Name string `cbor:"name"`
}

type webEntry struct {
	ID          string       `cbor:"id"`
	Title       string       `cbor:"title"`
	Username    string       `cbor:"username"`
	Domain      string       `cbor:"domain"`
	Domains     []string     `cbor:"domains"`
	Website     bool         `cbor:"website"`
	HasTOTP     bool         `cbor:"has_totp"`
	Note        string       `cbor:"note"`
	IsDeleted   bool         `cbor:"is_deleted"`
	Groups      []groupEntry `cbor:"groups"`
	Shared      bool         `cbor:"shared"`
	Pwned       bool         `cbor:"pwned"`
	HasPassword bool         `cbor:"has_password"`
	Created     int64        `cbor:"created"`
	Modified    int64        `cbor:"modified"`
	Deleted     int64        `cbor:"deleted"`
}

type wifiEntry struct {
	ID       string `cbor:"id"`
	SSID     string `cbor:"ssid"`
	Created  int64  `cbor:"created"`
	Modified int64  `cbor:"modified"`
}

type passkeyEntry struct {
	ID           string   `cbor:"id"`
	Title        string   `cbor:"title"`
	CustomTitle  string   `cbor:"custom_title"`
	RelyingParty string   `cbor:"relying_party"`
	UserName     string   `cbor:"username"`
	DisplayName  string   `cbor:"display_name"`
	Note         string   `cbor:"note"`
	Sites        []string `cbor:"sites"`
	CredentialID string   `cbor:"credential_id"`
	IsDeleted    bool     `cbor:"is_deleted"`
	Created      int64    `cbor:"created"`
	Modified     int64    `cbor:"modified"`
	Deleted      int64    `cbor:"deleted"`
}

type bottleEntry struct {
	Index      int    `cbor:"index"`
	Name       string `cbor:"name"`
	Model      string `cbor:"model"`
	ShortModel string `cbor:"short_model"`
	OS         string `cbor:"os"`
	ImageURL   string `cbor:"image_url"`
}

func millis(at time.Time) int64 {
	if at.IsZero() {
		return 0
	}
	return at.UnixMilli()
}

func groupsOf(groups []keychain.Group) []groupEntry {
	converted := make([]groupEntry, 0, len(groups))
	for _, group := range groups {
		converted = append(converted, groupEntry{ID: group.ID, Name: group.Name})
	}
	return converted
}

func webEntriesOf(items []vault.WebMeta) []webEntry {
	converted := make([]webEntry, 0, len(items))
	for _, item := range items {
		converted = append(converted, webEntry{
			ID:          item.ID,
			Title:       item.Title,
			Username:    item.Username,
			Domain:      item.Domain,
			Domains:     item.Domains,
			Website:     item.Website,
			HasTOTP:     item.HasTOTP,
			Note:        item.Note,
			IsDeleted:   item.IsDeleted,
			Groups:      groupsOf(item.Groups),
			Shared:      item.Shared,
			Pwned:       item.Pwned,
			HasPassword: item.HasPassword,
			Created:     millis(item.Created),
			Modified:    millis(item.Modified),
			Deleted:     millis(item.Deleted),
		})
	}
	return converted
}

func wifiEntriesOf(items []vault.WiFiMeta) []wifiEntry {
	converted := make([]wifiEntry, 0, len(items))
	for _, item := range items {
		converted = append(converted, wifiEntry{
			ID:       item.ID,
			SSID:     item.SSID,
			Created:  millis(item.Created),
			Modified: millis(item.Modified),
		})
	}
	return converted
}

func passkeyEntriesOf(items []vault.PasskeyMeta) []passkeyEntry {
	converted := make([]passkeyEntry, 0, len(items))
	for _, item := range items {
		converted = append(converted, passkeyEntry{
			ID:           item.ID,
			Title:        item.Title,
			CustomTitle:  item.CustomTitle,
			RelyingParty: item.RelyingParty,
			UserName:     item.UserName,
			DisplayName:  item.DisplayName,
			Note:         item.Note,
			Sites:        item.Sites,
			CredentialID: item.CredentialID,
			IsDeleted:    item.IsDeleted,
			Created:      millis(item.Created),
			Modified:     millis(item.Modified),
			Deleted:      millis(item.Deleted),
		})
	}
	return converted
}

func bottleEntriesOf(items []apple.BottleInfo) []bottleEntry {
	converted := make([]bottleEntry, 0, len(items))
	for _, item := range items {
		converted = append(converted, bottleEntry{
			Index:      item.Index,
			Name:       item.Name,
			Model:      item.Model,
			ShortModel: item.ShortModel,
			OS:         item.OS,
			ImageURL:   item.ImageURL,
		})
	}
	return converted
}

type quickRequest struct {
	Method     string `cbor:"method"`
	Key        []byte `cbor:"key"`
	Sealed     []byte `cbor:"sealed"`
	Credential bool   `cbor:"credential"`
}

type quickStateResponse struct {
	Methods []string `cbor:"methods"`
	Method  string   `cbor:"method"`
	Sealed  []byte   `cbor:"sealed"`
	Ready   bool     `cbor:"ready"`
	Expired bool     `cbor:"expired"`
}

type anisetteEntry struct {
	Name      string `cbor:"name"`
	Address   string `cbor:"address"`
	Kind      int    `cbor:"kind"`
	LatencyMs int64  `cbor:"latency_ms"`
	Reachable bool   `cbor:"reachable"`
}

type secureRequest struct {
	On bool `cbor:"on"`
}

type titleBarRequest struct {
	Dark    bool   `cbor:"dark"`
	Caption uint32 `cbor:"caption"`
	Text    uint32 `cbor:"text"`
}
