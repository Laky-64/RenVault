package service

import (
	"context"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/vault"
)

func installLogin() {
	handle(bridge.OpLoginStart, func(_ context.Context, request startLoginRequest) (startLoginResponse, error) {
		active, err := opened()
		if err != nil {
			return startLoginResponse{}, err
		}
		needsCode, err := active.StartLogin(request.AppleID, request.Password)
		return startLoginResponse{NeedsCode: needsCode}, err
	})

	trigger(bridge.OpLoginCode, func(context.Context) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.RequestCode()
	})

	command(bridge.OpLoginSubmit, func(_ context.Context, request submitCodeRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.SubmitCode(request.Code)
	})

	query(bridge.OpLoginBottles, func(context.Context) ([]bottleEntry, error) {
		active, err := opened()
		if err != nil {
			return nil, err
		}
		bottles, err := active.Bottles()
		if err != nil {
			return nil, err
		}
		return bottleEntriesOf(bottles), nil
	})

	command(bridge.OpLoginRecover, func(_ context.Context, request recoverRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.RecoverBottle(request.Index, request.Passcode)
	})

	query(bridge.OpAnisetteServer, func(context.Context) (anisetteResponse, error) {
		active, err := opened()
		if err != nil {
			return anisetteResponse{}, err
		}
		return anisetteResponse{URL: active.AnisetteServer(), DefaultList: vault.DefaultAnisetteList}, nil
	})

	command(bridge.OpAnisetteSetServer, func(_ context.Context, request anisetteRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		if err := active.SetAnisetteServer(request.URL); err != nil {
			return bridge.Fail(bridge.KindBadRequest, err.Error())
		}
		return nil
	})

	command(bridge.OpLoginFinish, func(_ context.Context, request secretRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.FinishSetup(request.Secret)
	})
}
