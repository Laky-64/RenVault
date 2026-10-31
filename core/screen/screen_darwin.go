//go:build darwin && !ios && cgo

package screen

/*
#cgo CFLAGS: -x objective-c -fobjc-arc
#cgo LDFLAGS: -framework AppKit

void rv_secure_windows(int on);
*/
import "C"

func Supported() bool { return true }

func SetSecure(on bool) error {
	flag := C.int(0)
	if on {
		flag = 1
	}
	C.rv_secure_windows(flag)
	return nil
}
