//go:build linux && !android

package agent

import (
	"context"
	"errors"
	"fmt"
	"sync"

	"github.com/godbus/dbus/v5"
	"github.com/godbus/dbus/v5/introspect"
	"github.com/godbus/dbus/v5/prop"
)

const (
	platformAdvertises = true
	platformReason     = "advertising is available"
)

func newAdvertiser() Advertiser { return NewBlueZAdvertiser() }

const (
	bluezService     = "org.bluez"
	advertisePath    = dbus.ObjectPath("/io/github/laky64/renvault/advertisement0")
	advertiseIface   = "org.bluez.LEAdvertisement1"
	advertiseManager = "org.bluez.LEAdvertisingManager1"
	cableServiceUUID = "0000fde2-0000-1000-8000-00805f9b34fb"
)

type release struct{}

func (release) Release() *dbus.Error { return nil }

type BlueZAdvertiser struct {
	mu         sync.Mutex
	conn       *dbus.Conn
	adapter    dbus.ObjectPath
	props      *prop.Properties
	registered bool
}

func NewBlueZAdvertiser() *BlueZAdvertiser {
	return &BlueZAdvertiser{}
}

func (b *BlueZAdvertiser) Start(ctx context.Context, advert []byte) error {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.registered {
		return errors.New("agent: already advertising")
	}

	conn, err := dbus.SystemBus()
	if err != nil {
		return fmt.Errorf("agent: system bus: %w", err)
	}
	adapter, err := findAdapter(conn)
	if err != nil {
		return err
	}

	table := map[string]map[string]*prop.Prop{
		advertiseIface: {
			"Type":         {Value: "broadcast", Writable: false, Emit: prop.EmitTrue},
			"ServiceUUIDs": {Value: []string{cableServiceUUID}, Writable: false, Emit: prop.EmitTrue},
			"ServiceData": {
				Value:    map[string]dbus.Variant{cableServiceUUID: dbus.MakeVariant(advert)},
				Writable: false,
				Emit:     prop.EmitTrue,
			},
		},
	}
	props, err := prop.Export(conn, advertisePath, table)
	if err != nil {
		return fmt.Errorf("agent: export advertisement properties: %w", err)
	}
	if err := conn.Export(release{}, advertisePath, advertiseIface); err != nil {
		return fmt.Errorf("agent: export advertisement: %w", err)
	}
	node := &introspect.Node{
		Name: string(advertisePath),
		Interfaces: []introspect.Interface{
			introspect.IntrospectData,
			prop.IntrospectData,
			{
				Name:       advertiseIface,
				Methods:    introspect.Methods(release{}),
				Properties: props.Introspection(advertiseIface),
			},
		},
	}
	if err := conn.Export(introspect.NewIntrospectable(node), advertisePath, "org.freedesktop.DBus.Introspectable"); err != nil {
		return fmt.Errorf("agent: export introspection: %w", err)
	}

	call := conn.Object(bluezService, adapter).CallWithContext(
		ctx, advertiseManager+".RegisterAdvertisement", 0, advertisePath, map[string]dbus.Variant{})
	if call.Err != nil {
		b.unexport(conn)
		return fmt.Errorf("agent: register advertisement: %w", call.Err)
	}

	b.conn, b.adapter, b.props, b.registered = conn, adapter, props, true
	return nil
}

func (b *BlueZAdvertiser) Stop() error {
	b.mu.Lock()
	defer b.mu.Unlock()
	if !b.registered {
		return nil
	}
	b.registered = false
	call := b.conn.Object(bluezService, b.adapter).Call(
		advertiseManager+".UnregisterAdvertisement", 0, advertisePath)
	b.unexport(b.conn)
	b.conn, b.props = nil, nil
	if call.Err != nil {
		return fmt.Errorf("agent: unregister advertisement: %w", call.Err)
	}
	return nil
}

func (b *BlueZAdvertiser) unexport(conn *dbus.Conn) {
	conn.Export(nil, advertisePath, advertiseIface)
	conn.Export(nil, advertisePath, "org.freedesktop.DBus.Introspectable")
	conn.Export(nil, advertisePath, "org.freedesktop.DBus.Properties")
}

func findAdapter(conn *dbus.Conn) (dbus.ObjectPath, error) {
	var objects map[dbus.ObjectPath]map[string]map[string]dbus.Variant
	err := conn.Object(bluezService, "/").Call(
		"org.freedesktop.DBus.ObjectManager.GetManagedObjects", 0).Store(&objects)
	if err != nil {
		return "", fmt.Errorf("agent: list bluez objects: %w", err)
	}
	for path, interfaces := range objects {
		if _, ok := interfaces[advertiseManager]; ok {
			return path, nil
		}
	}
	return "", errors.New("agent: no bluetooth adapter supports advertising")
}
