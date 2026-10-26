//go:build windows

package portal

import (
	"context"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
	"unsafe"

	"github.com/Laky-64/renvault-core/darkmode"
	"golang.org/x/sys/windows"
)

const (
	fosOverwritePrompt = 0x2
	fosForceFileSystem = 0x40
	fosPathMustExist   = 0x800
	fosFileMustExist   = 0x1000
	sigdnFileSysPath   = 0x80058000
	errorCancelled     = 0x800704C7
	clsctxInprocServer = 0x1
)

const (
	vtblRelease         = 2
	vtblShow            = 3
	vtblSetFileTypes    = 4
	vtblSetOptions      = 9
	vtblGetOptions      = 10
	vtblSetFileName     = 15
	vtblSetTitle        = 17
	vtblGetResult       = 20
	vtblSetDefaultExt   = 22
	vtblItemDisplayName = 5
)

var (
	clsidFileOpenDialog = windows.GUID{Data1: 0xDC1C5A9C, Data2: 0xE88A, Data3: 0x4DDE, Data4: [8]byte{0xA5, 0xA1, 0x60, 0xF8, 0x2A, 0x20, 0xAE, 0xF7}}
	clsidFileSaveDialog = windows.GUID{Data1: 0xC0B4E2F3, Data2: 0xBA21, Data3: 0x4773, Data4: [8]byte{0x8D, 0xBA, 0x33, 0x5E, 0xC9, 0x46, 0xEB, 0x8B}}
	iidFileOpenDialog   = windows.GUID{Data1: 0xD57C7288, Data2: 0xD4AD, Data3: 0x4768, Data4: [8]byte{0xBE, 0x02, 0x9D, 0x96, 0x95, 0x32, 0xD9, 0x60}}
	iidFileSaveDialog   = windows.GUID{Data1: 0x84BCCD23, Data2: 0x5FDE, Data3: 0x4CDB, Data4: [8]byte{0xAE, 0xA4, 0xAF, 0x64, 0xB8, 0x3D, 0x78, 0xAB}}

	procCoCreateInstance = windows.NewLazySystemDLL("ole32.dll").NewProc("CoCreateInstance")
)

type comObject struct {
	vtbl *[32]uintptr
}

func (o *comObject) call(index int, args ...uintptr) uintptr {
	r, _, _ := syscall.SyscallN(o.vtbl[index], append([]uintptr{uintptr(unsafe.Pointer(o))}, args...)...)
	return r
}

func (o *comObject) release() {
	o.call(vtblRelease)
}

type filterSpec struct {
	name *uint16
	spec *uint16
}

func OpenFile(_ context.Context, title string, f Filter) ([]byte, error) {
	path, err := choose(clsidFileOpenDialog, iidFileOpenDialog, title, "", f, fosFileMustExist|fosPathMustExist)
	if err != nil || path == "" {
		return nil, err
	}
	return readFile(path)
}

func SaveFile(_ context.Context, title, name string, f Filter, data []byte) (bool, error) {
	path, err := choose(clsidFileSaveDialog, iidFileSaveDialog, title, name, f, fosOverwritePrompt)
	if err != nil || path == "" {
		return false, err
	}
	return true, os.WriteFile(path, data, 0o600)
}

func choose(clsid, iid windows.GUID, title, name string, f Filter, options uint32) (string, error) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	switch err := windows.CoInitializeEx(0, windows.COINIT_APARTMENTTHREADED); err {
	case nil, syscall.Errno(windows.S_FALSE):
		defer windows.CoUninitialize()
	case syscall.Errno(windows.RPC_E_CHANGED_MODE):
	default:
		return "", ErrUnavailable
	}
	darkmode.Allow()

	var dialog *comObject
	if hr, _, _ := procCoCreateInstance.Call(
		uintptr(unsafe.Pointer(&clsid)), 0, clsctxInprocServer,
		uintptr(unsafe.Pointer(&iid)), uintptr(unsafe.Pointer(&dialog)),
	); hr != 0 || dialog == nil {
		return "", ErrUnavailable
	}
	defer dialog.release()

	var current uint32
	dialog.call(vtblGetOptions, uintptr(unsafe.Pointer(&current)))
	dialog.call(vtblSetOptions, uintptr(current|options|fosForceFileSystem))
	if title != "" {
		dialog.call(vtblSetTitle, uintptr(unsafe.Pointer(windows.StringToUTF16Ptr(title))))
	}
	if name != "" {
		dialog.call(vtblSetFileName, uintptr(unsafe.Pointer(windows.StringToUTF16Ptr(name))))
		if ext := strings.TrimPrefix(filepath.Ext(name), "."); ext != "" {
			dialog.call(vtblSetDefaultExt, uintptr(unsafe.Pointer(windows.StringToUTF16Ptr(ext))))
		}
	}
	if spec := patternsOf(f); spec != "" {
		specs := []filterSpec{{name: windows.StringToUTF16Ptr(f.Name), spec: windows.StringToUTF16Ptr(spec)}}
		dialog.call(vtblSetFileTypes, uintptr(len(specs)), uintptr(unsafe.Pointer(&specs[0])))
		runtime.KeepAlive(specs)
	}

	switch hr := dialog.call(vtblShow, uintptr(ownerWindow())); {
	case hr == errorCancelled:
		return "", nil
	case hr != 0:
		return "", syscall.Errno(hr)
	}

	var item *comObject
	if hr := dialog.call(vtblGetResult, uintptr(unsafe.Pointer(&item))); hr != 0 || item == nil {
		return "", syscall.Errno(hr)
	}
	defer item.release()

	var display *uint16
	if hr := item.call(vtblItemDisplayName, sigdnFileSysPath, uintptr(unsafe.Pointer(&display))); hr != 0 || display == nil {
		return "", syscall.Errno(hr)
	}
	defer windows.CoTaskMemFree(unsafe.Pointer(display))
	return windows.UTF16PtrToString(display), nil
}

func patternsOf(f Filter) string {
	seen := map[string]bool{}
	var out []string
	for _, pattern := range f.Patterns {
		pattern = strings.ToLower(pattern)
		if !seen[pattern] {
			seen[pattern] = true
			out = append(out, pattern)
		}
	}
	return strings.Join(out, ";")
}

func ownerWindow() windows.HWND {
	hwnd := windows.GetForegroundWindow()
	var pid uint32
	if _, err := windows.GetWindowThreadProcessId(hwnd, &pid); err != nil || pid != windows.GetCurrentProcessId() {
		return 0
	}
	return hwnd
}
