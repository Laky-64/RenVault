package service

import (
	"sync"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/icons"
	"github.com/Laky-64/renvault-core/vault"
)

var (
	mu      sync.RWMutex
	current *vault.Vault
	tiles   = icons.New()
)

func start(base string) {
	if base == "" {
		base = vault.DefaultBase()
	}

	opened := vault.New(base)
	opened.SetOnLock(func() { bridge.Emit(bridge.EventLocked, none{}) })
	opened.SetOnSync(func() { bridge.Emit(bridge.EventSyncChanged, syncStateOf(opened)) })

	mu.Lock()
	defer mu.Unlock()
	current = opened
}

func opened() (*vault.Vault, error) {
	mu.RLock()
	defer mu.RUnlock()

	if current == nil {
		return nil, bridge.Fail(bridge.KindNotInitialised, "service: the vault is not initialised")
	}
	return current, nil
}

func unlocked() (*vault.Vault, error) {
	active, err := opened()
	if err != nil {
		return nil, err
	}
	if !active.Unlocked() {
		return nil, bridge.Fail(bridge.KindLocked, "service: the vault is locked")
	}
	return active, nil
}

func syncStateOf(active *vault.Vault) syncResponse {
	state := active.SyncState()
	return syncResponse{
		Pending:  state.Pending,
		SyncedAt: millis(state.SyncedAt),
		Error:    state.Error,
	}
}
