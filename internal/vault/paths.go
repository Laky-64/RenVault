package vault

import (
	"path/filepath"

	"github.com/adrg/xdg"
	"github.com/wailsapp/wails/v3/pkg/application"
)

func storageBase() (string, error) {
	if p := application.Mobile.StoragePath(); p != "" {
		return p, nil
	}
	return filepath.Join(xdg.DataHome, "RenVault"), nil
}

func devicePath(base string) string {
	return filepath.Join(base, "device.json")
}

func vaultPath(base string) string {
	return filepath.Join(base, "vault.enc")
}
