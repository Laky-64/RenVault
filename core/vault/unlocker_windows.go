//go:build windows

package vault

import (
	"errors"
	"runtime"
	"syscall"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

const helloMessage = "RenVault"

var (
	combase                    = windows.NewLazySystemDLL("combase.dll")
	procRoInitialize           = combase.NewProc("RoInitialize")
	procRoGetActivationFactory = combase.NewProc("RoGetActivationFactory")
	procWindowsCreateString    = combase.NewProc("WindowsCreateString")
	procWindowsDeleteString    = combase.NewProc("WindowsDeleteString")
	procGetForegroundWindow    = windows.NewLazySystemDLL("user32.dll").NewProc("GetForegroundWindow")

	iidVerifierStatics = windows.GUID{Data1: 0xAF4F3F91, Data2: 0x564C, Data3: 0x4DDC, Data4: [8]byte{0xB8, 0xB5, 0x97, 0x34, 0x47, 0x62, 0x7C, 0x65}}
	iidVerifierInterop = windows.GUID{Data1: 0x39E050C3, Data2: 0x4E74, Data3: 0x441A, Data4: [8]byte{0x8D, 0xC0, 0xB8, 0x11, 0x04, 0xDF, 0x94, 0x9C}}
	iidVerificationOp  = windows.GUID{Data1: 0xFD596FFD, Data2: 0x2318, Data3: 0x558F, Data4: [8]byte{0x9D, 0xBE, 0xD2, 0x1D, 0xF4, 0x37, 0x64, 0xA5}}
	iidAsyncInfo       = windows.GUID{Data1: 0x00000036, Data2: 0x0000, Data3: 0x0000, Data4: [8]byte{0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46}}

	errHelloRefused = errors.New("vault: Windows Hello did not verify the user")
)

const (
	asyncStarted   = 0
	asyncCompleted = 1
	helloOK        = 0
	helloCanceled  = 6
)

type helloUnlocker struct{}

func platformUnlockers() []Unlocker { return []Unlocker{helloUnlocker{}} }

func (helloUnlocker) Method() string { return "windows-hello" }

func (helloUnlocker) Available() bool {
	ok := false
	_ = withWinRT(func() error {
		statics, err := activationFactory(&iidVerifierStatics)
		if err != nil {
			return err
		}
		defer release(statics)
		var op uintptr
		if hr := call(statics, 6, uintptr(unsafe.Pointer(&op))); hr != 0 {
			return syscall.Errno(hr)
		}
		defer release(op)
		state, err := awaitEnum(op)
		ok = err == nil && state == 0
		return err
	})
	return ok
}

func (helloUnlocker) Protect(k []byte) ([]byte, error) {
	if err := helloVerify(); err != nil {
		return nil, err
	}
	var out windows.DataBlob
	if err := windows.CryptProtectData(blobOf(k), nil, nil, 0, nil, windows.CRYPTPROTECT_UI_FORBIDDEN, &out); err != nil {
		return nil, err
	}
	return blobBytes(out), nil
}

func (helloUnlocker) Unlock(blob []byte) ([]byte, error) {
	if err := helloVerify(); err != nil {
		return nil, err
	}
	var out windows.DataBlob
	if err := windows.CryptUnprotectData(blobOf(blob), nil, nil, 0, nil, windows.CRYPTPROTECT_UI_FORBIDDEN, &out); err != nil {
		return nil, err
	}
	return blobBytes(out), nil
}

func helloVerify() error {
	return withWinRT(func() error {
		message, err := hstring(helloMessage)
		if err != nil {
			return err
		}
		defer procWindowsDeleteString.Call(message)

		var op uintptr
		interop, err := activationFactory(&iidVerifierInterop)
		if err == nil {
			hwnd, _, _ := procGetForegroundWindow.Call()
			hr := call(interop, 6, hwnd, message, uintptr(unsafe.Pointer(&iidVerificationOp)), uintptr(unsafe.Pointer(&op)))
			release(interop)
			if hr != 0 {
				op = 0
			}
		}
		if op == 0 {
			statics, err := activationFactory(&iidVerifierStatics)
			if err != nil {
				return err
			}
			hr := call(statics, 7, message, uintptr(unsafe.Pointer(&op)))
			release(statics)
			if hr != 0 {
				return syscall.Errno(hr)
			}
		}
		defer release(op)
		result, err := awaitEnum(op)
		switch {
		case err != nil:
			return err
		case result == helloCanceled:
			return ErrCancelled
		case result != helloOK:
			return errHelloRefused
		}
		return nil
	})
}

func withWinRT(fn func() error) error {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	procRoInitialize.Call(1)
	return fn()
}

func hstring(s string) (uintptr, error) {
	u, err := windows.UTF16FromString(s)
	if err != nil {
		return 0, err
	}
	var h uintptr
	if hr, _, _ := procWindowsCreateString.Call(uintptr(unsafe.Pointer(&u[0])), uintptr(len(u)-1), uintptr(unsafe.Pointer(&h))); hr != 0 {
		return 0, syscall.Errno(hr)
	}
	return h, nil
}

func activationFactory(iid *windows.GUID) (uintptr, error) {
	class, err := hstring("Windows.Security.Credentials.UI.UserConsentVerifier")
	if err != nil {
		return 0, err
	}
	defer procWindowsDeleteString.Call(class)
	var factory uintptr
	if hr, _, _ := procRoGetActivationFactory.Call(class, uintptr(unsafe.Pointer(iid)), uintptr(unsafe.Pointer(&factory))); hr != 0 {
		return 0, syscall.Errno(hr)
	}
	return factory, nil
}

func call(obj uintptr, slot int, args ...uintptr) uintptr {
	vtbl := *(*[16]uintptr)(unsafe.Pointer(*(*uintptr)(unsafe.Pointer(obj))))
	hr, _, _ := syscall.SyscallN(vtbl[slot], append([]uintptr{obj}, args...)...)
	return uintptr(uint32(hr))
}

func release(obj uintptr) {
	if obj != 0 {
		call(obj, 2)
	}
}

func awaitEnum(op uintptr) (int32, error) {
	var info uintptr
	if hr := call(op, 0, uintptr(unsafe.Pointer(&iidAsyncInfo)), uintptr(unsafe.Pointer(&info))); hr != 0 {
		return 0, syscall.Errno(hr)
	}
	defer release(info)
	for {
		var status int32
		if hr := call(info, 7, uintptr(unsafe.Pointer(&status))); hr != 0 {
			return 0, syscall.Errno(hr)
		}
		if status == asyncCompleted {
			break
		}
		if status != asyncStarted {
			return 0, ErrCancelled
		}
		time.Sleep(50 * time.Millisecond)
	}
	var result int32
	if hr := call(op, 8, uintptr(unsafe.Pointer(&result))); hr != 0 {
		return 0, syscall.Errno(hr)
	}
	return result, nil
}
