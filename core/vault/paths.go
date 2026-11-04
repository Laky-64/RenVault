package vault

import (
	"github.com/Laky-64/renvault-core/app"
	"path/filepath"

	"github.com/adrg/xdg"
)

func DefaultBase() string {
	return filepath.Join(xdg.DataHome, app.Name)
}

func devicePath(base string) string {
	return filepath.Join(base, "device.json")
}

func vaultPath(base string) string {
	return filepath.Join(base, "vault.enc")
}
