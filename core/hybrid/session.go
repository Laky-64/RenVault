package hybrid

import (
	"crypto/ecdh"
	"crypto/rand"
	"errors"
	"fmt"
)

type Session struct {
	qr       QR
	domain   uint16
	nonce    []byte
	eidKey   []byte
	tunnelID []byte
	eid      []byte
	psk      []byte
	advert   []byte
}

func NewSession(qr QR) (*Session, error) {
	domain, err := pickDomain(qr.KnownDomains)
	if err != nil {
		return nil, err
	}
	nonce := make([]byte, NonceSize)
	if _, err := rand.Read(nonce); err != nil {
		return nil, fmt.Errorf("hybrid: nonce: %w", err)
	}
	eidKey, err := derive(qr.Secret, nil, purposeEIDKey, EIDKeySize)
	if err != nil {
		return nil, err
	}
	tunnelID, err := derive(qr.Secret, nil, purposeTunnelID, TunnelIDSize)
	if err != nil {
		return nil, err
	}
	return &Session{qr: qr, domain: domain, nonce: nonce, eidKey: eidKey, tunnelID: tunnelID}, nil
}

func pickDomain(known uint16) (uint16, error) {
	if known == 0 {
		return 0, errors.New("hybrid: the client knows no tunnel server domain")
	}
	return authenticatorDomain, nil
}

func (s *Session) Domain() (string, error) {
	return TunnelDomain(s.domain)
}

func (s *Session) TunnelID() []byte {
	return s.tunnelID
}

func (s *Session) TunnelURL() (string, error) {
	return NewTunnelURL(s.domain, s.tunnelID)
}

func (s *Session) Operation() string {
	return s.qr.Operation
}

func (s *Session) PeerKey() (*ecdh.PublicKey, error) {
	return s.qr.PeerKey()
}

func (s *Session) Bind(routingID []byte) error {
	eid, err := EID{RoutingID: routingID, Domain: s.domain, Nonce: s.nonce}.encode()
	if err != nil {
		return err
	}
	psk, err := derive(s.qr.Secret, eid, purposePSK, PSKSize)
	if err != nil {
		return err
	}
	advert, err := sealAdvert(eid, s.eidKey)
	if err != nil {
		return err
	}
	s.eid, s.psk, s.advert = eid, psk, advert
	return nil
}

func (s *Session) Advert() ([]byte, error) {
	if s.advert == nil {
		return nil, errors.New("hybrid: session has no routing id yet")
	}
	return s.advert, nil
}

func (s *Session) PSK() ([]byte, error) {
	if s.psk == nil {
		return nil, errors.New("hybrid: session has no routing id yet")
	}
	return s.psk, nil
}
