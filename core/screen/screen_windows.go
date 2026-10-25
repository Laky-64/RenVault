//go:build windows

package screen

import "golang.org/x/sys/windows"

const (
	wdaNone               = 0x00
	wdaMonitor            = 0x01
	wdaExcludeFromCapture = 0x11
)

var (
	user32                       = windows.NewLazySystemDLL("user32.dll")
	procEnumWindows              = user32.NewProc("EnumWindows")
	procSetWindowDisplayAffinity = user32.NewProc("SetWindowDisplayAffinity")
)

func Supported() bool { return procSetWindowDisplayAffinity.Find() == nil }

func SetSecure(on bool) error {
	if !Supported() {
		return ErrUnsupported
	}
	affinity := uintptr(wdaNone)
	if on {
		affinity = wdaExcludeFromCapture
	}
	eachWindow(func(hwnd uintptr) {
		if ok, _, _ := procSetWindowDisplayAffinity.Call(hwnd, affinity); ok == 0 && on {
			procSetWindowDisplayAffinity.Call(hwnd, wdaMonitor)
		}
	})
	return nil
}
