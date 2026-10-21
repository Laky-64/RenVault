package service

import (
	"time"

	"github.com/Laky-64/appleservices/keychain"
	"github.com/Laky-64/renvault-core/apple"
	"github.com/Laky-64/renvault-core/vault"
)

type initRequest struct {
	Base string `cbor:"base"`
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
	URL         string `cbor:"url"`
	DefaultList string `cbor:"default_list"`
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
	ID           string `cbor:"id"`
	Title        string `cbor:"title"`
	RelyingParty string `cbor:"relying_party"`
	UserName     string `cbor:"username"`
	DisplayName  string `cbor:"display_name"`
	CredentialID string `cbor:"credential_id"`
	IsDeleted    bool   `cbor:"is_deleted"`
	Created      int64  `cbor:"created"`
	Modified     int64  `cbor:"modified"`
	Deleted      int64  `cbor:"deleted"`
}

type bottleEntry struct {
	Index      int    `cbor:"index"`
	Name       string `cbor:"name"`
	Model      string `cbor:"model"`
	ShortModel string `cbor:"short_model"`
	Class      string `cbor:"class"`
	OS         string `cbor:"os"`
	OSVersion  string `cbor:"os_version"`
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
			RelyingParty: item.RelyingParty,
			UserName:     item.UserName,
			DisplayName:  item.DisplayName,
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
			Class:      item.Class,
			OS:         item.OS,
			OSVersion:  item.OSVersion,
			ImageURL:   item.ImageURL,
		})
	}
	return converted
}
