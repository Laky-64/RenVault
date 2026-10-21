package service

import (
	"context"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/vault"
)

func installVault() {
	command(bridge.OpVaultInit, func(_ context.Context, request initRequest) error {
		start(request.Base)
		return nil
	})

	query(bridge.OpVaultState, func(context.Context) (stateResponse, error) {
		active, err := opened()
		if err != nil {
			return stateResponse{}, err
		}
		return stateResponse{Configured: active.Configured(), Unlocked: active.Unlocked()}, nil
	})

	command(bridge.OpVaultUnlock, func(_ context.Context, request secretRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.UnlockWithPassword(request.Secret)
	})

	trigger(bridge.OpVaultLock, func(context.Context) error {
		active, err := opened()
		if err != nil {
			return err
		}
		active.Lock()
		return nil
	})

	query(bridge.OpVaultProfile, func(context.Context) (profileResponse, error) {
		active, err := opened()
		if err != nil {
			return profileResponse{}, err
		}
		profile := active.ProfileInfo()
		return profileResponse{
			Name:     profile.Name,
			AppleID:  profile.AppleID,
			HasPhoto: profile.HasPhoto,
		}, nil
	})

	query(bridge.OpVaultProfilePhoto, func(context.Context) (bytesResponse, error) {
		active, err := opened()
		if err != nil {
			return bytesResponse{}, err
		}
		photo, _ := active.ProfilePhoto()
		return bytesResponse{Data: photo}, nil
	})

	query(bridge.OpVaultSettings, func(context.Context) (settingsResponse, error) {
		active, err := opened()
		if err != nil {
			return settingsResponse{}, err
		}
		settings := active.Settings()
		return settingsResponse{
			SortField:       settings.SortField,
			SortAscending:   settings.SortAscending,
			AutoLockMinutes: settings.AutoLockMinutes,
		}, nil
	})

	command(bridge.OpVaultSetSort, func(_ context.Context, request sortRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.SetSort(request.Field, request.Ascending)
	})

	command(bridge.OpVaultSetAutoLock, func(_ context.Context, request autoLockRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.SetAutoLockMinutes(request.Minutes)
	})

	query(bridge.OpVaultNames, func(context.Context) (namesPayload, error) {
		active, err := opened()
		if err != nil {
			return namesPayload{}, err
		}
		names, err := active.Names()
		if err != nil {
			return namesPayload{}, err
		}
		out := namesPayload{Names: make([]nameSwap, len(names))}
		for i, swap := range names {
			out.Names[i] = nameSwap(swap)
		}
		return out, nil
	})

	command(bridge.OpVaultSetNames, func(_ context.Context, request namesPayload) error {
		active, err := opened()
		if err != nil {
			return err
		}
		names := make([]vault.NameSwap, len(request.Names))
		for i, swap := range request.Names {
			names[i] = vault.NameSwap(swap)
		}
		return active.SetNames(names)
	})

	query(bridge.OpVaultAutoLockChoices, func(context.Context) (minutesResponse, error) {
		return minutesResponse{Minutes: vault.AutoLockChoices()}, nil
	})

	trigger(bridge.OpVaultSignOut, func(context.Context) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.SignOut()
	})

	query(bridge.OpVaultSync, func(context.Context) (changedResponse, error) {
		active, err := unlocked()
		if err != nil {
			return changedResponse{}, err
		}
		changed, err := active.Sync()
		return changedResponse{Changed: changed}, err
	})

	query(bridge.OpVaultSyncState, func(context.Context) (syncResponse, error) {
		active, err := opened()
		if err != nil {
			return syncResponse{}, err
		}
		return syncStateOf(active), nil
	})

	query(bridge.OpVaultCheckPwned, func(context.Context) (pwnedResponse, error) {
		active, err := unlocked()
		if err != nil {
			return pwnedResponse{}, err
		}
		report, err := active.CheckPwned()
		if err != nil {
			return pwnedResponse{}, err
		}
		return pwnedResponseOf(report), nil
	})

	query(bridge.OpVaultPwnedInfo, func(context.Context) (pwnedResponse, error) {
		active, err := opened()
		if err != nil {
			return pwnedResponse{}, err
		}
		return pwnedResponseOf(active.PwnedInfo()), nil
	})
}

func pwnedResponseOf(report vault.PwnedReport) pwnedResponse {
	return pwnedResponse{
		Checked:   report.Checked,
		Pwned:     report.Pwned,
		CheckedAt: millis(report.CheckedAt),
	}
}
