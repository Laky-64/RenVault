package service

import (
	"context"
	"sync"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/vault"
)

var once sync.Once

func Install() {
	once.Do(func() {
		installVault()
		installItems()
		installLogin()
		installIcons()
	})
}

func installIcons() {
	handle(bridge.OpIconTile, func(_ context.Context, request domainRequest) (bytesResponse, error) {
		png, err := tiles.Tile(request.Domain)
		return bytesResponse{Data: png}, err
	})

	handle(bridge.OpChangePasswordAt, func(_ context.Context, request domainRequest) (secretResponse, error) {
		return secretResponse{Secret: vault.ChangePasswordURL(request.Domain)}, nil
	})
}
