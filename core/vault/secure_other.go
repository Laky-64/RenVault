//go:build !unix

package vault

func lockMemory(b []byte) error { return nil }

func disableCoreDump() error { return nil }
