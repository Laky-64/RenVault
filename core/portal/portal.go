package portal

import "errors"

var ErrUnavailable = errors.New("portal: file chooser unavailable")

const maxImageBytes = 32 << 20

type Filter struct {
	Name     string
	Patterns []string
	Types    []string
}
