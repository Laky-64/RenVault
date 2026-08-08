package hybrid

import (
	"crypto/ecdh"
	"crypto/elliptic"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/fxamacker/cbor/v2"
)

const Prefix = "FIDO:/"

const (
	OperationAssert = "ga"
	OperationCreate = "mc"
)

const (
	chunkSize      = 7
	chunkMaxDigits = 17
	publicKeySize  = 33
	secretSize     = 16
)

var chunkDigits = [chunkSize + 1]int{0, 3, 5, 8, 10, 13, 15, 17}

type QR struct {
	PublicKey     []byte
	Secret        []byte
	KnownDomains  uint16
	Created       time.Time
	StateAssisted bool
	Operation     string
}

type qrPayload struct {
	PublicKey     []byte `cbor:"0,keyasint"`
	Secret        []byte `cbor:"1,keyasint"`
	KnownDomains  uint64 `cbor:"2,keyasint"`
	Created       int64  `cbor:"3,keyasint"`
	StateAssisted bool   `cbor:"4,keyasint"`
	Operation     string `cbor:"5,keyasint"`
}

func DecodeQR(url string) (QR, error) {
	if len(url) < len(Prefix) || !strings.EqualFold(url[:len(Prefix)], Prefix) {
		return QR{}, fmt.Errorf("hybrid: not a %s url", Prefix)
	}
	raw, err := decodeDigits(url[len(Prefix):])
	if err != nil {
		return QR{}, err
	}
	var p qrPayload
	if err := cbor.Unmarshal(raw, &p); err != nil {
		return QR{}, fmt.Errorf("hybrid: decode qr payload: %w", err)
	}
	if _, err := decompressP256(p.PublicKey); err != nil {
		return QR{}, err
	}
	if len(p.Secret) != secretSize {
		return QR{}, fmt.Errorf("hybrid: secret is %d bytes, want %d", len(p.Secret), secretSize)
	}
	if p.KnownDomains > 0xffff {
		return QR{}, fmt.Errorf("hybrid: known domain count %d out of range", p.KnownDomains)
	}
	return QR{
		PublicKey:     p.PublicKey,
		Secret:        p.Secret,
		KnownDomains:  uint16(p.KnownDomains),
		Created:       time.Unix(p.Created, 0).UTC(),
		StateAssisted: p.StateAssisted,
		Operation:     p.Operation,
	}, nil
}

func EncodeQR(q QR) (string, error) {
	if len(q.PublicKey) != publicKeySize {
		return "", fmt.Errorf("hybrid: public key is %d bytes, want %d", len(q.PublicKey), publicKeySize)
	}
	if len(q.Secret) != secretSize {
		return "", fmt.Errorf("hybrid: secret is %d bytes, want %d", len(q.Secret), secretSize)
	}
	mode, err := cbor.CTAP2EncOptions().EncMode()
	if err != nil {
		return "", fmt.Errorf("hybrid: cbor mode: %w", err)
	}
	raw, err := mode.Marshal(qrPayload{
		PublicKey:     q.PublicKey,
		Secret:        q.Secret,
		KnownDomains:  uint64(q.KnownDomains),
		Created:       q.Created.Unix(),
		StateAssisted: q.StateAssisted,
		Operation:     q.Operation,
	})
	if err != nil {
		return "", fmt.Errorf("hybrid: encode qr payload: %w", err)
	}
	return Prefix + encodeDigits(raw), nil
}

func (q QR) PeerKey() (*ecdh.PublicKey, error) {
	return decompressP256(q.PublicKey)
}

func decompressP256(compressed []byte) (*ecdh.PublicKey, error) {
	if len(compressed) != publicKeySize {
		return nil, fmt.Errorf("hybrid: public key is %d bytes, want %d", len(compressed), publicKeySize)
	}
	curve := elliptic.P256()
	x, y := elliptic.UnmarshalCompressed(curve, compressed)
	if x == nil {
		return nil, errors.New("hybrid: public key is not a P-256 point")
	}
	size := (curve.Params().BitSize + 7) / 8
	raw := make([]byte, 1+2*size)
	raw[0] = 4
	x.FillBytes(raw[1 : 1+size])
	y.FillBytes(raw[1+size:])
	return ecdh.P256().NewPublicKey(raw)
}

func decodeDigits(s string) ([]byte, error) {
	if s == "" {
		return nil, errors.New("hybrid: empty qr payload")
	}
	out := make([]byte, 0, len(s)/chunkMaxDigits*chunkSize+chunkSize)
	for len(s) > 0 {
		digits := chunkMaxDigits
		size := chunkSize
		if len(s) < chunkMaxDigits {
			digits = len(s)
			size = chunkBytes(digits)
			if size < 0 {
				return nil, fmt.Errorf("hybrid: %d trailing digits is not a valid chunk", digits)
			}
		}
		v, err := strconv.ParseUint(s[:digits], 10, 64)
		if err != nil {
			return nil, fmt.Errorf("hybrid: decode qr chunk: %w", err)
		}
		for i := 0; i < size; i++ {
			out = append(out, byte(v))
			v >>= 8
		}
		if v != 0 {
			return nil, errors.New("hybrid: qr chunk overflows its byte count")
		}
		s = s[digits:]
	}
	return out, nil
}

func encodeDigits(b []byte) string {
	var sb strings.Builder
	for len(b) > 0 {
		size := chunkSize
		if len(b) < chunkSize {
			size = len(b)
		}
		var v uint64
		for i := size - 1; i >= 0; i-- {
			v = v<<8 | uint64(b[i])
		}
		sb.WriteString(fmt.Sprintf("%0*d", chunkDigits[size], v))
		b = b[size:]
	}
	return sb.String()
}

func chunkBytes(digits int) int {
	for size, want := range chunkDigits {
		if want == digits {
			return size
		}
	}
	return -1
}
