package hybrid

import (
	"context"
	"errors"

	"github.com/Laky-64/RenVault/internal/ctap"
)

const commandGetAssertion = 0x02

func Serve(ctx context.Context, t *Transport, a *ctap.Authenticator) error {
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
		if err := t.Send(ctx, MessageCTAP, response); err != nil {
			return err
		}
		if len(payload) > 0 && payload[0] == commandGetAssertion && len(response) > 0 && response[0] == 0 {
			return nil
		}
	}
}
