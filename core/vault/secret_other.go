//go:build !(linux && !android) && !windows

package vault

func systemSecret(string, bool) ([]byte, error) { return nil, errUnsupported }

func forgetSystemSecret(string) {}
