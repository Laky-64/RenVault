//go:build !linux && !darwin && !windows && !android

package agent

import "context"

const (
	platformAdvertises = false
	platformReason     = "advertising is not implemented on this platform yet"
)

func newAdvertiser() Advertiser { return unsupportedAdvertiser{} }

type unsupportedAdvertiser struct{}

func (unsupportedAdvertiser) Start(context.Context, []byte) error { return ErrUnsupported }

func (unsupportedAdvertiser) Stop() error { return nil }
