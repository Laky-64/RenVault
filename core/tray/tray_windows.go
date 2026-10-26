//go:build windows

package tray

import (
	"runtime"
	"sync"
	"syscall"
	"unsafe"

	"github.com/Laky-64/renvault-core/darkmode"
	"golang.org/x/sys/windows"
)

const (
	wmClose       = 0x0010
	wmNull        = 0x0000
	wmDestroy     = 0x0002
	wmContextMenu = 0x007B
	wmApp         = 0x8000
	wmTray        = wmApp + 1
	wmRefresh     = wmApp + 2
	ninSelect     = 0x0400
	ninKeySelect  = 0x0401

	nimAdd        = 0
	nimModify     = 1
	nimDelete     = 2
	nimSetVersion = 4
	nifMessage    = 0x1
	nifIcon       = 0x2
	nifTip        = 0x4
	nifShowTip    = 0x80
	iconVersion4  = 4

	mfString           = 0x0
	tpmRightAlign      = 0x8
	tpmBottomAlign     = 0x20
	tpmRightButton     = 0x2
	tpmReturnCmd       = 0x100
	smCxSmIcon         = 49
	smMenuDropAlign    = 40
	lrDefaultColor     = 0x0
	iconResourceFormat = 0x00030000
	iconID             = 1
)

var (
	user32  = windows.NewLazySystemDLL("user32.dll")
	shell32 = windows.NewLazySystemDLL("shell32.dll")

	procRegisterClassEx          = user32.NewProc("RegisterClassExW")
	procCreateWindowEx           = user32.NewProc("CreateWindowExW")
	procDefWindowProc            = user32.NewProc("DefWindowProcW")
	procDestroyWindow            = user32.NewProc("DestroyWindow")
	procGetMessage               = user32.NewProc("GetMessageW")
	procTranslateMessage         = user32.NewProc("TranslateMessage")
	procDispatchMessage          = user32.NewProc("DispatchMessageW")
	procPostMessage              = user32.NewProc("PostMessageW")
	procPostQuitMessage          = user32.NewProc("PostQuitMessage")
	procRegisterWindowMessage    = user32.NewProc("RegisterWindowMessageW")
	procCreatePopupMenu          = user32.NewProc("CreatePopupMenu")
	procAppendMenu               = user32.NewProc("AppendMenuW")
	procDestroyMenu              = user32.NewProc("DestroyMenu")
	procTrackPopupMenuEx         = user32.NewProc("TrackPopupMenuEx")
	procSetForegroundWindow      = user32.NewProc("SetForegroundWindow")
	procGetSystemMetrics         = user32.NewProc("GetSystemMetrics")
	procCreateIconFromResourceEx = user32.NewProc("CreateIconFromResourceEx")
	procDestroyIcon              = user32.NewProc("DestroyIcon")
	procShellNotifyIcon          = shell32.NewProc("Shell_NotifyIconW")

	className = windows.StringToUTF16Ptr("RenVaultTray")
	wndProc   = syscall.NewCallback(handle)

	showing        sync.Mutex
	mu             sync.Mutex
	window         uintptr
	current        Config
	icon           uintptr
	report         func(id string)
	taskbarCreated uintptr
)

type wndClassEx struct {
	size       uint32
	style      uint32
	wndProc    uintptr
	clsExtra   int32
	wndExtra   int32
	instance   windows.Handle
	icon       windows.Handle
	cursor     windows.Handle
	background windows.Handle
	menuName   *uint16
	className  *uint16
	iconSm     windows.Handle
}

type notifyIconData struct {
	size            uint32
	window          uintptr
	id              uint32
	flags           uint32
	callbackMessage uint32
	icon            uintptr
	tip             [128]uint16
	state           uint32
	stateMask       uint32
	info            [256]uint16
	version         uint32
	infoTitle       [64]uint16
	infoFlags       uint32
	guidItem        windows.GUID
	balloonIcon     uintptr
}

type msg struct {
	window  uintptr
	message uint32
	wParam  uintptr
	lParam  uintptr
	time    uint32
	pt      struct{ x, y int32 }
	private uint32
}

func Show(config Config, onEvent func(id string)) error {
	showing.Lock()
	defer showing.Unlock()
	mu.Lock()
	defer mu.Unlock()
	current = config
	report = onEvent
	if window != 0 {
		procPostMessage.Call(window, wmRefresh, 0, 0)
		return nil
	}

	ready := make(chan error, 1)
	go loop(ready)
	mu.Unlock()
	err := <-ready
	mu.Lock()
	return err
}

func Hide() {
	mu.Lock()
	hwnd := window
	mu.Unlock()
	if hwnd != 0 {
		procPostMessage.Call(hwnd, wmClose, 0, 0)
	}
}

