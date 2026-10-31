//go:build (!linux && !windows) || android

package tray

func Show(Config, func(id string)) error {
	return ErrUnsupported
}

func Hide() {}

func GiveActivationToken(string) {}

func TakeActivationToken() string {
	return ""
}
