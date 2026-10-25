//go:build !windows

package screen

func SetTitleBar(bool, uint32, uint32) error { return ErrUnsupported }
