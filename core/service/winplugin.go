package service

import (
	"context"
	"sync"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/ctap"
	"github.com/Laky-64/renvault-core/tray"
	"github.com/Laky-64/renvault-core/vault"
	"github.com/Laky-64/renvault-core/winplugin"
)

const winPasskeySession = "windows"

type winPasskeyStatus struct {
	Supported bool `cbor:"supported"`
	Packaged  bool `cbor:"packaged"`
	Installed bool `cbor:"installed"`
	Enabled   bool `cbor:"enabled"`
}

type winPasskeyEnable struct {
	Restart bool `cbor:"restart"`
}

var winPasskeys struct {
	sync.Mutex
	service *winplugin.Service
}

func startWinPasskeys(v *vault.Vault) {
	s := winplugin.NewService(v, winplugin.Hooks{
		Choose: func(ctx context.Context, rp string, options []ctap.Credential) (ctap.Credential, error) {
			return chooseWith(ctx, winPasskeySession, rp, options)
		},
		Approve: func(ctx context.Context, c ctap.NewCredential) error {
			return approveWith(ctx, winPasskeySession, c)
		},
		OnLocked: func() {
			bridge.Emit(bridge.EventTray, trayEvent{Item: tray.Activated})
		},
	})
	winPasskeys.Lock()
	winPasskeys.service = s
	winPasskeys.Unlock()
	_ = s.Start()
}

func winPasskeyService() *winplugin.Service {
	winPasskeys.Lock()
	defer winPasskeys.Unlock()
	return winPasskeys.service
}

func syncWinPasskeys() {
	if s := winPasskeyService(); s != nil {
		go func() { _ = s.Sync() }()
	}
}

func installWinPasskeys() {
	query(bridge.OpWinPasskeyStatus, func(context.Context) (winPasskeyStatus, error) {
		s := winPasskeyService()
		if s == nil {
			return winPasskeyStatus{}, nil
		}
		st := s.Status()
		return winPasskeyStatus{Supported: st.Supported, Packaged: st.Packaged, Installed: st.Installed, Enabled: st.Enabled}, nil
	})

	query(bridge.OpWinPasskeyEnable, func(context.Context) (winPasskeyEnable, error) {
		s := winPasskeyService()
		if s == nil {
			return winPasskeyEnable{}, winplugin.ErrUnsupported
		}
		if !s.Status().Packaged {
			if err := s.Register(); err != nil {
				return winPasskeyEnable{}, err
			}
			return winPasskeyEnable{Restart: true}, nil
		}
		if err := s.Install(); err != nil {
			return winPasskeyEnable{}, err
		}
		return winPasskeyEnable{}, winplugin.OpenSettings()
	})

	command(bridge.OpWinPasskeySync, func(context.Context, none) error {
		if s := winPasskeyService(); s != nil {
			return s.Sync()
		}
		return nil
	})

	command(bridge.OpWinPasskeySettings, func(context.Context, none) error {
		return winplugin.OpenSettings()
	})

	command(bridge.OpWinPasskeyRestart, func(context.Context, none) error {
		if s := winPasskeyService(); s != nil {
			return s.Restart()
		}
		return winplugin.ErrUnsupported
	})
}
