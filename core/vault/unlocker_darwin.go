//go:build darwin && !ios

package vault

/*
#cgo CFLAGS: -x objective-c -fobjc-arc
#cgo LDFLAGS: -framework Foundation -framework LocalAuthentication -framework Security
#include <stdlib.h>

int rv_touch_available(void);
int rv_touch_verify(const char *reason);
int rv_keychain_put(const char *account, const void *data, int length);
int rv_keychain_get(const char *account, void *out, int capacity);
*/
import "C"

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"unsafe"
)

const touchReason = "unlock RenVault"

var errTouchRefused = errors.New("vault: Touch ID did not verify the user")

type touchUnlocker struct{}

func platformUnlockers() []Unlocker { return []Unlocker{touchUnlocker{}} }

func (touchUnlocker) Method() string { return "touch-id" }

func (touchUnlocker) Available() bool { return C.rv_touch_available() == 1 }

func (touchUnlocker) Protect(k []byte) ([]byte, error) {
	if err := touchVerify(); err != nil {
		return nil, err
	}
	id := make([]byte, 16)
	if _, err := rand.Read(id); err != nil {
		return nil, err
	}
	account := C.CString(hex.EncodeToString(id))
	defer C.free(unsafe.Pointer(account))
	if status := C.rv_keychain_put(account, unsafe.Pointer(&k[0]), C.int(len(k))); status != 0 {
		return nil, errors.New("vault: could not save the quick unlock key in the Keychain")
	}
	return []byte(hex.EncodeToString(id)), nil
}

func (touchUnlocker) Unlock(blob []byte) ([]byte, error) {
	if err := touchVerify(); err != nil {
		return nil, err
	}
	account := C.CString(string(blob))
	defer C.free(unsafe.Pointer(account))
	out := make([]byte, 64)
	n := C.rv_keychain_get(account, unsafe.Pointer(&out[0]), C.int(len(out)))
	if n <= 0 {
		return nil, errQuickOff
	}
	return out[:n], nil
}

func touchVerify() error {
	reason := C.CString(touchReason)
	defer C.free(unsafe.Pointer(reason))
	switch C.rv_touch_verify(reason) {
	case 0:
		return nil
	case 1:
		return ErrCancelled
	}
	return errTouchRefused
}
