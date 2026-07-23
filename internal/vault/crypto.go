package vault

import (
	"crypto/rand"
	"errors"

	"golang.org/x/crypto/argon2"
	"golang.org/x/crypto/chacha20poly1305"
)

type argonParams struct {
	Time    uint32 `json:"time" cbor:"time"`
	Memory  uint32 `json:"memory" cbor:"memory"`
	Threads uint8  `json:"threads" cbor:"threads"`
	KeyLen  uint32 `json:"key_len" cbor:"key_len"`
}

func defaultArgon() argonParams {
	return argonParams{Time: 3, Memory: 64 * 1024, Threads: 4, KeyLen: 32}
}

func newSalt() ([]byte, error) {
	s := make([]byte, 16)
	_, err := rand.Read(s)
	return s, err
}

func genKey() ([]byte, error) {
	k := make([]byte, 32)
	_, err := rand.Read(k)
	return k, err
}

func deriveKEK(password, salt []byte, p argonParams) []byte {
	return argon2.IDKey(password, salt, p.Time, p.Memory, p.Threads, p.KeyLen)
}

func wrapKey(kek, k []byte) ([]byte, error) {
	aead, err := chacha20poly1305.NewX(kek)
	if err != nil {
		return nil, err
	}
	nonce := make([]byte, aead.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return nil, err
	}
	return aead.Seal(nonce, nonce, k, nil), nil
}

func unwrapKey(kek, blob []byte) ([]byte, error) {
	aead, err := chacha20poly1305.NewX(kek)
	if err != nil {
		return nil, err
	}
	ns := aead.NonceSize()
	if len(blob) < ns {
		return nil, errors.New("vault: wrapped key too short")
	}
	nonce, ct := blob[:ns], blob[ns:]
	return aead.Open(nil, nonce, ct, nil)
}
