package hybrid

import (
	"context"
	"errors"

	"github.com/Laky-64/renvault-core/ctap"
)

const (
	commandMakeCredential = 0x01
	commandGetAssertion   = 0x02
)

func Serve(ctx context.Context, t *Transport, a *ctap.Authenticator, observe func(command, status byte)) error {
	if err := t.SendPostHandshake(ctx); err != nil {
		return err
	}
	for {
		kind, payload, err := t.Receive(ctx)
		if err != nil {
			return err
		}
		switch kind {
		case MessageShutdown:
			return nil
		case MessageUpdate:
			continue
		case MessageJSON:
			return errors.New("hybrid: json payloads are not supported")
		case MessageCTAP:
		}
		response, err := a.Handle(ctx, payload)
		if err != nil {
			return err
		}
		if observe != nil && len(payload) > 0 && len(response) > 0 {
			observe(payload[0], response[0])
		}
		if err := t.Send(ctx, MessageCTAP, response); err != nil {
			return err
		}
		if len(payload) > 0 && (payload[0] == commandGetAssertion || payload[0] == commandMakeCredential) && len(response) > 0 && response[0] == 0 {
			return nil
		}
	}
}
