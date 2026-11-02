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
	Bundled   bool `cbor:"bundled"`
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
		return winPasskeyStatus{Supported: st.Supported, Packaged: st.Packaged, Bundled: st.Bundled, Installed: st.Installed, Enabled: st.Enabled}, nil
	})

	query(bridge.OpWinPasskeyEnable, func(context.Context) (winPasskeyEnable, error) {
		s := winPasskeyService()
		if s == nil {
			return winPasskeyEnable{}, winplugin.ErrUnsupported
		}
		if !s.Status().Packaged {
			if err := s.Register(); err != nil {
				winplugin.Note("register", err)
				return winPasskeyEnable{}, err
			}
			return winPasskeyEnable{Restart: true}, nil
		}
		if err := s.Install(); err != nil {
			winplugin.Note("install", err)
			return winPasskeyEnable{}, err
		}
		err := winplugin.OpenSettings()
		winplugin.Note("open settings", err)
		return winPasskeyEnable{}, err
	})

	command(bridge.OpWinPasskeySync, func(context.Context, none) error {
		if s := winPasskeyService(); s != nil {
			return s.Sync()
		}
		return nil
	})

	command(bridge.OpWinPasskeyDisable, func(context.Context, none) error {
		if s := winPasskeyService(); s != nil {
			return s.Uninstall()
		}
		return winplugin.ErrUnsupported
	})

	command(bridge.OpWinPasskeyRestart, func(context.Context, none) error {
		if s := winPasskeyService(); s != nil {
			return s.Restart()
		}
		return winplugin.ErrUnsupported
	})
}
