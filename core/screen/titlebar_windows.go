//go:build windows

package screen

import (
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

const (
	dwmUseImmersiveDarkMode = 20
	dwmCaptionColor         = 35
	dwmTextColor            = 36
)

var procDwmSetWindowAttribute = windows.NewLazySystemDLL("dwmapi.dll").NewProc("DwmSetWindowAttribute")

func SetTitleBar(dark bool, caption, text uint32) error {
	if procDwmSetWindowAttribute.Find() != nil {
		return ErrUnsupported
	}
	mode := uint32(0)
	if dark {
		mode = 1
	}
	set := func(hwnd uintptr, attribute uintptr, value uint32) {
		procDwmSetWindowAttribute.Call(hwnd, attribute, uintptr(unsafe.Pointer(&value)), unsafe.Sizeof(value))
	}
	eachWindow(func(hwnd uintptr) {
		set(hwnd, dwmUseImmersiveDarkMode, mode)
		set(hwnd, dwmCaptionColor, colorRef(caption))
		set(hwnd, dwmTextColor, colorRef(text))
	})
	return nil
}

func colorRef(rgb uint32) uint32 {
	return (rgb&0xFF)<<16 | rgb&0xFF00 | (rgb>>16)&0xFF
}

func eachWindow(fn func(hwnd uintptr)) {
	pid := windows.GetCurrentProcessId()
	callback := syscall.NewCallback(func(hwnd, _ uintptr) uintptr {
		var owner uint32
		if _, err := windows.GetWindowThreadProcessId(windows.HWND(hwnd), &owner); err == nil && owner == pid {
			fn(hwnd)
		}
		return 1
	})
	procEnumWindows.Call(callback, 0)
}
