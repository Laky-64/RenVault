package service

import (
	"context"
	"errors"
	"sync"
	"time"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/ctap"
	"github.com/Laky-64/renvault-core/localkey"
	"github.com/Laky-64/renvault-core/notify"
	"github.com/Laky-64/renvault-core/tray"
	"github.com/Laky-64/renvault-core/vault"
)

const (
	localKeySession = "localkey"
	localKeyWait    = 60 * time.Second
)

type notifyRequest struct {
	Title string `cbor:"title"`
	Body  string `cbor:"body"`
}

type notifyResponse struct {
	ID uint32 `cbor:"id"`
}

type localKeyStatus struct {
	Supported bool `cbor:"supported"`
	Permitted bool `cbor:"permitted"`
	Running   bool `cbor:"running"`
}

var localKeyState struct {
	sync.Mutex
	vault   *vault.Vault
	service *localkey.Service
}

func installLocalKey() {
	query(bridge.OpLocalKeyStatus, func(context.Context) (localKeyStatus, error) {
		localKeyState.Lock()
		defer localKeyState.Unlock()
		running := localKeyState.service != nil && localKeyState.service.Running()
		return localKeyStatus{Supported: localkey.Supported(), Permitted: localkey.Permitted(), Running: running}, nil
	})

	command(bridge.OpLocalKeyStart, func(context.Context, none) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		localKeyState.Lock()
		defer localKeyState.Unlock()
		if localKeyState.service != nil && localKeyState.vault != active {
			_ = localKeyState.service.Stop()
			localKeyState.service = nil
		}
		if localKeyState.service == nil {
			localKeyState.vault = active
			localKeyState.service = localkey.NewService(active, chooseLocalKey, approveLocalKey)
		}
		return localKeyState.service.Start()
	})

	command(bridge.OpLocalKeyGrant, func(context.Context, none) error {
		return localkey.Grant()
	})

	handle(bridge.OpNotifyShow, func(_ context.Context, request notifyRequest) (notifyResponse, error) {
		id, err := notify.Show(request.Title, request.Body, func(token string) {
			tray.GiveActivationToken(token)
			bridge.Emit(bridge.EventTray, trayEvent{Item: tray.Activated})
		})
		if errors.Is(err, notify.ErrUnsupported) {
			return notifyResponse{}, bridge.Fail(bridge.KindBadOp, err.Error())
		}
		return notifyResponse{ID: id}, err
	})

	command(bridge.OpNotifyClose, func(_ context.Context, request notifyResponse) error {
		notify.Close(request.ID)
		return nil
	})

	command(bridge.OpLocalKeyStop, func(context.Context, none) error {
		localKeyState.Lock()
		defer localKeyState.Unlock()
		if localKeyState.service == nil {
			return nil
		}
		return localKeyState.service.Stop()
	})
}

func chooseLocalKey(ctx context.Context, rp string, options []ctap.Credential) (ctap.Credential, error) {
	return chooseWith(ctx, localKeySession, rp, options)
}

func approveLocalKey(ctx context.Context, c ctap.NewCredential) error {
	return approveWith(ctx, localKeySession, c)
}

func chooseWith(ctx context.Context, session, rp string, options []ctap.Credential) (ctap.Credential, error) {
	ctx, cancel := context.WithTimeout(ctx, localKeyWait)
	defer cancel()
	accounts := make([]hybridAccount, len(options))
	for i, o := range options {
		accounts[i] = hybridAccount{Title: o.DisplayName, Username: o.UserName}
	}
	d, err := askHybrid(ctx, hybridPrompt{Session: session, RelyingParty: rp, Accounts: accounts})
	if err != nil {
		return ctap.Credential{}, err
	}
	if d.account < 0 || d.account >= len(options) {
		return ctap.Credential{}, errHybridDeclined
	}
	return options[d.account], nil
}

func approveWith(ctx context.Context, session string, c ctap.NewCredential) error {
	ctx, cancel := context.WithTimeout(ctx, localKeyWait)
	defer cancel()
	_, err := askHybrid(ctx, hybridPrompt{Session: session, RelyingParty: c.RelyingParty, Create: true, Username: c.UserName})
	return err
}
