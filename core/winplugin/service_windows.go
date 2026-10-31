//go:build windows

package winplugin

import (
	"context"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"
	"unsafe"

	"github.com/Laky-64/renvault-core/ctap"
	"github.com/Laky-64/renvault-core/localkey"
	"golang.org/x/sys/windows"
)

const (
	commandMakeCredential byte = 0x01
	commandGetAssertion   byte = 0x02

	statusOperationDenied byte = 0x27
	statusNoCredentials   byte = 0x2e
	statusCancelled       byte = 0x2d

	operationTimeout = 3 * time.Minute
	unlockPoll       = 250 * time.Millisecond

	packageFile     = "RenVault.msix"
	certificateFile = "RenVault.cer"
)

var transportsInternal = []string{"internal", "hybrid"}

type Service struct {
	vault Vault
	hooks Hooks
	auth  *ctap.Authenticator

	mu   sync.Mutex
	ops  map[windows.GUID]context.CancelFunc
	busy bool
}

func NewService(v Vault, hooks Hooks) *Service {
	s := &Service{vault: v, hooks: hooks, ops: map[windows.GUID]context.CancelFunc{}}
	s.auth = ctap.NewAuthenticator(localkey.NewStore(v), s.choose, s.approve, transportsInternal)
	return s
}

func (s *Service) Start() error {
	if !available() || !packaged() {
		return nil
	}
	current.Lock()
	current.service = s
	current.Unlock()
	return registerClass()
}

func (s *Service) Status() Status {
	st := Status{Supported: available(), Packaged: packaged()}
	if !st.Supported || !st.Packaged {
		return st
	}
	var state int32
	r, _, _ := procAuthenticatorState.Call(uintptr(unsafe.Pointer(&clsidRenVault)), uintptr(unsafe.Pointer(&state)))
	if hresult(r) == nil {
		st.Installed = true
		st.Enabled = state == stateEnabled
	}
	return st
}

func (s *Service) Install() error {
	if !available() {
		return ErrUnsupported
	}
	if !packaged() {
		return ErrNoPackage
	}
	info, err := ctap.GetInfoResponse(transportsInternal)
	if err != nil {
		return err
	}
	options := addOptions{
		name:     utf16(authenticatorName),
		clsid:    &clsidRenVault,
		infoSize: uint32(len(info)),
		info:     bytesPtr(info),
	}
	var response *addResponse
	r, _, _ := procAddAuthenticator.Call(uintptr(unsafe.Pointer(&options)), uintptr(unsafe.Pointer(&response)))
	if response != nil {
		procFreeAddResponse.Call(uintptr(unsafe.Pointer(response)))
	}
	if err := hresult(r); err != nil {
		return err
	}
	return s.Sync()
}

func (s *Service) Sync() error {
	if !available() || !packaged() || !s.vault.Unlocked() {
		return nil
	}
	if !s.Status().Installed {
		return nil
	}
	procRemoveAllCredentials.Call(uintptr(unsafe.Pointer(&clsidRenVault)))
	var details []credentialDetails
	for _, m := range s.vault.ListPasskey() {
		if m.IsDeleted || len(m.UserHandle) == 0 {
			continue
		}
		id, err := hex.DecodeString(m.CredentialID)
		if err != nil || len(id) == 0 {
			continue
		}
		user := append([]byte(nil), m.UserHandle...)
		name := m.UserName
		display := m.DisplayName
		if display == "" {
			display = name
		}
		details = append(details, credentialDetails{
			idSize:          uint32(len(id)),
			id:              &id[0],
			rpID:            utf16(m.RelyingParty),
			rpName:          utf16(m.RelyingParty),
			userIDSize:      uint32(len(user)),
			userID:          &user[0],
			userName:        utf16(name),
			userDisplayName: utf16(display),
		})
	}
	if len(details) == 0 {
		return nil
	}
	r, _, _ := procAddCredentials.Call(uintptr(unsafe.Pointer(&clsidRenVault)), uintptr(len(details)), uintptr(unsafe.Pointer(&details[0])))
	return hresult(r)
}

