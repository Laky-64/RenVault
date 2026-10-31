//go:build linux && !android

package tray

import (
	"bytes"
	"fmt"
	"image/color"
	"image/png"
	"os"
	"strings"
	"sync"

	"github.com/godbus/dbus/v5"
	"github.com/godbus/dbus/v5/introspect"
	"github.com/godbus/dbus/v5/prop"
)

const (
	itemIface    = "org.kde.StatusNotifierItem"
	itemPath     = dbus.ObjectPath("/StatusNotifierItem")
	menuIface    = "com.canonical.dbusmenu"
	menuPath     = dbus.ObjectPath("/MenuBar")
	watcherName  = "org.kde.StatusNotifierWatcher"
	watcherPath  = dbus.ObjectPath("/StatusNotifierWatcher")
	watcherIface = "org.kde.StatusNotifierWatcher"
	rootID       = 0
)

type pixmap struct {
	Width  int32
	Height int32
	Data   []byte
}

type tooltip struct {
	Icon     string
	Pixmaps  []pixmap
	Title    string
	Subtitle string
}

type layout struct {
	ID         int32
	Properties map[string]dbus.Variant
	Children   []dbus.Variant
}

type itemProperties struct {
	ID         int32
	Properties map[string]dbus.Variant
}

var (
	mu         sync.Mutex
	conn       *dbus.Conn
	props      *prop.Properties
	current    Config
	report     func(id string)
	revision   uint32
	activation string
)

type item struct{}

func (item) Activate(int32, int32) *dbus.Error {
	emit(Activated)
	return nil
}

func (item) SecondaryActivate(int32, int32) *dbus.Error {
	emit(Activated)
	return nil
}

func (item) ContextMenu(int32, int32) *dbus.Error { return nil }

func (item) ProvideXdgActivationToken(token string) *dbus.Error {
	mu.Lock()
	activation = token
	mu.Unlock()
	return nil
}

func (item) Scroll(int32, string) *dbus.Error { return nil }

type menu struct{}

func (menu) GetLayout(parent int32, _ int32, _ []string) (uint32, layout, *dbus.Error) {
	mu.Lock()
	defer mu.Unlock()
	root := layout{ID: rootID, Properties: map[string]dbus.Variant{
		"children-display": dbus.MakeVariant("submenu"),
	}}
	for i, entry := range current.Items {
		root.Children = append(root.Children, dbus.MakeVariant(layout{
			ID:         int32(i + 1),
			Properties: entryProperties(entry),
			Children:   []dbus.Variant{},
		}))
	}
	if parent != rootID {
		for _, child := range root.Children {
			if child.Value().(layout).ID == parent {
				return revision, child.Value().(layout), nil
			}
		}
	}
	return revision, root, nil
}

func (menu) GetGroupProperties(ids []int32, _ []string) ([]itemProperties, *dbus.Error) {
	mu.Lock()
	defer mu.Unlock()
	out := []itemProperties{}
	for _, id := range ids {
		if id >= 1 && int(id) <= len(current.Items) {
			out = append(out, itemProperties{ID: id, Properties: entryProperties(current.Items[id-1])})
		}
	}
	return out, nil
}

func (menu) GetProperty(id int32, name string) (dbus.Variant, *dbus.Error) {
	mu.Lock()
	defer mu.Unlock()
	if id >= 1 && int(id) <= len(current.Items) {
		if value, ok := entryProperties(current.Items[id-1])[name]; ok {
			return value, nil
		}
	}
	return dbus.MakeVariant(""), nil
}

func (menu) Event(id int32, event string, _ dbus.Variant, _ uint32) *dbus.Error {
	if event == "clicked" {
		clicked(id)
	}
	return nil
}

func (menu) EventGroup(events []struct {
	ID        int32
	Event     string
	Data      dbus.Variant
	Timestamp uint32
}) ([]int32, *dbus.Error) {
	for _, event := range events {
		if event.Event == "clicked" {
			clicked(event.ID)
		}
	}
	return []int32{}, nil
}

func (menu) AboutToShow(int32) (bool, *dbus.Error) { return false, nil }

func (menu) AboutToShowGroup([]int32) ([]int32, []int32, *dbus.Error) {
	return []int32{}, []int32{}, nil
}

func entryProperties(entry Item) map[string]dbus.Variant {
	return map[string]dbus.Variant{
		"label":   dbus.MakeVariant(strings.ReplaceAll(entry.Label, "_", "__")),
		"enabled": dbus.MakeVariant(true),
		"visible": dbus.MakeVariant(true),
	}
}

func clicked(id int32) {
	mu.Lock()
	items := current.Items
	mu.Unlock()
	if id >= 1 && int(id) <= len(items) {
		emit(items[id-1].ID)
	}
}

func emit(id string) {
	mu.Lock()
	callback := report
	mu.Unlock()
	if callback != nil {
		go callback(id)
	}
}

func GiveActivationToken(token string) {
	mu.Lock()
	activation = token
	mu.Unlock()
}

func TakeActivationToken() string {
	mu.Lock()
	defer mu.Unlock()
	token := activation
	activation = ""
	return token
}

func Show(config Config, onEvent func(id string)) error {
	bus, name, err := start(config, onEvent)
	if err != nil || bus == nil {
		return err
	}
	if err := bus.Object(watcherName, watcherPath).Call(watcherIface+".RegisterStatusNotifierItem", 0, name).Err; err != nil {
		Hide()
		return ErrUnsupported
	}
	go reregister(bus, name)
	return nil
}

