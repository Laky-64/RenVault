//go:build darwin && !ios && cgo

package vault

/*
#cgo CFLAGS: -x objective-c -fobjc-arc
#cgo LDFLAGS: -framework Foundation -framework LocalAuthentication -framework Security
#include <stdlib.h>

int rv_touch_available(void);
int rv_touch_verify(const char *reason, int credential);
int rv_keychain_put(const char *account, const void *data, int length);
int rv_keychain_get(const char *account, void *out, int capacity);
*/
import "C"

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"strings"
	"unsafe"
)

const (
	touchReason      = "unlock RenVault"
	credentialPrefix = "credential:"
)

var errTouchRefused = errors.New("vault: Touch ID did not verify the user")

type touchUnlocker struct{}

func platformUnlockers() []Unlocker { return []Unlocker{touchUnlocker{}} }

func (touchUnlocker) Method() string { return "touch-id" }

func (touchUnlocker) Available() bool { return C.rv_touch_available() == 1 }

func (touchUnlocker) Protect(k []byte, credential bool) ([]byte, error) {
	if err := touchVerify(credential); err != nil {
		return nil, err
	}
	id := make([]byte, 16)
	if _, err := rand.Read(id); err != nil {
		return nil, err
	}
	name := hex.EncodeToString(id)
	account := C.CString(name)
	defer C.free(unsafe.Pointer(account))
	if status := C.rv_keychain_put(account, unsafe.Pointer(&k[0]), C.int(len(k))); status != 0 {
		return nil, errors.New("vault: could not save the quick unlock key in the Keychain")
	}
	if credential {
		name = credentialPrefix + name
	}
	return []byte(name), nil
}

func (touchUnlocker) Unlock(blob []byte) ([]byte, error) {
	name, credential := strings.CutPrefix(string(blob), credentialPrefix)
	if err := touchVerify(credential); err != nil {
		return nil, err
	}
	account := C.CString(name)
	defer C.free(unsafe.Pointer(account))
	out := make([]byte, 64)
	n := C.rv_keychain_get(account, unsafe.Pointer(&out[0]), C.int(len(out)))
	if n <= 0 {
		return nil, errQuickOff
	}
	return out[:n], nil
}

func touchVerify(credential bool) error {
	reason := C.CString(touchReason)
	defer C.free(unsafe.Pointer(reason))
	allow := C.int(0)
	if credential {
		allow = 1
	}
	switch C.rv_touch_verify(reason, allow) {
	case 0:
		return nil
	case 1:
		return ErrCancelled
	}
	return errTouchRefused
}
