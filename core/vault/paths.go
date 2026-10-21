package vault

import (
	"path/filepath"

	"github.com/adrg/xdg"
)

func DefaultBase() string {
	return filepath.Join(xdg.DataHome, "RenVault")
}

func devicePath(base string) string {
	return filepath.Join(base, "device.json")
}

func vaultPath(base string) string {
	return filepath.Join(base, "vault.enc")
}
