//go:build !linux

package ctaphid

import (
	"context"
	"errors"
)

var errUnsupported = errors.New("ctaphid: virtual hid devices are only supported on linux")

type UHID struct{}

func Open(string) (*UHID, error) {
	return nil, errUnsupported
}

func (*UHID) ReadReport(context.Context) ([]byte, error) {
	return nil, errUnsupported
}

func (*UHID) WriteReport(context.Context, []byte) error {
	return errUnsupported
}

func (*UHID) Close() error { return nil }

func Available() bool { return false }
