package service

import (
	"context"
	"errors"
	"sync"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/portal"
	"github.com/Laky-64/renvault-core/vault"
)

var once sync.Once

func Install() {
	once.Do(func() {
		installVault()
		installItems()
		installLogin()
		installDevices()
		installBackup()
		installIcons()
	})
}

func installIcons() {
	handle(bridge.OpIconTile, func(_ context.Context, request domainRequest) (bytesResponse, error) {
		png, err := tiles.Tile(request.Domain)
		return bytesResponse{Data: png}, err
	})

	handle(bridge.OpIconImage, func(_ context.Context, request urlRequest) (bytesResponse, error) {
		png, err := tiles.Image(request.URL)
		return bytesResponse{Data: png}, err
	})

	handle(bridge.OpPickImage, func(ctx context.Context, request pickRequest) (bytesResponse, error) {
		data, err := portal.OpenFile(ctx, request.Title, portal.Filter{
			Name:     request.Filter,
			Patterns: []string{"*.png", "*.jpg", "*.jpeg", "*.PNG", "*.JPG", "*.JPEG"},
			Types:    []string{"image/png", "image/jpeg"},
		})
		if errors.Is(err, portal.ErrUnavailable) {
			return bytesResponse{}, bridge.Fail(bridge.KindBadOp, err.Error())
		}
		return bytesResponse{Data: data}, err
	})

	handle(bridge.OpChangePasswordAt, func(_ context.Context, request domainRequest) (secretResponse, error) {
		return secretResponse{Secret: vault.ChangePasswordURL(request.Domain)}, nil
	})
}
