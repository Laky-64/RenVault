package hybrid

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"fmt"
)

const (
	protocolKNpsk0 = "Noise_KNpsk0_P256_AESGCM_SHA256"
	hashSize       = sha256.Size
)

type symmetric struct {
	ck     [hashSize]byte
	h      [hashSize]byte
	k      [32]byte
	hasKey bool
	n      uint32
}

func newSymmetric(name string) *symmetric {
	s := &symmetric{}
	if len(name) <= hashSize {
		copy(s.ck[:], name)
	} else {
		s.ck = sha256.Sum256([]byte(name))
	}
	s.h = s.ck
	return s
}

func (s *symmetric) mixHash(data []byte) {
	sum := sha256.New()
	sum.Write(s.h[:])
	sum.Write(data)
	copy(s.h[:], sum.Sum(nil))
}

func (s *symmetric) mixKey(ikm []byte) {
	out := noiseHKDF(s.ck, ikm, 2)
	s.ck = out[0]
	s.k = out[1]
	s.hasKey = true
	s.n = 0
}

func (s *symmetric) mixKeyAndHash(ikm []byte) {
	out := noiseHKDF(s.ck, ikm, 3)
	s.ck = out[0]
	s.mixHash(out[1][:])
	s.k = out[2]
	s.hasKey = true
	s.n = 0
}

func (s *symmetric) encryptAndHash(plaintext []byte) ([]byte, error) {
	if !s.hasKey {
		s.mixHash(plaintext)
		return append([]byte(nil), plaintext...), nil
	}
	gcm, err := s.aead()
	if err != nil {
		return nil, err
	}
	out := gcm.Seal(nil, s.nonce(), plaintext, s.h[:])
	s.n++
	s.mixHash(out)
	return out, nil
}

func (s *symmetric) decryptAndHash(ciphertext []byte) ([]byte, error) {
	if !s.hasKey {
		s.mixHash(ciphertext)
		return append([]byte(nil), ciphertext...), nil
	}
	gcm, err := s.aead()
	if err != nil {
		return nil, err
	}
	out, err := gcm.Open(nil, s.nonce(), ciphertext, s.h[:])
	if err != nil {
		return nil, fmt.Errorf("hybrid: noise decrypt: %w", err)
	}
	s.n++
	s.mixHash(ciphertext)
	return out, nil
}

func (s *symmetric) split() ([32]byte, [32]byte) {
	out := noiseHKDF(s.ck, nil, 2)
	return out[0], out[1]
}

func (s *symmetric) aead() (cipher.AEAD, error) {
	block, err := aes.NewCipher(s.k[:])
	if err != nil {
		return nil, fmt.Errorf("hybrid: noise cipher: %w", err)
	}
	return cipher.NewGCM(block)
}

func (s *symmetric) nonce() []byte {
	var out [12]byte
	binary.BigEndian.PutUint32(out[:4], s.n)
	return out[:]
}

func transportNonce(counter uint32) []byte {
	var out [12]byte
	binary.BigEndian.PutUint32(out[8:], counter)
	return out[:]
}

func noiseHKDF(ck [hashSize]byte, ikm []byte, n int) [][hashSize]byte {
	prk := hmacSum(ck[:], ikm)
	out := make([][hashSize]byte, 0, n)
	var prev []byte
	for i := 1; i <= n; i++ {
		prev = hmacSum(prk, append(append([]byte(nil), prev...), byte(i)))
		var v [hashSize]byte
		copy(v[:], prev)
		out = append(out, v)
	}
	return out
}

func hmacSum(key, data []byte) []byte {
	mac := hmac.New(sha256.New, key)
	mac.Write(data)
	return mac.Sum(nil)
}

const paddingGranularity = 32

type Crypter struct {
	send    [32]byte
	recv    [32]byte
	sendSeq uint32
	recvSeq uint32
}

func (c *Crypter) Seal(plaintext []byte) ([]byte, error) {
	gcm, err := gcmFor(c.send)
	if err != nil {
		return nil, err
	}
	out := gcm.Seal(nil, transportNonce(c.sendSeq), pad(plaintext), nil)
	c.sendSeq++
	return out, nil
}

func (c *Crypter) Open(ciphertext []byte) ([]byte, error) {
	gcm, err := gcmFor(c.recv)
	if err != nil {
		return nil, err
	}
	padded, err := gcm.Open(nil, transportNonce(c.recvSeq), ciphertext, nil)
	if err != nil {
		return nil, fmt.Errorf("hybrid: open transport message: %w", err)
	}
	c.recvSeq++
	return unpad(padded)
}

func pad(plaintext []byte) []byte {
	size := (len(plaintext) + paddingGranularity) &^ (paddingGranularity - 1)
	out := make([]byte, size)
	copy(out, plaintext)
	out[size-1] = byte(size - len(plaintext) - 1)
	return out
}

func unpad(padded []byte) ([]byte, error) {
	if len(padded) == 0 {
		return nil, errors.New("hybrid: transport message is empty")
	}
	zeros := int(padded[len(padded)-1])
	if zeros+1 > len(padded) {
		return nil, errors.New("hybrid: transport message has invalid padding")
	}
	return padded[:len(padded)-zeros-1], nil
}

var errShortMessage = errors.New("hybrid: handshake message is too short")

func gcmFor(key [32]byte) (cipher.AEAD, error) {
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return nil, fmt.Errorf("hybrid: transport cipher: %w", err)
	}
	return cipher.NewGCM(block)
}
