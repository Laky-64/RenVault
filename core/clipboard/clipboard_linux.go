//go:build linux && !android

package clipboard

import (
	"errors"
	"sync"
	"time"

	"github.com/jezek/xgb"
	"github.com/jezek/xgb/xproto"
)

const maxText = 64 << 10

var errTooLong = errors.New("clipboard: text too long")

type owner struct {
	conn    *xgb.Conn
	window  xproto.Window
	atoms   map[string]xproto.Atom
	text    []byte
	secret  bool
	owned   bool
	version uint64
}

var (
	mu    sync.Mutex
	state *owner
)

func Set(text string, secret bool, clearAfter time.Duration) error {
	if len(text) > maxText {
		return errTooLong
	}
	mu.Lock()
	defer mu.Unlock()
	if state == nil {
		o, err := connect()
		if err != nil {
			return ErrUnsupported
		}
		state = o
		go state.serve()
	}
	state.text = []byte(text)
	state.secret = secret
	state.version++
	state.owned = true
	xproto.SetSelectionOwner(state.conn, state.window, state.atoms["CLIPBOARD"], xproto.TimeCurrentTime)
	state.conn.Sync()
	if clearAfter > 0 {
		version := state.version
		time.AfterFunc(clearAfter, func() { clear(version) })
	}
	return nil
}

func clear(version uint64) {
	mu.Lock()
	defer mu.Unlock()
	if state == nil || state.version != version || !state.owned {
		return
	}
	state.text = nil
	state.owned = false
	xproto.SetSelectionOwner(state.conn, xproto.WindowNone, state.atoms["CLIPBOARD"], xproto.TimeCurrentTime)
	state.conn.Sync()
}

func connect() (*owner, error) {
	conn, err := xgb.NewConn()
	if err != nil {
		return nil, err
	}
	screen := xproto.Setup(conn).DefaultScreen(conn)
	window, err := xproto.NewWindowId(conn)
	if err != nil {
		conn.Close()
		return nil, err
	}
	xproto.CreateWindow(conn, 0, window, screen.Root, 0, 0, 1, 1, 0, xproto.WindowClassInputOnly, screen.RootVisual, 0, nil)
	o := &owner{conn: conn, window: window, atoms: map[string]xproto.Atom{}}
	for _, name := range []string{"CLIPBOARD", "TARGETS", "UTF8_STRING", "STRING", "TEXT", "text/plain;charset=utf-8", "text/plain", "x-kde-passwordManagerHint", "ATOM"} {
		reply, err := xproto.InternAtom(conn, false, uint16(len(name)), name).Reply()
		if err != nil {
			conn.Close()
			return nil, err
		}
		o.atoms[name] = reply.Atom
	}
	return o, nil
}

func (o *owner) serve() {
	for {
		event, err := o.conn.WaitForEvent()
		if event == nil && err == nil {
			return
		}
		switch e := event.(type) {
		case xproto.SelectionRequestEvent:
			o.answer(e)
		case xproto.SelectionClearEvent:
			mu.Lock()
			o.owned = false
			o.text = nil
			mu.Unlock()
		}
	}
}

func (o *owner) answer(e xproto.SelectionRequestEvent) {
	mu.Lock()
	text, secret, owned := o.text, o.secret, o.owned
	mu.Unlock()

	property := e.Property
	if property == xproto.AtomNone {
		property = e.Target
	}
	textTargets := []xproto.Atom{o.atoms["UTF8_STRING"], o.atoms["STRING"], o.atoms["TEXT"], o.atoms["text/plain;charset=utf-8"], o.atoms["text/plain"]}
	served := owned && e.Selection == o.atoms["CLIPBOARD"]
	switch {
	case !served:
		property = xproto.AtomNone
	case e.Target == o.atoms["TARGETS"]:
		targets := append([]xproto.Atom{o.atoms["TARGETS"]}, textTargets...)
		if secret {
			targets = append(targets, o.atoms["x-kde-passwordManagerHint"])
		}
		data := make([]byte, 4*len(targets))
		for i, atom := range targets {
			xgb.Put32(data[i*4:], uint32(atom))
		}
		xproto.ChangeProperty(o.conn, xproto.PropModeReplace, e.Requestor, property, o.atoms["ATOM"], 32, uint32(len(targets)), data)
	case e.Target == o.atoms["x-kde-passwordManagerHint"] && secret:
		value := []byte("secret")
		xproto.ChangeProperty(o.conn, xproto.PropModeReplace, e.Requestor, property, xproto.AtomString, 8, uint32(len(value)), value)
	case contains(textTargets, e.Target):
		kind := e.Target
		if kind == o.atoms["TEXT"] {
			kind = o.atoms["UTF8_STRING"]
		}
		xproto.ChangeProperty(o.conn, xproto.PropModeReplace, e.Requestor, property, kind, 8, uint32(len(text)), text)
	default:
		property = xproto.AtomNone
	}

	notify := xproto.SelectionNotifyEvent{
		Time:      e.Time,
		Requestor: e.Requestor,
		Selection: e.Selection,
		Target:    e.Target,
		Property:  property,
	}
	xproto.SendEvent(o.conn, false, e.Requestor, 0, string(notify.Bytes()))
	o.conn.Sync()
}

func contains(list []xproto.Atom, atom xproto.Atom) bool {
	for _, a := range list {
		if a == atom {
			return true
		}
	}
	return false
}
