package agent

import (
	"context"
	"errors"
	"fmt"

	"github.com/Laky-64/renvault-core/ctap"
	"github.com/Laky-64/renvault-core/hybrid"
)

type Advertiser interface {
	Start(ctx context.Context, advert []byte) error
	Stop() error
}

type Agent struct {
	vault      Vault
	scanner    Scanner
	advertiser Advertiser
	choose     ctap.Chooser
}

func New(v Vault, scanner Scanner, advertiser Advertiser, choose ctap.Chooser) *Agent {
	return &Agent{vault: v, scanner: scanner, advertiser: advertiser, choose: choose}
}

func NewDefault(v Vault, choose ctap.Chooser) *Agent {
	return New(v, defaultScanner(), newAdvertiser(), choose)
}

func (a *Agent) Run(ctx context.Context) error {
	if !Supported() {
		return ErrUnsupported
	}
	if a.scanner == nil {
		return errors.New("agent: no qr scanner")
	}
	if a.advertiser == nil {
		return errors.New("agent: no bluetooth advertiser")
	}
	qrURL, err := a.scanner.Scan(ctx)
	if err != nil {
		return err
	}
	stopped := make(chan error, 1)
	advert, done, err := Start(ctx, a.vault, qrURL, a.choose, nil, func() { stopped <- a.advertiser.Stop() }, nil)
	if err != nil {
		return err
	}
	if err := a.advertiser.Start(ctx, advert); err != nil {
		return fmt.Errorf("agent: start advertising: %w", err)
	}
	err = <-done
	select {
	case stopErr := <-stopped:
		if stopErr != nil && err == nil {
			err = fmt.Errorf("agent: stop advertising: %w", stopErr)
		}
	default:
		_ = a.advertiser.Stop()
	}
	return err
}

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
