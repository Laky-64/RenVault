//go:build !linux || android

package clipboard

import "time"

func Set(string, bool, time.Duration) error { return ErrUnsupported }
