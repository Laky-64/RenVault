package winplugin

import (
	"context"
	"errors"

	"github.com/Laky-64/renvault-core/ctap"
	"github.com/Laky-64/renvault-core/localkey"
)

const authenticatorName = "RenVault"

var (
	ErrUnsupported = errors.New("winplugin: passkeys in windows need windows 11 24h2 or newer")
	ErrNoPackage   = errors.New("winplugin: renvault is not registered as a windows package")
	ErrLocked      = errors.New("winplugin: the vault is locked")
	ErrCancelled   = errors.New("winplugin: cancelled")
	errNoHello     = errors.New("winplugin: windows hello is not available")
)

type Vault interface {
	localkey.Vault
}

type Status struct {
	Supported bool
	Packaged  bool
	Installed bool
	Enabled   bool
}

type Hooks struct {
	Choose   ctap.Chooser
	Approve  ctap.Approver
	OnLocked func()
}

type verifyRequest struct {
	window      uintptr
	transaction [16]byte
}

type requestKey struct{}

func withRequest(ctx context.Context, r verifyRequest) context.Context {
	return context.WithValue(ctx, requestKey{}, r)
}

func requestFrom(ctx context.Context) (verifyRequest, bool) {
	r, ok := ctx.Value(requestKey{}).(verifyRequest)
	return r, ok
}
