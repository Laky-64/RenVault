//go:build linux && !android

package vault

import (
	"github.com/godbus/dbus/v5"
)

const (
	secretsService    = "org.freedesktop.secrets"
	secretsPath       = dbus.ObjectPath("/org/freedesktop/secrets")
	secretsCollection = dbus.ObjectPath("/org/freedesktop/secrets/aliases/default")
	secretsPrefix     = "org.freedesktop.Secret."
	noPrompt          = dbus.ObjectPath("/")
)

type secretValue struct {
	Session     dbus.ObjectPath
	Params      []byte
	Value       []byte
	ContentType string
}

func secretAttributes(base string) map[string]string {
	return map[string]string{"application": "io.lauren.renvault", "purpose": "device-secret", "base": base}
}

func withSecrets(fn func(conn *dbus.Conn, session dbus.ObjectPath) error) error {
	conn, err := dbus.ConnectSessionBus()
	if err != nil {
		return err
	}
	defer conn.Close()
	var (
		output  dbus.Variant
		session dbus.ObjectPath
	)
	service := conn.Object(secretsService, secretsPath)
	if err := service.Call(secretsPrefix+"Service.OpenSession", 0, "plain", dbus.MakeVariant("")).Store(&output, &session); err != nil {
		return err
	}
	defer conn.Object(secretsService, session).Call(secretsPrefix+"Session.Close", 0)
	return fn(conn, session)
}

func systemSecret(base string, create bool) ([]byte, error) {
	var secret []byte
	err := withSecrets(func(conn *dbus.Conn, session dbus.ObjectPath) error {
		var unlocked, locked []dbus.ObjectPath
		service := conn.Object(secretsService, secretsPath)
		if err := service.Call(secretsPrefix+"Service.SearchItems", 0, secretAttributes(base)).Store(&unlocked, &locked); err != nil {
			return err
		}
		if len(unlocked) > 0 {
			var value secretValue
			if err := conn.Object(secretsService, unlocked[0]).Call(secretsPrefix+"Item.GetSecret", 0, session).Store(&value); err != nil {
				return err
			}
			if len(value.Value) != secretSize {
				return errNoSecret
			}
			secret = value.Value
			return nil
		}
		if len(locked) > 0 || !create {
			return errNoSecret
		}
		fresh, err := newSecret()
		if err != nil {
			return err
		}
		properties := map[string]dbus.Variant{
			secretsPrefix + "Item.Label":      dbus.MakeVariant("RenVault device key"),
			secretsPrefix + "Item.Attributes": dbus.MakeVariant(secretAttributes(base)),
		}
		value := secretValue{Session: session, Params: []byte{}, Value: fresh, ContentType: "application/octet-stream"}
		var item, prompt dbus.ObjectPath
		if err := conn.Object(secretsService, secretsCollection).Call(secretsPrefix+"Collection.CreateItem", 0, properties, value, true).Store(&item, &prompt); err != nil {
			return err
		}
		if prompt != noPrompt {
			return errNoSecret
		}
		secret = fresh
		return nil
	})
	return secret, err
}

func forgetSystemSecret(base string) {
	_ = withSecrets(func(conn *dbus.Conn, _ dbus.ObjectPath) error {
		var unlocked, locked []dbus.ObjectPath
		if err := conn.Object(secretsService, secretsPath).Call(secretsPrefix+"Service.SearchItems", 0, secretAttributes(base)).Store(&unlocked, &locked); err != nil {
			return err
		}
		for _, item := range unlocked {
			var prompt dbus.ObjectPath
			_ = conn.Object(secretsService, item).Call(secretsPrefix+"Item.Delete", 0).Store(&prompt)
		}
		return nil
	})
}
