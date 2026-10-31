//go:build linux && !android

package notify

import (
	"sync"

	"github.com/godbus/dbus/v5"
)

const (
	service    = "org.freedesktop.Notifications"
	path       = "/org/freedesktop/Notifications"
	iface      = "org.freedesktop.Notifications"
	expireTime = 60_000
)

var (
	mu       sync.Mutex
	conn     *dbus.Conn
	tokens   = map[uint32]string{}
	handlers = map[uint32]func(token string){}
)

func connect() (*dbus.Conn, error) {
	if conn != nil {
		return conn, nil
	}
	bus, err := dbus.ConnectSessionBus()
	if err != nil {
		return nil, ErrUnsupported
	}
	for _, member := range []string{"ActionInvoked", "ActivationToken", "NotificationClosed"} {
		if err := bus.AddMatchSignal(dbus.WithMatchInterface(iface), dbus.WithMatchMember(member)); err != nil {
			bus.Close()
			return nil, err
		}
	}
	signals := make(chan *dbus.Signal, 16)
	bus.Signal(signals)
	go listen(signals)
	conn = bus
	return conn, nil
}

func listen(signals chan *dbus.Signal) {
	for signal := range signals {
		if len(signal.Body) < 2 {
			continue
		}
		id, ok := signal.Body[0].(uint32)
		if !ok {
			continue
		}
		mu.Lock()
		switch signal.Name {
		case iface + ".ActivationToken":
			if token, ok := signal.Body[1].(string); ok {
				tokens[id] = token
			}
			mu.Unlock()
		case iface + ".ActionInvoked":
			handler, token := handlers[id], tokens[id]
			delete(handlers, id)
			delete(tokens, id)
			mu.Unlock()
			if handler != nil {
				go handler(token)
			}
		default:
			delete(handlers, id)
			delete(tokens, id)
			mu.Unlock()
		}
	}
}

func Show(title, body string, onOpen func(token string)) (uint32, error) {
	mu.Lock()
	defer mu.Unlock()
	bus, err := connect()
	if err != nil {
		return 0, err
	}
	hints := map[string]dbus.Variant{
		"urgency":       dbus.MakeVariant(byte(1)),
		"desktop-entry": dbus.MakeVariant("io.lauren.renvault"),
	}
	var id uint32
	err = bus.Object(service, path).Call(iface+".Notify", 0,
		"RenVault", uint32(0), "", title, body, []string{"default", title}, hints, int32(expireTime),
	).Store(&id)
	if err != nil {
		return 0, err
	}
	handlers[id] = onOpen
	return id, nil
}

func Close(id uint32) {
	mu.Lock()
	defer mu.Unlock()
	delete(handlers, id)
	delete(tokens, id)
	if conn == nil {
		return
	}
	conn.Object(service, path).Call(iface+".CloseNotification", 0, id)
}
