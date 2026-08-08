package hybrid

import (
	"crypto/aes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/binary"
	"errors"
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

func decodeEID(raw []byte) (EID, error) {
	if len(raw) != EIDSize {
		return EID{}, fmt.Errorf("hybrid: eid is %d bytes, want %d", len(raw), EIDSize)
	}
	if raw[0] != 0 {
		return EID{}, errors.New("hybrid: eid has a non zero reserved byte")
	}
	return EID{
		Nonce:     append([]byte(nil), raw[1:1+NonceSize]...),
		RoutingID: append([]byte(nil), raw[1+NonceSize:1+NonceSize+RoutingIDSize]...),
		Domain:    binary.LittleEndian.Uint16(raw[1+NonceSize+RoutingIDSize:]),
	}, nil
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

func openAdvert(advert, eidKey []byte) ([]byte, error) {
	if len(advert) != AdvertSize {
		return nil, fmt.Errorf("hybrid: advert is %d bytes, want %d", len(advert), AdvertSize)
	}
	if len(eidKey) != EIDKeySize {
		return nil, fmt.Errorf("hybrid: eid key is %d bytes, want %d", len(eidKey), EIDKeySize)
	}
	if !hmac.Equal(advert[EIDSize:], advertTag(advert[:EIDSize], eidKey)) {
		return nil, errors.New("hybrid: advert tag does not match")
	}
	block, err := aes.NewCipher(eidKey[:32])
	if err != nil {
		return nil, fmt.Errorf("hybrid: eid cipher: %w", err)
	}
	out := make([]byte, EIDSize)
	block.Decrypt(out, advert[:EIDSize])
	return out, nil
}

func advertTag(sealed, eidKey []byte) []byte {
	mac := hmac.New(sha256.New, eidKey[32:])
	mac.Write(sealed)
	return mac.Sum(nil)[:advertTagSize]
}
