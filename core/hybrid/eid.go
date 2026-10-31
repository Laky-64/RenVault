package hybrid

import (
	"crypto/aes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/binary"
	"fmt"
)

type EID struct {
	RoutingID []byte
	Domain    uint16
	Nonce     []byte
}

func (e EID) encode() ([]byte, error) {
	if len(e.RoutingID) != RoutingIDSize {
		return nil, fmt.Errorf("hybrid: routing id is %d bytes, want %d", len(e.RoutingID), RoutingIDSize)
	}
	if len(e.Nonce) != NonceSize {
		return nil, fmt.Errorf("hybrid: nonce is %d bytes, want %d", len(e.Nonce), NonceSize)
	}
	out := make([]byte, EIDSize)
	copy(out[1:], e.Nonce)
	copy(out[1+NonceSize:], e.RoutingID)
	binary.LittleEndian.PutUint16(out[1+NonceSize+RoutingIDSize:], e.Domain)
	return out, nil
}

func sealAdvert(eid, eidKey []byte) ([]byte, error) {
	if len(eidKey) != EIDKeySize {
		return nil, fmt.Errorf("hybrid: eid key is %d bytes, want %d", len(eidKey), EIDKeySize)
	}
	block, err := aes.NewCipher(eidKey[:32])
	if err != nil {
		return nil, fmt.Errorf("hybrid: eid cipher: %w", err)
	}
	out := make([]byte, AdvertSize)
	block.Encrypt(out[:EIDSize], eid)
	copy(out[EIDSize:], advertTag(out[:EIDSize], eidKey))
	return out, nil
}

func advertTag(sealed, eidKey []byte) []byte {
	mac := hmac.New(sha256.New, eidKey[32:])
	mac.Write(sealed)
	return mac.Sum(nil)[:advertTagSize]
}
