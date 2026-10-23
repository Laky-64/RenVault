package vault

import (
	"crypto/rand"
	"errors"
	"os"
	"path/filepath"
	"slices"
	"sync"
)

const (
	sealerPlatform = "platform"
	sealerSystem   = "system"
	sealerFile     = "file"
	secretSize     = 32
)

var (
	errNoSecret    = errors.New("vault: no device secret available")
	errUnsupported = errors.New("vault: not supported on this platform")

	platformMu     sync.Mutex
	platformSecret []byte
)

func SetPlatformSecret(secret []byte) {
	platformMu.Lock()
	defer platformMu.Unlock()
	if len(secret) == secretSize {
		platformSecret = slices.Clone(secret)
	}
}

func platformKey() []byte {
	platformMu.Lock()
	defer platformMu.Unlock()
	return slices.Clone(platformSecret)
}

func deviceSecret(base, sealer string, create bool) ([]byte, string, error) {
	order := []string{sealer}
	if create {
		order = []string{sealerPlatform, sealerSystem, sealerFile}
	}
	for _, kind := range order {
		var (
			secret []byte
			err    error
		)
		switch kind {
		case sealerPlatform:
			secret = platformKey()
			if len(secret) != secretSize {
				err = errNoSecret
			}
		case sealerSystem:
			secret, err = systemSecret(base, create)
		case sealerFile:
			secret, err = fileSecret(base, create)
		default:
			err = errNoSecret
		}
		if err == nil && len(secret) == secretSize {
			return secret, kind, nil
		}
	}
	return nil, "", errNoSecret
}

func forgetDeviceSecret(base string) {
	_ = os.Remove(secretFilePath(base))
	forgetSystemSecret(base)
}

func secretFilePath(base string) string {
	return filepath.Join(base, "device.secret")
}

func newSecret() ([]byte, error) {
	secret := make([]byte, secretSize)
	_, err := rand.Read(secret)
	return secret, err
}

func fileSecret(base string, create bool) ([]byte, error) {
	data, err := os.ReadFile(secretFilePath(base))
	if err == nil && len(data) == secretSize {
		return data, nil
	}
	if !create {
		return nil, errNoSecret
	}
	secret, err := newSecret()
	if err != nil {
		return nil, err
	}
	if err := writeFileAtomic(secretFilePath(base), secret, 0o600); err != nil {
		return nil, err
	}
	return secret, nil
}
