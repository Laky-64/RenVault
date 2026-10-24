//go:build windows

package screen

import (
	"syscall"

	"golang.org/x/sys/windows"
)

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
	pid := windows.GetCurrentProcessId()
	affinity := uintptr(wdaNone)
	if on {
		affinity = wdaExcludeFromCapture
	}
	callback := syscall.NewCallback(func(hwnd, _ uintptr) uintptr {
		var owner uint32
		if _, err := windows.GetWindowThreadProcessId(windows.HWND(hwnd), &owner); err != nil || owner != pid {
			return 1
		}
		if ok, _, _ := procSetWindowDisplayAffinity.Call(hwnd, affinity); ok == 0 && on {
			procSetWindowDisplayAffinity.Call(hwnd, wdaMonitor)
		}
		return 1
	})
	procEnumWindows.Call(callback, 0)
	return nil
}
