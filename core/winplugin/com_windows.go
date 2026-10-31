//go:build windows

package winplugin

import (
	"runtime"
	"sync"
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	comOnce     sync.Once
	comErr      error
	factoryObj  uintptr
	pluginObj   uintptr
	classCookie uint32
	current     struct {
		sync.Mutex
		service *Service
	}
)

func activeService() *Service {
	current.Lock()
	defer current.Unlock()
	return current.service
}

func newObject(methods []uintptr) (uintptr, error) {
	size := uintptr(len(methods)) * unsafe.Sizeof(uintptr(0))
	table, err := windows.LocalAlloc(windows.LPTR, uint32(size))
	if err != nil {
		return 0, err
	}
	copy(unsafe.Slice((*uintptr)(unsafe.Pointer(table)), len(methods)), methods)
	object, err := windows.LocalAlloc(windows.LPTR, uint32(unsafe.Sizeof(uintptr(0))))
	if err != nil {
		return 0, err
	}
	*(*uintptr)(unsafe.Pointer(object)) = table
	return object, nil
}

func sameGUID(p uintptr, g *windows.GUID) bool {
	return p != 0 && *(*windows.GUID)(unsafe.Pointer(p)) == *g
}

func registerClass() error {
	comOnce.Do(func() {
		addRef := syscall.NewCallback(func(this uintptr) uintptr { return 1 })
		release := syscall.NewCallback(func(this uintptr) uintptr { return 1 })

		pluginObj, comErr = newObject([]uintptr{
			syscall.NewCallback(func(this, riid, out uintptr) uintptr {
				if out == 0 {
					return ePointer
				}
				if sameGUID(riid, &iidUnknown) || sameGUID(riid, &iidPluginAuthenticator) {
					*(*uintptr)(unsafe.Pointer(out)) = this
					return sOK
				}
				*(*uintptr)(unsafe.Pointer(out)) = 0
				return eNoInterface
			}),
			addRef,
			release,
			syscall.NewCallback(func(this, request, response uintptr) uintptr {
				return operate(commandMakeCredential, request, response)
			}),
			syscall.NewCallback(func(this, request, response uintptr) uintptr {
				return operate(commandGetAssertion, request, response)
			}),
			syscall.NewCallback(func(this, request uintptr) uintptr {
				if request == 0 {
					return ePointer
				}
				s := activeService()
				if s == nil {
					return hrNotFound
				}
				return s.cancel((*cancelRequest)(unsafe.Pointer(request)).transaction)
			}),
			syscall.NewCallback(func(this, status uintptr) uintptr {
				if status == 0 {
					return ePointer
				}
				value := int32(0)
				if s := activeService(); s != nil && s.vault.Unlocked() {
					value = 1
				}
				*(*int32)(unsafe.Pointer(status)) = value
				return sOK
			}),
		})
		if comErr != nil {
			return
		}

		factoryObj, comErr = newObject([]uintptr{
			syscall.NewCallback(func(this, riid, out uintptr) uintptr {
				if out == 0 {
					return ePointer
				}
				if sameGUID(riid, &iidUnknown) || sameGUID(riid, &iidClassFactory) {
					*(*uintptr)(unsafe.Pointer(out)) = this
					return sOK
				}
				*(*uintptr)(unsafe.Pointer(out)) = 0
				return eNoInterface
			}),
			addRef,
			release,
			syscall.NewCallback(func(this, outer, riid, out uintptr) uintptr {
				if out == 0 {
					return ePointer
				}
				*(*uintptr)(unsafe.Pointer(out)) = 0
				if outer != 0 {
					return classNoAggregate
				}
				if sameGUID(riid, &iidUnknown) || sameGUID(riid, &iidPluginAuthenticator) {
					*(*uintptr)(unsafe.Pointer(out)) = pluginObj
					return sOK
				}
				return eNoInterface
			}),
			syscall.NewCallback(func(this, lock uintptr) uintptr { return sOK }),
		})
		if comErr != nil {
			return
		}

		ready := make(chan error, 1)
		go func() {
			runtime.LockOSThread()
			r, _, _ := procCoInitializeEx.Call(0, coinitMultithreaded)
			if err := hresult(r); err != nil {
				ready <- err
				return
			}
			r, _, _ = procCoRegisterClassObject.Call(
				uintptr(unsafe.Pointer(&clsidRenVault)),
				factoryObj,
				clsctxLocalServer,
				regclsMultipleUse,
				uintptr(unsafe.Pointer(&classCookie)),
			)
			ready <- hresult(r)
			select {}
		}()
		comErr = <-ready
	})
	return comErr
}
