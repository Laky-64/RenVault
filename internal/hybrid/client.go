package hybrid

import (
	"context"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"

	"github.com/coder/websocket"
)

const (
	subprotocol      = "fido.cable"
	routingIDHeader  = "X-caBLE-Routing-ID"
	maxMessageLength = 1 << 20
)

type socket struct {
	conn *websocket.Conn
}

func (s *socket) Read(ctx context.Context) ([]byte, error) {
	kind, data, err := s.conn.Read(ctx)
	if err != nil {
		return nil, fmt.Errorf("hybrid: read tunnel: %w", err)
	}
	if kind != websocket.MessageBinary {
		return nil, fmt.Errorf("hybrid: tunnel sent a %v message", kind)
	}
	return data, nil
}

func (s *socket) Write(ctx context.Context, data []byte) error {
	if err := s.conn.Write(ctx, websocket.MessageBinary, data); err != nil {
		return fmt.Errorf("hybrid: write tunnel: %w", err)
	}
	return nil
}

func (s *socket) Close() error {
	return s.conn.Close(websocket.StatusNormalClosure, "")
}

func Dial(ctx context.Context, url string) (Conn, []byte, error) {
	conn, resp, err := websocket.Dial(ctx, url, &websocket.DialOptions{
		Subprotocols: []string{subprotocol},
	})
	if err != nil {
		return nil, nil, fmt.Errorf("hybrid: dial tunnel: %w", err)
	}
	conn.SetReadLimit(maxMessageLength)
	if conn.Subprotocol() != subprotocol {
		conn.Close(websocket.StatusProtocolError, "")
		return nil, nil, fmt.Errorf("hybrid: tunnel selected subprotocol %q", conn.Subprotocol())
	}
	routingID, err := routingIDOf(resp.Header.Values(routingIDHeader))
	if err != nil {
		conn.Close(websocket.StatusProtocolError, "")
		return nil, nil, err
	}
	return &socket{conn: conn}, routingID, nil
}

func routingIDOf(values []string) ([]byte, error) {
	if len(values) == 0 {
		return nil, errors.New("hybrid: tunnel did not send a routing id")
	}
	if len(values) > 1 {
		return nil, errors.New("hybrid: tunnel sent more than one routing id")
	}
	raw, err := hex.DecodeString(strings.TrimSpace(values[0]))
	if err != nil {
		return nil, fmt.Errorf("hybrid: routing id is not hex: %w", err)
	}
	if len(raw) != RoutingIDSize {
		return nil, fmt.Errorf("hybrid: routing id is %d bytes, want %d", len(raw), RoutingIDSize)
	}
	return raw, nil
}
