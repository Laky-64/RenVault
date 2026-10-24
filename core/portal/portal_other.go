//go:build !linux || android

package portal

import "context"

func OpenFile(context.Context, string, Filter) ([]byte, error) {
	return nil, ErrUnavailable
}

func SaveFile(context.Context, string, string, Filter, []byte) (bool, error) {
	return false, ErrUnavailable
}