func start(config Config, onEvent func(id string)) (*dbus.Conn, string, error) {
	mu.Lock()
	defer mu.Unlock()
	current = config
	report = onEvent
	if conn != nil {
		refresh()
		return nil, "", nil
	}

	bus, err := dbus.ConnectSessionBus()
	if err != nil {
		return nil, "", ErrUnsupported
	}
	name := fmt.Sprintf("org.kde.StatusNotifierItem-%d-1", os.Getpid())
	if err := export(bus); err != nil {
		bus.Close()
		return nil, "", err
	}
	if reply, err := bus.RequestName(name, dbus.NameFlagDoNotQueue); err != nil || reply != dbus.RequestNameReplyPrimaryOwner {
		bus.Close()
		return nil, "", ErrUnsupported
	}
	conn = bus
	return bus, name, nil
}

func Hide() {
	mu.Lock()
	defer mu.Unlock()
	if conn != nil {
		conn.Close()
		conn = nil
		props = nil
	}
}

func export(bus *dbus.Conn) error {
	if err := bus.Export(item{}, itemPath, itemIface); err != nil {
		return err
	}
	if err := bus.Export(menu{}, menuPath, menuIface); err != nil {
		return err
	}

	var err error
	props, err = prop.Export(bus, itemPath, prop.Map{itemIface: {
		"Category":   {Value: "ApplicationStatus"},
		"Id":         {Value: "renvault"},
		"Title":      {Value: current.Tooltip, Emit: prop.EmitTrue},
		"Status":     {Value: "Active", Emit: prop.EmitTrue},
		"IconName":   {Value: ""},
		"IconPixmap": {Value: pixmapsOf(current.Icons), Emit: prop.EmitTrue},
		"ToolTip":    {Value: tooltip{Title: current.Tooltip, Pixmaps: []pixmap{}}, Emit: prop.EmitTrue},
		"ItemIsMenu": {Value: false},
		"Menu":       {Value: menuPath},
	}})
	if err != nil {
		return err
	}
	menuProps, err := prop.Export(bus, menuPath, prop.Map{menuIface: {
		"Version":       {Value: uint32(3)},
		"TextDirection": {Value: "ltr"},
		"Status":        {Value: "normal"},
		"IconThemePath": {Value: []string{}},
	}})
	if err != nil {
		return err
	}

	if err := bus.Export(introspect.NewIntrospectable(&introspect.Node{
		Name: string(itemPath),
		Interfaces: []introspect.Interface{
			introspect.IntrospectData,
			prop.IntrospectData,
			{Name: itemIface, Methods: introspect.Methods(item{}), Properties: props.Introspection(itemIface)},
		},
	}), itemPath, "org.freedesktop.DBus.Introspectable"); err != nil {
		return err
	}
	return bus.Export(introspect.NewIntrospectable(&introspect.Node{
		Name: string(menuPath),
		Interfaces: []introspect.Interface{
			introspect.IntrospectData,
			prop.IntrospectData,
			{Name: menuIface, Methods: introspect.Methods(menu{}), Properties: menuProps.Introspection(menuIface)},
		},
	}), menuPath, "org.freedesktop.DBus.Introspectable")
}

func refresh() {
	props.SetMust(itemIface, "Title", current.Tooltip)
	props.SetMust(itemIface, "ToolTip", tooltip{Title: current.Tooltip, Pixmaps: []pixmap{}})
	props.SetMust(itemIface, "IconPixmap", pixmapsOf(current.Icons))
	conn.Emit(itemPath, itemIface+".NewTitle")
	conn.Emit(itemPath, itemIface+".NewIcon")
	conn.Emit(itemPath, itemIface+".NewToolTip")
	revision++
	conn.Emit(menuPath, menuIface+".LayoutUpdated", revision, int32(rootID))
}

func reregister(bus *dbus.Conn, name string) {
	if err := bus.AddMatchSignal(
		dbus.WithMatchInterface("org.freedesktop.DBus"),
		dbus.WithMatchMember("NameOwnerChanged"),
		dbus.WithMatchArg(0, watcherName),
	); err != nil {
		return
	}
	signals := make(chan *dbus.Signal, 4)
	bus.Signal(signals)
	for signal := range signals {
		if len(signal.Body) < 3 {
			continue
		}
		if owner, _ := signal.Body[2].(string); owner != "" {
			bus.Object(watcherName, watcherPath).Call(watcherIface+".RegisterStatusNotifierItem", 0, name)
		}
	}
}

func pixmapsOf(icons [][]byte) []pixmap {
	out := []pixmap{}
	for _, data := range icons {
		if pixmap, ok := pixmapOf(data); ok {
			out = append(out, pixmap)
		}
	}
	return out
}

func pixmapOf(data []byte) (pixmap, bool) {
	decoded, err := png.Decode(bytes.NewReader(data))
	if err != nil {
		return pixmap{}, false
	}
	bounds := decoded.Bounds()
	argb := make([]byte, 0, bounds.Dx()*bounds.Dy()*4)
	for y := bounds.Min.Y; y < bounds.Max.Y; y++ {
		for x := bounds.Min.X; x < bounds.Max.X; x++ {
			c := color.NRGBAModel.Convert(decoded.At(x, y)).(color.NRGBA)
			argb = append(argb, c.A, c.R, c.G, c.B)
		}
	}
	return pixmap{Width: int32(bounds.Dx()), Height: int32(bounds.Dy()), Data: argb}, true
}
