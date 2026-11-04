//go:build windows

package winplugin

import (
	"archive/zip"
	"context"
	"crypto/sha1"
	"crypto/x509"
	"encoding/hex"
	"encoding/xml"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
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

	statusExcluded        byte = 0x19
	statusOperationDenied byte = 0x27
	statusNoCredentials   byte = 0x2e
	statusCancelled       byte = 0x2d

	operationTimeout = 3 * time.Minute
	unlockPoll       = 250 * time.Millisecond

	restartWait = 30

	packageFile     = "RenVault.msix"
	certificateFile = "RenVault.cer"
)

var transportsInternal = []string{"internal"}

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
	if exe, err := os.Executable(); err == nil {
		st.Bundled = locate(filepath.Dir(exe), packageFile) != ""
	}
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
		rpID:     utf16(pluginRpID),
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

func (s *Service) Uninstall() error {
	if !available() || !packaged() {
		return nil
	}
	procRemoveAllCredentials.Call(uintptr(unsafe.Pointer(&clsidRenVault)))
	r, _, _ := procRemoveAuthenticator.Call(uintptr(unsafe.Pointer(&clsidRenVault)))
	return hresult(r)
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
	if cert != "" {
		trusted, err := isTrusted(cert)
		if err != nil {
			return fmt.Errorf("check certificate: %w", err)
		}
		if !trusted {
			if err := trust(cert); err != nil {
				return fmt.Errorf("trust certificate: %w", err)
			}
		}
	}
	if err := addPackage(pkg, dir); err != nil {
		return fmt.Errorf("add package: %w", err)
	}
	return nil
}

func (s *Service) Restart() error {
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	script := fmt.Sprintf("Wait-Process -Id %d -Timeout %d -ErrorAction SilentlyContinue; Start-Process -FilePath %s", os.Getpid(), restartWait, quote(exe))
	cmd := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: windows.DETACHED_PROCESS}
	return cmd.Start()
}

func HandOff() bool {
	if !available() || !packaged() || embedded() {
		return false
	}
	script := fmt.Sprintf("Wait-Process -Id %d -Timeout %d -ErrorAction SilentlyContinue; ", os.Getpid(), restartWait)
	if pkg, dir := bundledUpdate(); pkg != "" {
		script += fmt.Sprintf("try { Add-AppxPackage -Path %s -ExternalLocation %s -ForceUpdateFromAnyVersion -ErrorAction Stop } catch { Add-Content -LiteralPath %s -Value ((Get-Date -Format s) + ' package update failed: ' + $_) }; ", quote(pkg), quote(dir), quote(logPath()))
	}
	script += fmt.Sprintf("[void][Activator]::CreateInstance([Type]::GetTypeFromCLSID('%s'))", clsidRenVault.String())
	err := startOutsidePackage("cmd.exe /d /c powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -Command \"" + script + "\"")
	if err != nil {
		writeLog(fmt.Sprintf("hand off failed: %v", err))
		return false
	}
	return true
}

func startOutsidePackage(command string) error {
	attributes, err := windows.NewProcThreadAttributeList(1)
	if err != nil {
		return err
	}
	defer attributes.Delete()
	policy := uint32(desktopAppBreakawayProcessTree)
	if err := attributes.Update(procThreadAttributeDesktopAppPolicy, unsafe.Pointer(&policy), unsafe.Sizeof(policy)); err != nil {
		return err
	}
	info := windows.StartupInfoEx{ProcThreadAttributeList: attributes.List()}
	info.Cb = uint32(unsafe.Sizeof(info))
	info.Flags = windows.STARTF_USESHOWWINDOW
	info.ShowWindow = windows.SW_HIDE
	line, err := windows.UTF16PtrFromString(command)
	if err != nil {
		return err
	}
	var process windows.ProcessInformation
	if err := windows.CreateProcess(nil, line, nil, nil, false, windows.EXTENDED_STARTUPINFO_PRESENT|windows.CREATE_NO_WINDOW, nil, nil, &info.StartupInfo, &process); err != nil {
		return err
	}
	windows.CloseHandle(process.Thread)
	windows.CloseHandle(process.Process)
	return nil
}

