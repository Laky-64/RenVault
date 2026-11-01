package clipboard

import "errors"

var ErrUnsupported = errors.New("clipboard: not supported on this platform")
