//go:build windows

package winplugin

import (
	"fmt"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	webauthn = windows.NewLazySystemDLL("webauthn.dll")
	ole32    = windows.NewLazySystemDLL("ole32.dll")
	kernel32 = windows.NewLazySystemDLL("kernel32.dll")

	procAddAuthenticator       = webauthn.NewProc("WebAuthNPluginAddAuthenticator")
	procFreeAddResponse        = webauthn.NewProc("WebAuthNPluginFreeAddAuthenticatorResponse")
	procAuthenticatorState     = webauthn.NewProc("WebAuthNPluginGetAuthenticatorState")
	procAddCredentials         = webauthn.NewProc("WebAuthNPluginAuthenticatorAddCredentials")
	procRemoveAllCredentials   = webauthn.NewProc("WebAuthNPluginAuthenticatorRemoveAllCredentials")
	procPerformVerification    = webauthn.NewProc("WebAuthNPluginPerformUserVerification")
	procFreeVerification       = webauthn.NewProc("WebAuthNPluginFreeUserVerificationResponse")
	procCoInitializeEx         = ole32.NewProc("CoInitializeEx")
	procCoRegisterClassObject  = ole32.NewProc("CoRegisterClassObject")
	procCoTaskMemAlloc         = ole32.NewProc("CoTaskMemAlloc")
	procCurrentPackageFullName = kernel32.NewProc("GetCurrentPackageFullName")
)

const (
	sOK              = 0
	eNoInterface     = 0x80004002
	eFail            = 0x80004005
	ePointer         = 0x80004003
	eOutOfMemory     = 0x8007000E
	classNoAggregate = 0x80040110
	hrCancelled      = 0x800704C7
	hrNotFound       = 0x80090011
	hrBusy           = 0x800700AA

	coinitMultithreaded = 0x0
	clsctxLocalServer   = 0x4
	regclsMultipleUse   = 0x1

	appmodelNoPackage = 15700
	stateEnabled      = 1
)

var (
	clsidRenVault          = windows.GUID{Data1: 0xbda0089a, Data2: 0x7184, Data3: 0x42f4, Data4: [8]byte{0x84, 0x0c, 0x38, 0xa3, 0x20, 0x95, 0x26, 0xf8}}
	iidUnknown             = windows.GUID{Data1: 0x00000000, Data2: 0x0000, Data3: 0x0000, Data4: [8]byte{0xc0, 0, 0, 0, 0, 0, 0, 0x46}}
	iidClassFactory        = windows.GUID{Data1: 0x00000001, Data2: 0x0000, Data3: 0x0000, Data4: [8]byte{0xc0, 0, 0, 0, 0, 0, 0, 0x46}}
	iidPluginAuthenticator = windows.GUID{Data1: 0xd26bcf6f, Data2: 0xb54c, Data3: 0x43ff, Data4: [8]byte{0x9f, 0x06, 0xd5, 0xbf, 0x14, 0x86, 0x25, 0xf7}}
)

type addOptions struct {
	name      *uint16
	clsid     *windows.GUID
	rpID      *uint16
	lightLogo *uint16
	darkLogo  *uint16
	infoSize  uint32
	info      *byte
	rpCount   uint32
	rpIDs     **uint16
}

type addResponse struct {
	keySize uint32
	key     *byte
}

type credentialDetails struct {
	idSize          uint32
	id              *byte
	rpID            *uint16
	rpName          *uint16
	userIDSize      uint32
	userID          *byte
	userName        *uint16
	userDisplayName *uint16
}

type verificationRequest struct {
	window      uintptr
	transaction *windows.GUID
	username    *uint16
	hint        *uint16
}

type operationRequest struct {
	window        uintptr
	transaction   windows.GUID
	signatureSize uint32
	signature     *byte
	requestType   int32
	encodedSize   uint32
	encoded       *byte
}

type operationResponse struct {
	encodedSize uint32
	encoded     *byte
}

type cancelRequest struct {
	transaction   windows.GUID
	signatureSize uint32
	signature     *byte
}

func hresult(r uintptr) error {
	if int32(r) >= 0 {
		return nil
	}
	return fmt.Errorf("winplugin: hresult 0x%08x", uint32(r))
}

func packaged() bool {
	var length uint32
	r, _, _ := procCurrentPackageFullName.Call(uintptr(unsafe.Pointer(&length)), 0)
	return r != appmodelNoPackage
}

func available() bool {
	return webauthn.Load() == nil && procAddAuthenticator.Find() == nil && procPerformVerification.Find() == nil
}

func taskMemCopy(data []byte) (*byte, error) {
	if len(data) == 0 {
		return nil, nil
	}
	r, _, _ := procCoTaskMemAlloc.Call(uintptr(len(data)))
	if r == 0 {
		return nil, fmt.Errorf("winplugin: out of memory")
	}
	copy(unsafe.Slice((*byte)(unsafe.Pointer(r)), len(data)), data)
	return (*byte)(unsafe.Pointer(r)), nil
}

func utf16(s string) *uint16 {
	if s == "" {
		return nil
	}
	p, err := windows.UTF16PtrFromString(s)
	if err != nil {
		return nil
	}
	return p
}

func bytesPtr(b []byte) *byte {
	if len(b) == 0 {
		return nil
	}
	return &b[0]
}
