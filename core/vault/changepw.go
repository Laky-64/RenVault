package vault

import (
	"strings"

	"github.com/Laky-64/renvault-core/quirks"
)

func knownChangeURL(host string) string {
	return quirks.ChangeURL(host)
}

func ChangePasswordURL(domain string) string {
	return changePasswordURL(domain)
}

func changePasswordURL(domain string) string {
	host := strings.ToLower(strings.TrimSpace(domain))
	host = strings.TrimSuffix(host, ".")
	if host == "" || strings.ContainsAny(host, " /?#\\") {
		return ""
	}
	if known := knownChangeURL(host); known != "" {
		return known
	}
	return "https://" + host + "/"
}
