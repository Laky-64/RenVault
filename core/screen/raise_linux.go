//go:build linux && !android

package screen

import (
	"os"
	"strconv"
	"strings"

	"github.com/jezek/xgb"
	"github.com/jezek/xgb/xproto"
)

const sourcePager = 2

func Raise(token string) error {
	conn, err := xgb.NewConn()
	if err != nil {
		return ErrUnsupported
	}
	defer conn.Close()

	root := xproto.Setup(conn).DefaultScreen(conn).Root
	clients, err := atom(conn, "_NET_CLIENT_LIST")
	if err != nil {
		return err
	}
	pidAtom, err := atom(conn, "_NET_WM_PID")
	if err != nil {
		return err
	}
	active, err := atom(conn, "_NET_ACTIVE_WINDOW")
	if err != nil {
		return err
	}
	startup, err := atom(conn, "_NET_STARTUP_ID")
	if err != nil {
		return err
	}
	utf8, err := atom(conn, "UTF8_STRING")
	if err != nil {
		return err
	}

	list, err := xproto.GetProperty(conn, false, root, clients, xproto.AtomWindow, 0, 1024).Reply()
	if err != nil || list.Format != 32 {
		return ErrUnsupported
	}
	pid := uint32(os.Getpid())
	for i := 0; i+4 <= len(list.Value); i += 4 {
		window := xproto.Window(xgb.Get32(list.Value[i:]))
		owner, err := xproto.GetProperty(conn, false, window, pidAtom, xproto.AtomCardinal, 0, 1).Reply()
		if err != nil || owner.Format != 32 || len(owner.Value) < 4 || xgb.Get32(owner.Value) != pid {
			continue
		}
		if token != "" {
			xproto.ChangeProperty(conn, xproto.PropModeReplace, window, startup, utf8, 8, uint32(len(token)), []byte(token))
		}
		event := xproto.ClientMessageEvent{
			Format: 32,
			Window: window,
			Type:   active,
			Data:   xproto.ClientMessageDataUnionData32New([]uint32{sourcePager, tokenTime(token), 0, 0, 0}),
		}
		xproto.SendEvent(conn, false, root,
			xproto.EventMaskSubstructureRedirect|xproto.EventMaskSubstructureNotify, string(event.Bytes()))
	}
	conn.Sync()
	return nil
}

func atom(conn *xgb.Conn, name string) (xproto.Atom, error) {
	reply, err := xproto.InternAtom(conn, false, uint16(len(name)), name).Reply()
	if err != nil {
		return 0, err
	}
	return reply.Atom, nil
}

func tokenTime(token string) uint32 {
	index := strings.LastIndex(token, "_TIME")
	if index < 0 {
		return xproto.TimeCurrentTime
	}
	value, err := strconv.ParseUint(token[index+len("_TIME"):], 10, 32)
	if err != nil {
		return xproto.TimeCurrentTime
	}
	return uint32(value)
}
