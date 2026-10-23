//go:build windows

package vault

import (
	"os"
	"path/filepath"
	"unsafe"

	"golang.org/x/sys/windows"
)

func dpapiPath(base string) string {
	return filepath.Join(base, "device.dpapi")
}

func blobOf(b []byte) *windows.DataBlob {
	if len(b) == 0 {
		return &windows.DataBlob{}
	}
	return &windows.DataBlob{Size: uint32(len(b)), Data: &b[0]}
}

func blobBytes(b windows.DataBlob) []byte {
	defer windows.LocalFree(windows.Handle(unsafe.Pointer(b.Data)))
	return append([]byte(nil), unsafe.Slice(b.Data, b.Size)...)
}

func systemSecret(base string, create bool) ([]byte, error) {
	if sealed, err := os.ReadFile(dpapiPath(base)); err == nil {
		var out windows.DataBlob
		if err := windows.CryptUnprotectData(blobOf(sealed), nil, nil, 0, nil, windows.CRYPTPROTECT_UI_FORBIDDEN, &out); err != nil {
			return nil, err
		}
		return blobBytes(out), nil
	}
	if !create {
		return nil, errNoSecret
	}
	secret, err := newSecret()
	if err != nil {
		return nil, err
	}
	var out windows.DataBlob
	if err := windows.CryptProtectData(blobOf(secret), nil, nil, 0, nil, windows.CRYPTPROTECT_UI_FORBIDDEN, &out); err != nil {
		return nil, err
	}
	if err := writeFileAtomic(dpapiPath(base), blobBytes(out), 0o600); err != nil {
		return nil, err
	}
	return secret, nil
}

func forgetSystemSecret(base string) {
	_ = os.Remove(dpapiPath(base))
}
