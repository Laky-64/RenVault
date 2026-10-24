//go:build (!darwin && !windows) || ios

package vault

func platformUnlockers() []Unlocker { return nil }
