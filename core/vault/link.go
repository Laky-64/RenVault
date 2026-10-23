package vault

import (
	"bytes"
	"crypto/ecdh"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"time"

	"filippo.io/cpace"
	"github.com/flynn/noise"
)

const (
	linkPrologue   = "renvault-link-v1"
	linkTimeout    = 30 * time.Second
	linkMaxMessage = 64 << 20
	linkChunk      = noise.MaxMsgLen - 16

	modeSync byte = 'S'
	modePair byte = 'P'
)

var (
	linkSuite = noise.NewCipherSuite(noise.DH25519, noise.CipherChaChaPoly, noise.HashBLAKE2s)

	errLinkFrame   = errors.New("vault: malformed link frame")
	errUntrusted   = errors.New("vault: device is not paired with this vault")
	errNoPairing   = errors.New("vault: no pairing in progress on that device")
	errPairRefused = errors.New("vault: pairing was refused, check the code")
)

type link struct {
	conn net.Conn
	send *noise.CipherState
	recv *noise.CipherState
	peer []byte
}

func writeFrame(w io.Writer, b []byte) error {
	if len(b) > noise.MaxMsgLen {
		return errLinkFrame
	}
	frame := binary.BigEndian.AppendUint16(make([]byte, 0, 2+len(b)), uint16(len(b)))
	_, err := w.Write(append(frame, b...))
	return err
}

func readFrame(r io.Reader) ([]byte, error) {
	var size [2]byte
	if _, err := io.ReadFull(r, size[:]); err != nil {
		return nil, err
	}
	b := make([]byte, binary.BigEndian.Uint16(size[:]))
	if _, err := io.ReadFull(r, b); err != nil {
		return nil, err
	}
	return b, nil
}

func identityKey(private []byte) (noise.DHKey, error) {
	private = bytes.Clone(private)
	key, err := ecdh.X25519().NewPrivateKey(private)
	if err != nil {
		return noise.DHKey{}, err
	}
	return noise.DHKey{Private: private, Public: key.PublicKey().Bytes()}, nil
}

func handshake(conn net.Conn, initiator bool, self noise.DHKey, psk []byte) (*link, error) {
	cfg := noise.Config{
		CipherSuite:   linkSuite,
		Pattern:       noise.HandshakeXX,
		Initiator:     initiator,
		StaticKeypair: self,
		Prologue:      []byte(linkPrologue),
	}
	if psk != nil {
		cfg.PresharedKey = psk
		cfg.PresharedKeyPlacement = 3
	}
	hs, err := noise.NewHandshakeState(cfg)
	if err != nil {
		return nil, err
	}
	var out, in *noise.CipherState
	for step := range 3 {
		if (step%2 == 0) == initiator {
			msg, a, b, err := hs.WriteMessage(nil, nil)
			if err != nil {
				return nil, err
			}
			if err := writeFrame(conn, msg); err != nil {
				return nil, err
			}
			out, in = a, b
			continue
		}
		msg, err := readFrame(conn)
		if err != nil {
			return nil, err
		}
		_, a, b, err := hs.ReadMessage(nil, msg)
		if err != nil {
			return nil, err
		}
		out, in = a, b
	}
	if out == nil || in == nil {
		return nil, errLinkFrame
	}
	l := &link{conn: conn, peer: hs.PeerStatic()}
	if initiator {
		l.send, l.recv = out, in
	} else {
		l.send, l.recv = in, out
	}
	return l, nil
}

func (l *link) seal(b []byte) error {
	sealed, err := l.send.Encrypt(nil, nil, b)
	if err != nil {
		return err
	}
	return writeFrame(l.conn, sealed)
}

func (l *link) open() ([]byte, error) {
	frame, err := readFrame(l.conn)
	if err != nil {
		return nil, err
	}
	return l.recv.Decrypt(nil, nil, frame)
}

func (l *link) write(v any) error {
	plain, err := cborEnc.Marshal(v)
	if err != nil {
		return err
	}
	_ = l.conn.SetWriteDeadline(time.Now().Add(linkTimeout))
	if err := l.seal(binary.BigEndian.AppendUint32(nil, uint32(len(plain)))); err != nil {
		return err
	}
	for len(plain) > 0 {
		n := min(len(plain), linkChunk)
		if err := l.seal(plain[:n]); err != nil {
			return err
		}
		plain = plain[n:]
	}
	return nil
}

func (l *link) read(v any) error {
	_ = l.conn.SetReadDeadline(time.Now().Add(linkTimeout))
	head, err := l.open()
	if err != nil {
		return err
	}
	if len(head) != 4 {
		return errLinkFrame
	}
	size := int(binary.BigEndian.Uint32(head))
	if size > linkMaxMessage {
		return errLinkFrame
	}
	plain := make([]byte, 0, size)
	for len(plain) < size {
		part, err := l.open()
		if err != nil {
			return err
		}
		plain = append(plain, part...)
	}
	if len(plain) != size {
		return errLinkFrame
	}
	return cborDec.Unmarshal(plain, v)
}

func pairContext(session []byte) *cpace.ContextInfo {
	return cpace.NewContextInfo("renvault-joiner", "renvault-host", session)
}

func pairKey(key []byte) []byte {
	sum := sha256.Sum256(key)
	return sum[:]
}

func dialPair(conn net.Conn, code string, self noise.DHKey) (*link, error) {
	_ = conn.SetDeadline(time.Now().Add(linkTimeout))
	if err := writeFrame(conn, []byte{modePair}); err != nil {
		return nil, err
	}
	session, err := readFrame(conn)
	if err != nil {
		return nil, errNoPairing
	}
	if len(session) == 0 {
		return nil, errNoPairing
	}
	msgA, state, err := cpace.Start(code, pairContext(session))
	if err != nil {
		return nil, err
	}
	if err := writeFrame(conn, msgA); err != nil {
		return nil, err
	}
	msgB, err := readFrame(conn)
	if err != nil {
		return nil, err
	}
	key, err := state.Finish(msgB)
	if err != nil {
		return nil, errPairRefused
	}
	l, err := handshake(conn, true, self, pairKey(key))
	if err != nil {
		return nil, errPairRefused
	}
	return l, nil
}

func acceptPair(conn net.Conn, code string, session []byte, self noise.DHKey) (*link, error) {
	if err := writeFrame(conn, session); err != nil {
		return nil, err
	}
	msgA, err := readFrame(conn)
	if err != nil {
		return nil, err
	}
	msgB, key, err := cpace.Exchange(code, pairContext(session), msgA)
	if err != nil {
		return nil, err
	}
	if err := writeFrame(conn, msgB); err != nil {
		return nil, err
	}
	l, err := handshake(conn, false, self, pairKey(key))
	if err != nil {
		return nil, errPairRefused
	}
	return l, nil
}

func dialSync(conn net.Conn, self noise.DHKey) (*link, error) {
	_ = conn.SetDeadline(time.Now().Add(linkTimeout))
	if err := writeFrame(conn, []byte{modeSync}); err != nil {
		return nil, err
	}
	return handshake(conn, true, self, nil)
}
