//go:build !linux

package ctaphid

import "errors"

type UHID struct{}

func Open(string) (*UHID, error) {
	return nil, errors.New("ctaphid: virtual hid devices are only supported on linux")
}

func Available() bool { return false }
