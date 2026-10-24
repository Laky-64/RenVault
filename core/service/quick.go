package service

import (
	"context"
	"errors"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/vault"
)

func installQuick() {
	query(bridge.OpQuickState, func(context.Context) (quickStateResponse, error) {
		active, err := opened()
		if err != nil {
			return quickStateResponse{}, err
		}
		state, err := active.QuickState()
		return quickStateResponse{
			Methods: vault.QuickMethods(),
			Method:  state.Method,
			Sealed:  state.Sealed,
			Ready:   state.Ready,
			Expired: state.Expired,
		}, err
	})

	command(bridge.OpQuickEnable, func(_ context.Context, request quickRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return cancelled(active.EnableQuick(request.Method, request.Key, request.Sealed))
	})

	trigger(bridge.OpQuickDisable, func(context.Context) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.DisableQuick()
	})

	command(bridge.OpQuickUnlock, func(_ context.Context, request quickRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return cancelled(active.UnlockWithQuick(request.Method, request.Key))
	})
}

func cancelled(err error) error {
	if errors.Is(err, vault.ErrCancelled) {
		return bridge.Fail(bridge.KindCancelled, err.Error())
	}
	return err
}
