package hybrid

import (
	"context"
	"errors"
	"fmt"

	"github.com/Laky-64/RenVault/internal/ctap"
	"github.com/fxamacker/cbor/v2"
)

type MessageType byte

const (
	MessageShutdown MessageType = 0
	MessageCTAP     MessageType = 1
	MessageUpdate   MessageType = 2
	MessageJSON     MessageType = 3
)

type Conn interface {
	Read(ctx context.Context) ([]byte, error)
	Write(ctx context.Context, data []byte) error
	Close() error
}

type Transport struct {
	conn    Conn
	crypter *Crypter
}

func NewTransport(conn Conn, crypter *Crypter) *Transport {
	return &Transport{conn: conn, crypter: crypter}
}

func (t *Transport) Send(ctx context.Context, kind MessageType, payload []byte) error {
	framed := make([]byte, 0, len(payload)+1)
	framed = append(framed, byte(kind))
	framed = append(framed, payload...)
	sealed, err := t.crypter.Seal(framed)
	if err != nil {
		return err
	}
	return t.conn.Write(ctx, sealed)
}

func (t *Transport) Receive(ctx context.Context) (MessageType, []byte, error) {
	raw, err := t.conn.Read(ctx)
	if err != nil {
		return 0, nil, err
	}
	plain, err := t.crypter.Open(raw)
	if err != nil {
		return 0, nil, err
	}
	if len(plain) == 0 {
		return 0, nil, errors.New("hybrid: empty message")
	}
	kind := MessageType(plain[0])
	if kind > MessageJSON {
		return 0, nil, fmt.Errorf("hybrid: unknown message type %d", plain[0])
	}
	return kind, plain[1:], nil
}

func (t *Transport) SendPostHandshake(ctx context.Context) error {
	info, err := ctap.GetInfoResponse(ctap.TransportsHybrid)
	if err != nil {
		return err
	}
	mode, err := cbor.CTAP2EncOptions().EncMode()
	if err != nil {
		return fmt.Errorf("hybrid: cbor mode: %w", err)
	}
	raw, err := mode.Marshal(map[int]any{
		1: info,
		3: []string{"ctap"},
	})
	if err != nil {
		return fmt.Errorf("hybrid: encode post handshake message: %w", err)
	}
	sealed, err := t.crypter.Seal(raw)
	if err != nil {
		return err
	}
	return t.conn.Write(ctx, sealed)
}

func (t *Transport) Close() error {
	return t.conn.Close()
}
