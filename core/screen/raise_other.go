//go:build !linux || android

package screen

func Raise(string) error {
	return ErrUnsupported
}
