package ctap

import (
	"bytes"
	"fmt"

	"github.com/fxamacker/cbor/v2"
)

const (
	commandMakeCredential byte = 0x01
	commandGetAssertion   byte = 0x02
	commandGetInfo        byte = 0x04
	commandSelection      byte = 0x0b
)

const (
	statusOK                   byte = 0x00
	statusInvalidCommand       byte = 0x01
	statusInvalidParameter     byte = 0x02
	statusCredentialExcluded   byte = 0x19
	statusUnsupportedAlgorithm byte = 0x26
	statusInvalidCBOR          byte = 0x12
	statusOperationDenied      byte = 0x27
	statusNoCredentials        byte = 0x2e
	statusOther                byte = 0x7f
)

type credentialDescriptor struct {
	Type       string   `cbor:"type"`
	ID         []byte   `cbor:"id"`
	Transports []string `cbor:"transports,omitempty"`
}

type userEntity struct {
	ID          []byte `cbor:"id"`
	Name        string `cbor:"name"`
	DisplayName string `cbor:"displayName"`
}

type assertionRequest struct {
	RPID           string                 `cbor:"1,keyasint"`
	ClientDataHash []byte                 `cbor:"2,keyasint"`
	AllowList      []credentialDescriptor `cbor:"3,keyasint,omitempty"`
	Extensions     cbor.RawMessage        `cbor:"4,keyasint,omitempty"`
	Options        map[string]bool        `cbor:"5,keyasint,omitempty"`
}

const algES256 = -7

type rpEntity struct {
	ID   string `cbor:"id"`
	Name string `cbor:"name,omitempty"`
}

type credentialParam struct {
	Type string `cbor:"type"`
	Alg  int    `cbor:"alg"`
}

type makeCredentialRequest struct {
	ClientDataHash []byte                 `cbor:"1,keyasint"`
	RP             rpEntity               `cbor:"2,keyasint"`
	User           userEntity             `cbor:"3,keyasint"`
	Params         []credentialParam      `cbor:"4,keyasint"`
	ExcludeList    []credentialDescriptor `cbor:"5,keyasint,omitempty"`
	Extensions     cbor.RawMessage        `cbor:"6,keyasint,omitempty"`
	Options        map[string]bool        `cbor:"7,keyasint,omitempty"`
}

type attestationResponse struct {
	Format    string         `cbor:"1,keyasint"`
	AuthData  []byte         `cbor:"2,keyasint"`
	Statement map[string]any `cbor:"3,keyasint"`
}

type assertionResponse struct {
	Credential   credentialDescriptor `cbor:"1,keyasint"`
	AuthData     []byte               `cbor:"2,keyasint"`
	Signature    []byte               `cbor:"3,keyasint"`
	User         *userEntity          `cbor:"4,keyasint,omitempty"`
	UserSelected bool                 `cbor:"6,keyasint,omitempty"`
}

func fail(status byte) []byte {
	return []byte{status}
}

func succeed(payload []byte) []byte {
	return append([]byte{statusOK}, payload...)
}

func encodeCTAP(v any) ([]byte, error) {
	mode, err := cbor.CTAP2EncOptions().EncMode()
	if err != nil {
		return nil, fmt.Errorf("ctap: cbor mode: %w", err)
	}
	raw, err := mode.Marshal(v)
	if err != nil {
		return nil, fmt.Errorf("ctap: encode ctap response: %w", err)
	}
	return raw, nil
}

func matchesAllowList(allow []credentialDescriptor, credentialID []byte) bool {
	for _, entry := range allow {
		if bytes.Equal(entry.ID, credentialID) {
			return true
		}
	}
	return false
}

const aaguidSize = 16

var AAGUID = [aaguidSize]byte{0xab, 0xbd, 0x52, 0x9f, 0x36, 0x7e, 0x4f, 0x07, 0x83, 0x02, 0xf7, 0xa2, 0x70, 0xd9, 0x67, 0xee}

var (
	TransportsUSB    = []string{"usb"}
	TransportsHybrid = []string{"cable", "hybrid", "internal"}
)

func GetInfoResponse(transports []string) ([]byte, error) {
	mode, err := cbor.CTAP2EncOptions().EncMode()
	if err != nil {
		return nil, fmt.Errorf("ctap: cbor mode: %w", err)
	}
	raw, err := mode.Marshal(map[int]any{
		1: []string{"FIDO_2_0", "FIDO_2_1"},
		2: []string{"prf"},
		3: AAGUID[:],
		4: map[string]bool{"rk": true, "uv": true},
		9: transports,
	})
	if err != nil {
		return nil, fmt.Errorf("ctap: encode getInfo: %w", err)
	}
	return raw, nil
}
