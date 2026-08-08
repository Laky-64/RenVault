package hybrid

import (
	"crypto/ecdh"
	"crypto/rand"
	"errors"
	"fmt"
)

const pointSize = 65

var prologueKNpsk0 = []byte{1}

type Handshake struct {
	state *symmetric
	peer  *ecdh.PublicKey
	psk   []byte
}

func NewHandshake(peer *ecdh.PublicKey, psk []byte) (*Handshake, error) {
	if peer == nil {
		return nil, fmt.Errorf("hybrid: handshake needs the peer identity")
	}
	if len(psk) != PSKSize {
		return nil, fmt.Errorf("hybrid: psk is %d bytes, want %d", len(psk), PSKSize)
	}
	state := newSymmetric(protocolKNpsk0)
	state.mixHash(prologueKNpsk0)
	state.mixHash(peer.Bytes())
	return &Handshake{state: state, peer: peer, psk: psk}, nil
}

func (h *Handshake) Respond(message []byte) ([]byte, *Crypter, error) {
	if len(message) < pointSize {
		return nil, nil, errShortMessage
	}
	h.state.mixKeyAndHash(h.psk)

	peerEphemeral, err := ecdh.P256().NewPublicKey(message[:pointSize])
	if err != nil {
		return nil, nil, fmt.Errorf("hybrid: peer ephemeral key: %w", err)
	}
	h.state.mixHash(message[:pointSize])
	h.state.mixKey(message[:pointSize])
	payload, err := h.state.decryptAndHash(message[pointSize:])
	if err != nil {
		return nil, nil, err
	}
	if len(payload) != 0 {
		return nil, nil, errors.New("hybrid: handshake payload is not empty")
	}

	ephemeral, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		return nil, nil, fmt.Errorf("hybrid: ephemeral key: %w", err)
	}
	response := append([]byte(nil), ephemeral.PublicKey().Bytes()...)
	h.state.mixHash(response)
	h.state.mixKey(response)

	shared, err := ephemeral.ECDH(peerEphemeral)
	if err != nil {
		return nil, nil, fmt.Errorf("hybrid: ee: %w", err)
	}
	h.state.mixKey(shared)

	shared, err = ephemeral.ECDH(h.peer)
	if err != nil {
		return nil, nil, fmt.Errorf("hybrid: se: %w", err)
	}
	h.state.mixKey(shared)

	tail, err := h.state.encryptAndHash(nil)
	if err != nil {
		return nil, nil, err
	}
	response = append(response, tail...)

	first, second := h.state.split()
	return response, &Crypter{send: second, recv: first}, nil
}

type Initiator struct {
	state     *symmetric
	static    *ecdh.PrivateKey
	ephemeral *ecdh.PrivateKey
	psk       []byte
}

func NewInitiator(static *ecdh.PrivateKey, psk []byte) (*Initiator, error) {
	if static == nil {
		return nil, fmt.Errorf("hybrid: initiator needs a static key")
	}
	if len(psk) != PSKSize {
		return nil, fmt.Errorf("hybrid: psk is %d bytes, want %d", len(psk), PSKSize)
	}
	state := newSymmetric(protocolKNpsk0)
	state.mixHash(prologueKNpsk0)
	state.mixHash(static.PublicKey().Bytes())
	return &Initiator{state: state, static: static, psk: psk}, nil
}

func (i *Initiator) Offer() ([]byte, error) {
	i.state.mixKeyAndHash(i.psk)
	ephemeral, err := ecdh.P256().GenerateKey(rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("hybrid: ephemeral key: %w", err)
	}
	i.ephemeral = ephemeral
	out := append([]byte(nil), ephemeral.PublicKey().Bytes()...)
	i.state.mixHash(out)
	i.state.mixKey(out)
	tail, err := i.state.encryptAndHash(nil)
	if err != nil {
		return nil, err
	}
	return append(out, tail...), nil
}

func (i *Initiator) Accept(message []byte) (*Crypter, error) {
	if i.ephemeral == nil {
		return nil, fmt.Errorf("hybrid: initiator has not offered yet")
	}
	if len(message) < pointSize {
		return nil, errShortMessage
	}
	peerEphemeral, err := ecdh.P256().NewPublicKey(message[:pointSize])
	if err != nil {
		return nil, fmt.Errorf("hybrid: peer ephemeral key: %w", err)
	}
	i.state.mixHash(message[:pointSize])
	i.state.mixKey(message[:pointSize])

	shared, err := i.ephemeral.ECDH(peerEphemeral)
	if err != nil {
		return nil, fmt.Errorf("hybrid: ee: %w", err)
	}
	i.state.mixKey(shared)

	shared, err = i.static.ECDH(peerEphemeral)
	if err != nil {
		return nil, fmt.Errorf("hybrid: se: %w", err)
	}
	i.state.mixKey(shared)

	if _, err := i.state.decryptAndHash(message[pointSize:]); err != nil {
		return nil, err
	}

	first, second := i.state.split()
	return &Crypter{send: first, recv: second}, nil
}
