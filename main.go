package main

import (
	"embed"
	"log"

	"github.com/Laky-64/RenVault/internal/appinfo"
	"github.com/Laky-64/RenVault/internal/icons"
	"github.com/Laky-64/RenVault/internal/vault"

	"github.com/wailsapp/wails/v3/pkg/application"
)

//go:embed all:frontend/dist
var assets embed.FS

func main() {
	iconService := icons.New()

	appleVault := vault.NewService()
	app := application.New(application.Options{
		Name:        appinfo.Name,
		Description: appinfo.Description,
		Services: []application.Service{
			application.NewService(appleVault),
			application.NewService(appinfo.NewService()),
		},
		Assets: application.AssetOptions{
			Handler:    application.AssetFileServerFS(assets),
			Middleware: application.ChainMiddleware(iconService.Middleware(), appleVault.Middleware()),
		},
		Linux: application.LinuxOptions{},
		Mac: application.MacOptions{
			ApplicationShouldTerminateAfterLastWindowClosed: true,
		},
	})

	app.Window.NewWithOptions(application.WebviewWindowOptions{
		Title:     appinfo.Name,
		Width:     1000,
		Height:    670,
		Frameless: true,
		URL:       "/",
		MinWidth:  350,
		MinHeight: 620,
	})

	err := app.Run()

	if err != nil {
		log.Fatal(err)
	}
}
