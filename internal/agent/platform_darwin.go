//go:build darwin

package agent

import "context"

const (
	platformAdvertises = false
	platformReason     = "macOS cannot broadcast the Bluetooth advert this needs: CoreBluetooth only accepts a local name and service UUIDs when advertising, never service data"
)

func newAdvertiser() Advertiser { return unsupportedAdvertiser{} }

type unsupportedAdvertiser struct{}

func (unsupportedAdvertiser) Start(context.Context, []byte) error { return ErrUnsupported }

func (unsupportedAdvertiser) Stop() error { return nil }
