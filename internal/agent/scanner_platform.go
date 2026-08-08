//go:build !hybriddev && !android

package agent

func defaultScanner() Scanner { return nil }
