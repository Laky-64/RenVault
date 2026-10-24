//go:build !windows && !(darwin && !ios)

package screen

func Supported() bool { return false }

func SetSecure(bool) error { return ErrUnsupported }
