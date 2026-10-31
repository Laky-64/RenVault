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
