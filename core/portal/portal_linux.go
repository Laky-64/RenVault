//go:build linux && !android

package portal

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"io"
	"net/url"
	"os"
	"strings"

	"github.com/godbus/dbus/v5"
)

const (
	portalService = "org.freedesktop.portal.Desktop"
	portalPath    = dbus.ObjectPath("/org/freedesktop/portal/desktop")
	chooserIface  = "org.freedesktop.portal.FileChooser"
	requestIface  = "org.freedesktop.portal.Request"
)

type rule struct {
	Kind    uint32
	Pattern string
}

type filter struct {
	Name  string
	Rules []rule
}

func OpenFile(ctx context.Context, title string, f Filter) ([]byte, error) {
	conn, err := dbus.ConnectSessionBus()
	if err != nil {
		return nil, ErrUnavailable
	}
	defer conn.Close()

	token := make([]byte, 8)
	if _, err := rand.Read(token); err != nil {
		return nil, err
	}
	handle := "renvault_" + hex.EncodeToString(token)
	sender := strings.ReplaceAll(strings.TrimPrefix(conn.Names()[0], ":"), ".", "_")
	request := dbus.ObjectPath("/org/freedesktop/portal/desktop/request/" + sender + "/" + handle)

	if err := conn.AddMatchSignal(
		dbus.WithMatchObjectPath(request),
		dbus.WithMatchInterface(requestIface),
		dbus.WithMatchMember("Response"),
	); err != nil {
		return nil, ErrUnavailable
	}
	signals := make(chan *dbus.Signal, 4)
	conn.Signal(signals)

	rules := make([]rule, 0, len(f.Patterns)+len(f.Types))
	for _, p := range f.Patterns {
		rules = append(rules, rule{Kind: 0, Pattern: p})
	}
	for _, t := range f.Types {
		rules = append(rules, rule{Kind: 1, Pattern: t})
	}
	options := map[string]dbus.Variant{
		"handle_token": dbus.MakeVariant(handle),
		"modal":        dbus.MakeVariant(true),
	}
	if len(rules) > 0 {
		filters := []filter{{Name: f.Name, Rules: rules}}
		options["filters"] = dbus.MakeVariant(filters)
		options["current_filter"] = dbus.MakeVariant(filters[0])
	}

	chooser := conn.Object(portalService, portalPath)
	var opened dbus.ObjectPath
	if err := chooser.CallWithContext(ctx, chooserIface+".OpenFile", 0, "", title, options).Store(&opened); err != nil {
		return nil, ErrUnavailable
	}

	for {
		select {
		case <-ctx.Done():
			conn.Object(portalService, opened).Call(requestIface+".Close", 0)
			return nil, ctx.Err()
		case sig, ok := <-signals:
			if !ok {
				return nil, ErrUnavailable
			}
			if sig.Path != opened && sig.Path != request {
				continue
			}
			return readResponse(sig)
		}
	}
}

func readResponse(sig *dbus.Signal) ([]byte, error) {
	if len(sig.Body) < 2 {
		return nil, ErrUnavailable
	}
	if code, _ := sig.Body[0].(uint32); code != 0 {
		return nil, nil
	}
	results, _ := sig.Body[1].(map[string]dbus.Variant)
	uris, _ := results["uris"].Value().([]string)
	if len(uris) == 0 {
		return nil, nil
	}
	target, err := url.Parse(uris[0])
	if err != nil || target.Scheme != "file" {
		return nil, errors.New("portal: unsupported file location")
	}
	file, err := os.Open(target.Path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	data, err := io.ReadAll(io.LimitReader(file, maxImageBytes+1))
	if err != nil {
		return nil, err
	}
	if len(data) > maxImageBytes {
		return nil, errors.New("portal: file too large")
	}
	return data, nil
}
