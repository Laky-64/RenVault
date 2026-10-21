package hybrid

import (
	"crypto/hkdf"
	"crypto/sha256"
	"encoding/binary"
	"fmt"
)

type purpose uint32

const (
	purposeEIDKey   purpose = 1
	purposeTunnelID purpose = 2
	purposePSK      purpose = 3
)

const (
	NonceSize     = 10
	RoutingIDSize = 3
	TunnelIDSize  = 16
	EIDKeySize    = 64
	PSKSize       = 32
	EIDSize       = 16
	AdvertSize    = 20
	advertTagSize = AdvertSize - EIDSize
)

func derive(secret, salt []byte, p purpose, length int) ([]byte, error) {
	var info [4]byte
	binary.LittleEndian.PutUint32(info[:], uint32(p))
	out, err := hkdf.Key(sha256.New, secret, salt, string(info[:]), length)
	if err != nil {
		return nil, fmt.Errorf("hybrid: derive %d: %w", p, err)
	}
	return out, nil
}
