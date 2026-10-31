//go:build !linux || android

package localkey

import "errors"

func Grant() error {
	return errors.New("localkey: not supported on this platform")
}
