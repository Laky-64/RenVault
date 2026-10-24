//go:build !darwin && !windows

package vault

func platformUnlockers() []Unlocker { return nil }
