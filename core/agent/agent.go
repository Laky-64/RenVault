package agent

import (
	"context"

	"github.com/Laky-64/renvault-core/ctap"
	"github.com/Laky-64/renvault-core/hybrid"
)

func Start(ctx context.Context, v Vault, qrURL string, choose ctap.Chooser, approve ctap.Approver, connected func(), observe func(command, status byte)) ([]byte, <-chan error, error) {
	qr, err := hybrid.DecodeQR(qrURL)
	if err != nil {
		return nil, nil, err
	}
	session, err := hybrid.NewSession(qr)
	if err != nil {
		return nil, nil, err
	}
	url, err := session.TunnelURL()
	if err != nil {
		return nil, nil, err
	}
	conn, routingID, err := hybrid.Dial(ctx, url)
	if err != nil {
		return nil, nil, err
	}
	if err := session.Bind(routingID); err != nil {
		conn.Close()
		return nil, nil, err
	}
	advert, err := session.Advert()
	if err != nil {
		conn.Close()
		return nil, nil, err
	}
	done := make(chan error, 1)
	go func() {
		defer conn.Close()
		crypter, err := greet(ctx, session, conn)
		if err != nil {
			done <- err
			return
		}
		if connected != nil {
			connected()
		}
		transport := hybrid.NewTransport(conn, crypter)
		done <- hybrid.Serve(ctx, transport, ctap.NewAuthenticator(store{v: v}, choose, approve, ctap.TransportsHybrid), observe)
	}()
	return advert, done, nil
}

func greet(ctx context.Context, session *hybrid.Session, conn hybrid.Conn) (*hybrid.Crypter, error) {
	offer, err := conn.Read(ctx)
	if err != nil {
		return nil, err
	}
	peer, err := session.PeerKey()
	if err != nil {
		return nil, err
	}
	psk, err := session.PSK()
	if err != nil {
		return nil, err
	}
	handshake, err := hybrid.NewHandshake(peer, psk)
	if err != nil {
		return nil, err
	}
	reply, crypter, err := handshake.Respond(offer)
	if err != nil {
		return nil, err
	}
	if err := conn.Write(ctx, reply); err != nil {
		return nil, err
	}
	return crypter, nil
}
