package main

import (
	"embed"
	"log"

	"github.com/Laky-64/RenVault/internal/icons"

	"github.com/wailsapp/wails/v3/pkg/application"
)

//go:embed all:frontend/dist
var assets embed.FS

func main() {
	iconService := icons.New()

	app := application.New(application.Options{
		Name:        "RenVault",
		Description: "An unofficial cross-platform Apple Passwords client for managing your credentials securely",
		Services: []application.Service{
			application.NewService(iconService),
		},
		Assets: application.AssetOptions{
			Handler:    application.AssetFileServerFS(assets),
			Middleware: iconService.Middleware(),
		},
		Linux: application.LinuxOptions{},
		Mac: application.MacOptions{
			ApplicationShouldTerminateAfterLastWindowClosed: true,
		},
	})

	app.Window.NewWithOptions(application.WebviewWindowOptions{
		Title:     "RenVault",
		Width:     1000,
		Height:    618,
		Frameless: true,
		URL:       "/",
	})

	err := app.Run()

	if err != nil {
		log.Fatal(err)
	}
}