func (s *Service) Register() error {
	if !available() {
		return ErrUnsupported
	}
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	dir := filepath.Dir(exe)
	pkg := locate(dir, packageFile)
	cert := locate(dir, certificateFile)
	if pkg == "" {
		return fmt.Errorf("winplugin: %s is missing from %s", packageFile, dir)
	}
	err = addPackage(pkg, dir)
	if err == nil || cert == "" || !untrusted(err) {
		return err
	}
	if err := trust(cert); err != nil {
		return err
	}
	return addPackage(pkg, dir)
}

func (s *Service) Restart() error {
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	script := fmt.Sprintf("Start-Sleep -Seconds 2; Start-Process -FilePath %s", quote(exe))
	cmd := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: windows.DETACHED_PROCESS}
	return cmd.Start()
}

func OpenSettings() error {
	return windows.ShellExecute(0, utf16("open"), utf16("ms-settings:savedpasskeys"), nil, nil, windows.SW_SHOWNORMAL)
}

func locate(dir, name string) string {
	for _, candidate := range []string{filepath.Join(dir, name), filepath.Join(dir, "app", name), filepath.Join(dir, "app", "resources", name)} {
		if _, err := os.Stat(candidate); err == nil {
			return candidate
		}
	}
	return ""
}

func quote(s string) string {
	return "'" + strings.ReplaceAll(s, "'", "''") + "'"
}

func powershell(script string) error {
	cmd := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("winplugin: %w: %s", err, strings.TrimSpace(string(out)))
	}
	return nil
}

func addPackage(pkg, dir string) error {
	return powershell(fmt.Sprintf("$ErrorActionPreference='Stop'; Add-AppxPackage -Path %s -ExternalLocation %s -ForceUpdateFromAnyVersion", quote(pkg), quote(dir)))
}

func untrusted(err error) bool {
	text := strings.ToLower(err.Error())
	return strings.Contains(text, "0x800b0109") || strings.Contains(text, "0x800b010a") || strings.Contains(text, "certificat")
}

func trust(cert string) error {
	return powershell(fmt.Sprintf("$p = Start-Process -FilePath certutil.exe -ArgumentList '-addstore','TrustedPeople',%s -Verb RunAs -Wait -PassThru -WindowStyle Hidden; if ($p.ExitCode -ne 0) { exit $p.ExitCode }", quote(cert)))
}

func operate(command byte, request, response uintptr) uintptr {
	if request == 0 || response == 0 {
		return ePointer
	}
	s := activeService()
	if s == nil {
		return eFail
	}
	return s.operate(command, (*operationRequest)(unsafe.Pointer(request)), (*operationResponse)(unsafe.Pointer(response)))
}

func (s *Service) operate(command byte, req *operationRequest, resp *operationResponse) uintptr {
	s.mu.Lock()
	if s.busy {
		s.mu.Unlock()
		return hrBusy
	}
	s.busy = true
	ctx, cancel := context.WithTimeout(context.Background(), operationTimeout)
	s.ops[req.transaction] = cancel
	s.mu.Unlock()
	defer func() {
		cancel()
		s.mu.Lock()
		delete(s.ops, req.transaction)
		s.busy = false
		s.mu.Unlock()
	}()

	var encoded []byte
	if req.encoded != nil && req.encodedSize > 0 {
		encoded = append([]byte(nil), unsafe.Slice(req.encoded, req.encodedSize)...)
	}
	message := encoded
	if len(encoded) == 0 || encoded[0]&0xe0 == 0xa0 {
		message = append([]byte{command}, encoded...)
	}
	diagnose(command, encoded)

	ctx = withRequest(ctx, verifyRequest{window: req.window, transaction: *(*[16]byte)(unsafe.Pointer(&req.transaction))})
	out, err := s.auth.Handle(ctx, message)
	if err != nil || len(out) == 0 {
		return eFail
	}
	switch out[0] {
	case 0:
	case statusCancelled, statusOperationDenied:
		return hrCancelled
	case statusNoCredentials:
		return hrNotFound
	default:
		return eFail
	}
	body := out[1:]
	data, err := taskMemCopy(body)
	if err != nil {
		return eOutOfMemory
	}
	resp.encodedSize = uint32(len(body))
	resp.encoded = data
	return sOK
}

