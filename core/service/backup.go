package service

import (
	"context"
	"errors"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/portal"
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

	handle(bridge.OpSaveFile, func(ctx context.Context, request saveFileRequest) (savedResponse, error) {
		saved, err := portal.SaveFile(ctx, request.Title, request.Name, request.Filter.portal(), request.Data)
		if errors.Is(err, portal.ErrUnavailable) {
			return savedResponse{}, bridge.Fail(bridge.KindBadOp, err.Error())
		}
		return savedResponse{Saved: saved}, err
	})
}