func bundledUpdate() (pkg, dir string) {
	exe, err := os.Executable()
	if err != nil {
		return "", ""
	}
	dir = filepath.Dir(exe)
	pkg = locate(dir, packageFile)
	if pkg == "" {
		return "", ""
	}
	bundled, err := bundledVersion(pkg)
	if err != nil {
		writeLog(fmt.Sprintf("read %s failed: %v", packageFile, err))
		return "", ""
	}
	if compareVersions(bundled, registeredVersion()) <= 0 {
		return "", ""
	}
	return pkg, dir
}

func bundledVersion(pkg string) (string, error) {
	archive, err := zip.OpenReader(pkg)
	if err != nil {
		return "", err
	}
	defer archive.Close()
	manifest, err := archive.Open("AppxManifest.xml")
	if err != nil {
		return "", err
	}
	defer manifest.Close()
	var parsed struct {
		Identity struct {
			Version string `xml:"Version,attr"`
		} `xml:"Identity"`
	}
	if err := xml.NewDecoder(manifest).Decode(&parsed); err != nil {
		return "", err
	}
	return parsed.Identity.Version, nil
}

func registeredVersion() string {
	length := uint32(256)
	name := make([]uint16, length)
	r, _, _ := procCurrentPackageFullName.Call(uintptr(unsafe.Pointer(&length)), uintptr(unsafe.Pointer(&name[0])))
	if r != 0 {
		return ""
	}
	parts := strings.Split(windows.UTF16ToString(name), "_")
	if len(parts) < 2 {
		return ""
	}
	return parts[1]
}

func compareVersions(a, b string) int {
	x, y := strings.Split(a, "."), strings.Split(b, ".")
	for i := 0; i < 4; i++ {
		var p, q int
		if i < len(x) {
			p, _ = strconv.Atoi(x[i])
		}
		if i < len(y) {
			q, _ = strconv.Atoi(y[i])
		}
		if p != q {
			return p - q
		}
	}
	return 0
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

func isTrusted(cert string) (bool, error) {
	der, err := os.ReadFile(cert)
	if err != nil {
		return false, err
	}
	parsed, err := x509.ParseCertificate(der)
	if err != nil {
		return false, err
	}
	sum := sha1.Sum(parsed.Raw)
	thumbprint := strings.ToUpper(hex.EncodeToString(sum[:]))
	err = powershell(fmt.Sprintf("if (-not (Test-Path %s)) { exit 3 }", quote(`Cert:\LocalMachine\TrustedPeople\`+thumbprint)))
	var exit *exec.ExitError
	if errors.As(err, &exit) && exit.ExitCode() == 3 {
		return false, nil
	}
	return err == nil, err
}

func trust(cert string) error {
	args := fmt.Sprintf(`-addstore TrustedPeople "%s"`, cert)
	return powershell(fmt.Sprintf("$p = Start-Process -FilePath certutil.exe -ArgumentList %s -Verb RunAs -Wait -PassThru -WindowStyle Hidden; if ($p.ExitCode -ne 0) { exit $p.ExitCode }", quote(args)))
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
	case statusExcluded:
		return hrExists
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
	if command == commandMakeCredential {
		go func() { Note("sync", s.Sync()) }()
	}
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
	head := encoded
	if len(head) > 4 {
		head = head[:4]
	}
	writeLog(fmt.Sprintf("command=0x%02x size=%d head=%x", command, len(encoded), head))
}

func Note(step string, err error) {
	if err == nil {
		return
	}
	writeLog(fmt.Sprintf("%s failed: %v available=%v packaged=%v", step, err, available(), packaged()))
}

func logPath() string {
	dir, err := os.UserCacheDir()
	if err != nil {
		return ""
	}
	return filepath.Join(dir, "RenVault", "winplugin.log")
}

func writeLog(line string) {
	path := logPath()
	if path == "" {
		return
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return
	}
	f, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600)
	if err != nil {
		return
	}
	defer f.Close()
	fmt.Fprintf(f, "%s %s\n", time.Now().Format(time.RFC3339), line)
}
