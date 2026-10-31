//go:build !linux || android

package notify

func Show(string, string, func(token string)) (uint32, error) { return 0, ErrUnsupported }

func Close(uint32) {}