func (s *Service) cancel(transaction windows.GUID) uintptr {
	s.mu.Lock()
	cancel, ok := s.ops[transaction]
	s.mu.Unlock()
	if !ok {
		return hrNotFound
	}
	cancel()
	return sOK
}

func (s *Service) waitUnlocked(ctx context.Context) error {
	if s.vault.Unlocked() {
		return nil
	}
	if s.hooks.OnLocked != nil {
		s.hooks.OnLocked()
	}
	ticker := time.NewTicker(unlockPoll)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return ErrLocked
		case <-ticker.C:
			if s.vault.Unlocked() {
				return nil
			}
		}
	}
}

func (s *Service) choose(ctx context.Context, rp string, options []ctap.Credential) (ctap.Credential, error) {
	if err := s.waitUnlocked(ctx); err != nil {
		return ctap.Credential{}, err
	}
	if len(options) == 1 {
		err := verify(ctx, options[0].UserName)
		if err == nil {
			return options[0], nil
		}
		if !errors.Is(err, errNoHello) {
			return ctap.Credential{}, err
		}
	}
	if s.hooks.Choose == nil {
		return ctap.Credential{}, ErrCancelled
	}
	return s.hooks.Choose(ctx, rp, options)
}

func (s *Service) approve(ctx context.Context, c ctap.NewCredential) error {
	if err := s.waitUnlocked(ctx); err != nil {
		return err
	}
	err := verify(ctx, c.UserName)
	if err == nil || !errors.Is(err, errNoHello) {
		return err
	}
	if s.hooks.Approve == nil {
		return ErrCancelled
	}
	return s.hooks.Approve(ctx, c)
}

func verify(ctx context.Context, username string) error {
	r, ok := requestFrom(ctx)
	if !ok {
		return errNoHello
	}
	transaction := *(*windows.GUID)(unsafe.Pointer(&r.transaction))
	request := verificationRequest{
		window:      r.window,
		transaction: &transaction,
		username:    utf16(username),
		hint:        utf16(authenticatorName),
	}
	done := make(chan uintptr, 1)
	go func() {
		var size uint32
		var data *byte
		hr, _, _ := procPerformVerification.Call(uintptr(unsafe.Pointer(&request)), uintptr(unsafe.Pointer(&size)), uintptr(unsafe.Pointer(&data)))
		if data != nil {
			procFreeVerification.Call(uintptr(unsafe.Pointer(data)))
		}
		done <- hr
	}()
	select {
	case <-ctx.Done():
		return ErrCancelled
	case hr := <-done:
		switch uint32(hr) {
		case sOK:
			return nil
		case hrCancelled, 0x80090036:
			return ErrCancelled
		default:
			return errNoHello
		}
	}
}

func diagnose(command byte, encoded []byte) {
	dir, err := os.UserCacheDir()
	if err != nil {
		return
	}
	dir = filepath.Join(dir, "RenVault")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return
	}
	f, err := os.OpenFile(filepath.Join(dir, "winplugin.log"), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600)
	if err != nil {
		return
	}
	defer f.Close()
	head := encoded
	if len(head) > 4 {
		head = head[:4]
	}
	fmt.Fprintf(f, "%s command=0x%02x size=%d head=%x\n", time.Now().Format(time.RFC3339), command, len(encoded), head)
}
