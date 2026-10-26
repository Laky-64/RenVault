//go:build windows

package darkmode

import (
	"sync"
	"syscall"

	"golang.org/x/sys/windows"
)

const (
	minimumBuild       = 18362
	preferredAllowDark = 1

	ordinalAllowForWindow   = 133
	ordinalPreferredAppMode = 135
	ordinalFlushMenuThemes  = 136
)

var (
	once                sync.Once
	allowForWindow      uintptr
	setPreferredAppMode uintptr
	flushMenuThemes     uintptr
)

func load() {
	if windows.RtlGetVersion().BuildNumber < minimumBuild {
		return
	}
	uxtheme, err := windows.LoadLibraryEx("uxtheme.dll", 0, windows.LOAD_LIBRARY_SEARCH_SYSTEM32)
	if err != nil {
		return
	}
	allowForWindow, _ = windows.GetProcAddressByOrdinal(uxtheme, ordinalAllowForWindow)
	setPreferredAppMode, _ = windows.GetProcAddressByOrdinal(uxtheme, ordinalPreferredAppMode)
	flushMenuThemes, _ = windows.GetProcAddressByOrdinal(uxtheme, ordinalFlushMenuThemes)
	if setPreferredAppMode != 0 {
		syscall.SyscallN(setPreferredAppMode, preferredAllowDark)
	}
}

func Allow() {
	once.Do(load)
}

func AllowWindow(hwnd uintptr) {
	Allow()
	if allowForWindow != 0 {
		syscall.SyscallN(allowForWindow, hwnd, 1)
	}
	if flushMenuThemes != 0 {
		syscall.SyscallN(flushMenuThemes)
	}
}
