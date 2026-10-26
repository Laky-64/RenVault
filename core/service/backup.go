package service

import (
	"context"
	"errors"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/portal"
	"github.com/Laky-64/renvault-core/screen"
	"github.com/Laky-64/renvault-core/tray"
)

func installBackup() {
	handle(bridge.OpBackupExport, func(_ context.Context, request backupRequest) (bytesResponse, error) {
		active, err := unlocked()
		if err != nil {
			return bytesResponse{}, err
		}
		data, err := active.ExportBackup(request.Password)
		return bytesResponse{Data: data}, err
	})

	handle(bridge.OpBackupImport, func(_ context.Context, request backupRequest) (importResponse, error) {
		active, err := unlocked()
		if err != nil {
			return importResponse{}, err
		}
		result, err := active.ImportBackup(request.Data, request.Password)
		return importResponseOf(result), err
	})

	handle(bridge.OpImportCSV, func(_ context.Context, request backupRequest) (importResponse, error) {
		active, err := unlocked()
		if err != nil {
			return importResponse{}, err
		}
		result, err := active.ImportCSV(request.Data)
		return importResponseOf(result), err
	})

	handle(bridge.OpPickFile, func(ctx context.Context, request pickFileRequest) (bytesResponse, error) {
		data, err := portal.OpenFile(ctx, request.Title, request.Filter.portal())
		if errors.Is(err, portal.ErrUnavailable) {
			return bytesResponse{}, bridge.Fail(bridge.KindBadOp, err.Error())
		}
		return bytesResponse{Data: data}, err
	})

	command(bridge.OpTitleBar, func(_ context.Context, request titleBarRequest) error {
		err := screen.SetTitleBar(request.Dark, request.Caption, request.Text)
		if errors.Is(err, screen.ErrUnsupported) {
			return nil
		}
		return err
	})

	command(bridge.OpTray, func(_ context.Context, request trayRequest) error {
		err := tray.Show(request.config(), func(id string) {
			bridge.Emit(bridge.EventTray, trayEvent{Item: id})
		})
		if errors.Is(err, tray.ErrUnsupported) {
			return bridge.Fail(bridge.KindBadOp, err.Error())
		}
		return err
	})

	command(bridge.OpRaise, func(_ context.Context, request raiseRequest) error {
		token := tray.TakeActivationToken()
		if request.Token != "" {
			token = request.Token
		}
		err := screen.Raise(token)
		if errors.Is(err, screen.ErrUnsupported) {
			return bridge.Fail(bridge.KindBadOp, err.Error())
		}
		return err
	})

	trigger(bridge.OpTrayHide, func(context.Context) error {
		tray.Hide()
		return nil
	})

	command(bridge.OpSecure, func(_ context.Context, request secureRequest) error {
		err := screen.SetSecure(request.On)
		if errors.Is(err, screen.ErrUnsupported) {
			return bridge.Fail(bridge.KindBadOp, err.Error())
		}
		return err
	})

	handle(bridge.OpSaveFile, func(ctx context.Context, request saveFileRequest) (savedResponse, error) {
		saved, err := portal.SaveFile(ctx, request.Title, request.Name, request.Filter.portal(), request.Data)
		if errors.Is(err, portal.ErrUnavailable) {
			return savedResponse{}, bridge.Fail(bridge.KindBadOp, err.Error())
		}
		return savedResponse{Saved: saved}, err
	})
}
