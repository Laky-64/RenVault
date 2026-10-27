package tray

import "errors"

var ErrUnsupported = errors.New("tray: unsupported")

const Activated = ""

type Item struct {
	ID    string
	Label string
}

type Config struct {
	Tooltip      string
	Icons        [][]byte
	IconsOnLight [][]byte
	Items        []Item
}
