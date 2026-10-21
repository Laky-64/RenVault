//go:build windows

package agent

import "context"

const (
	platformAdvertises = false
	platformReason     = "advertising on Windows is not implemented yet"
)

func newAdvertiser() Advertiser { return unsupportedAdvertiser{} }

type unsupportedAdvertiser struct{}

func (unsupportedAdvertiser) Start(context.Context, []byte) error { return ErrUnsupported }

func (unsupportedAdvertiser) Stop() error { return nil }
