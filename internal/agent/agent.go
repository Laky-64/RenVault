package agent

import (
	"context"
	"errors"
	"fmt"

	"github.com/Laky-64/RenVault/internal/ctap"
	"github.com/Laky-64/RenVault/internal/hybrid"
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
	qr, err := hybrid.DecodeQR(qrURL)
	if err != nil {
		return err
	}
	if qr.Operation == hybrid.OperationCreate {
		return errors.New("agent: creating new passkeys is not supported")
	}

	session, err := hybrid.NewSession(qr)
	if err != nil {
		return err
	}
	url, err := session.TunnelURL()
	if err != nil {
		return err
	}

	conn, routingID, err := hybrid.Dial(ctx, url)
	if err != nil {
		return err
	}
	defer conn.Close()

	if err := session.Bind(routingID); err != nil {
		return err
	}
	advert, err := session.Advert()
	if err != nil {
		return err
	}
	if err := a.advertiser.Start(ctx, advert); err != nil {
		return fmt.Errorf("agent: start advertising: %w", err)
	}

	crypter, err := a.greet(ctx, session, conn)
	if stopErr := a.advertiser.Stop(); stopErr != nil && err == nil {
		err = fmt.Errorf("agent: stop advertising: %w", stopErr)
	}
	if err != nil {
		return err
	}

	transport := hybrid.NewTransport(conn, crypter)
	return hybrid.Serve(ctx, transport, ctap.NewAuthenticator(store{v: a.vault}, a.choose, ctap.TransportsHybrid))
}

func (a *Agent) greet(ctx context.Context, session *hybrid.Session, conn hybrid.Conn) (*hybrid.Crypter, error) {
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