func loop(ready chan<- error) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	instance, _, _ := windows.NewLazySystemDLL("kernel32.dll").NewProc("GetModuleHandleW").Call(0)
	class := wndClassEx{wndProc: wndProc, instance: windows.Handle(instance), className: className}
	class.size = uint32(unsafe.Sizeof(class))
	procRegisterClassEx.Call(uintptr(unsafe.Pointer(&class)))

	hwnd, _, err := procCreateWindowEx.Call(0, uintptr(unsafe.Pointer(className)), 0, 0, 0, 0, 0, 0, 0, 0, instance, 0)
	if hwnd == 0 {
		ready <- err
		return
	}
	taskbarCreated, _, _ = procRegisterWindowMessage.Call(uintptr(unsafe.Pointer(windows.StringToUTF16Ptr("TaskbarCreated"))))
	darkmode.AllowWindow(hwnd)

	mu.Lock()
	window = hwnd
	mu.Unlock()
	if !notify(nimAdd) {
		procDestroyWindow.Call(hwnd)
		ready <- ErrUnsupported
		return
	}
	ready <- nil

	var m msg
	for {
		if r, _, _ := procGetMessage.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0); int32(r) <= 0 {
			return
		}
		procTranslateMessage.Call(uintptr(unsafe.Pointer(&m)))
		procDispatchMessage.Call(uintptr(unsafe.Pointer(&m)))
	}
}

func handle(hwnd, message, wParam, lParam uintptr) uintptr {
	switch {
	case message == wmTray:
		switch lParam & 0xFFFF {
		case ninSelect, ninKeySelect:
			emit(Activated)
		case wmContextMenu:
			showMenu(hwnd, int32(int16(wParam&0xFFFF)), int32(int16(wParam>>16&0xFFFF)))
		}
		return 0
	case message == wmRefresh:
		notify(nimModify)
		return 0
	case message == taskbarCreated && taskbarCreated != 0:
		notify(nimAdd)
		return 0
	case message == wmClose:
		notify(nimDelete)
		procDestroyWindow.Call(hwnd)
		return 0
	case message == wmDestroy:
		mu.Lock()
		window = 0
		if icon != 0 {
			procDestroyIcon.Call(icon)
			icon = 0
		}
		mu.Unlock()
		procPostQuitMessage.Call(0)
		return 0
	}
	r, _, _ := procDefWindowProc.Call(hwnd, message, wParam, lParam)
	return r
}

func notify(action uintptr) bool {
	mu.Lock()
	defer mu.Unlock()
	data := notifyIconData{window: window, id: iconID, flags: nifMessage | nifTip | nifShowTip, callbackMessage: wmTray}
	data.size = uint32(unsafe.Sizeof(data))
	if action != nimDelete {
		if next := iconOf(current.Icon); next != 0 {
			if icon != 0 {
				procDestroyIcon.Call(icon)
			}
			icon = next
		}
		if icon != 0 {
			data.flags |= nifIcon
			data.icon = icon
		}
		tip, _ := windows.UTF16FromString(current.Tooltip)
		copy(data.tip[:len(data.tip)-1], tip)
	}
	ok, _, _ := procShellNotifyIcon.Call(action, uintptr(unsafe.Pointer(&data)))
	if ok != 0 && action == nimAdd {
		data.version = iconVersion4
		procShellNotifyIcon.Call(nimSetVersion, uintptr(unsafe.Pointer(&data)))
	}
	return ok != 0
}

func iconOf(png []byte) uintptr {
	if len(png) == 0 {
		return 0
	}
	side, _, _ := procGetSystemMetrics.Call(smCxSmIcon)
	handle, _, _ := procCreateIconFromResourceEx.Call(
		uintptr(unsafe.Pointer(&png[0])), uintptr(len(png)), 1, iconResourceFormat, side, side, lrDefaultColor,
	)
	return handle
}

func showMenu(hwnd uintptr, x, y int32) {
	mu.Lock()
	items := current.Items
	mu.Unlock()

	menu, _, _ := procCreatePopupMenu.Call()
	if menu == 0 {
		return
	}
	defer procDestroyMenu.Call(menu)
	for i, item := range items {
		procAppendMenu.Call(menu, mfString, uintptr(i+1), uintptr(unsafe.Pointer(windows.StringToUTF16Ptr(item.Label))))
	}

	flags := uintptr(tpmReturnCmd | tpmRightButton | tpmBottomAlign)
	if align, _, _ := procGetSystemMetrics.Call(smMenuDropAlign); align != 0 {
		flags |= tpmRightAlign
	}
	procSetForegroundWindow.Call(hwnd)
	chosen, _, _ := procTrackPopupMenuEx.Call(menu, flags, uintptr(x), uintptr(y), hwnd, 0)
	procPostMessage.Call(hwnd, wmNull, 0, 0)
	if chosen > 0 && int(chosen) <= len(items) {
		emit(items[chosen-1].ID)
	}
}

func emit(id string) {
	mu.Lock()
	callback := report
	mu.Unlock()
	if callback != nil {
		go callback(id)
	}
}

func TakeActivationToken() string {
	return ""
}
