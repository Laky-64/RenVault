package vault

import (
	"crypto/sha256"
	"encoding/hex"
	"strconv"
	"strings"

	"github.com/Laky-64/appleservices/keychain"
)

type Kind string

const (
	KindWeb  Kind = "web"
	KindWiFi Kind = "wifi"
)

type WebMeta struct {
	ID       string   `json:"id"`
	Title    string   `json:"title"`
	Username string   `json:"username"`
	Domain   string   `json:"domain"`
	Domains  []string `json:"domains"`
	HasTOTP  bool     `json:"hasTotp"`
}

type WiFiMeta struct {
	ID   string `json:"id"`
	SSID string `json:"ssid"`
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

func webMetas(p payload) []WebMeta {
	out := make([]WebMeta, 0, len(p.Web))
	for _, w := range p.Web {
		out = append(out, WebMeta{
			ID:       webID(w),
			Title:    w.Name,
			Username: w.Username,
			Domain:   w.Domain,
			Domains:  w.Domains,
			HasTOTP:  w.TOTP != "",
		})
	}
	return out
}

func wifiMetas(p payload) []WiFiMeta {
	out := make([]WiFiMeta, 0, len(p.WiFi))
	for _, w := range p.WiFi {
		out = append(out, WiFiMeta{ID: wifiID(w), SSID: w.SSID})
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
