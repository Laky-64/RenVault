package ctap

import (
	"context"
	"fmt"
	"slices"

	"github.com/fxamacker/cbor/v2"
)

type Credential struct {
	ID           string
	CredentialID []byte
	RelyingParty string
	UserHandle   []byte
	UserName     string
	DisplayName  string
}

type Assertion struct {
	AuthenticatorData []byte
	Signature         []byte
	UserHandle        []byte
}

type Registration struct {
	CredentialID      []byte
	AuthenticatorData []byte
}

type NewCredential struct {
	RelyingParty     string
	RelyingPartyName string
	UserName         string
	DisplayName      string
	UserHandle       []byte
}

type Store interface {
	Passkeys(relyingParty string) ([]Credential, error)
	SignAssertion(id string, clientDataHash []byte, userVerified bool, signCount uint32) (Assertion, error)
	CreatePasskey(c NewCredential) (Registration, error)
}

type Chooser func(ctx context.Context, relyingParty string, options []Credential) (Credential, error)

type Approver func(ctx context.Context, c NewCredential) error

type Authenticator struct {
	store      Store
	choose     Chooser
	approve    Approver
	transports []string
}

func NewAuthenticator(store Store, choose Chooser, approve Approver, transports []string) *Authenticator {
	return &Authenticator{store: store, choose: choose, approve: approve, transports: transports}
}

func (a *Authenticator) Handle(ctx context.Context, message []byte) ([]byte, error) {
	if len(message) == 0 {
		return fail(statusInvalidCommand), nil
	}
	command, params := message[0], message[1:]
	switch command {
	case commandGetInfo:
		if len(params) != 0 {
			return fail(statusInvalidParameter), nil
		}
		info, err := GetInfoResponse(a.transports)
		if err != nil {
			return nil, err
		}
		return succeed(info), nil
	case commandGetAssertion:
		return a.getAssertion(ctx, params)
	case commandMakeCredential:
		return a.makeCredential(ctx, params)
	case commandSelection:
		return fail(statusOperationDenied), nil
	default:
		return fail(statusInvalidCommand), nil
	}
}

func (a *Authenticator) getAssertion(ctx context.Context, params []byte) ([]byte, error) {
	var request assertionRequest
	if err := cbor.Unmarshal(params, &request); err != nil {
		return fail(statusInvalidCBOR), nil
	}
	if request.RPID == "" || len(request.ClientDataHash) == 0 {
		return fail(statusInvalidParameter), nil
	}

	found, err := a.store.Passkeys(request.RPID)
	if err != nil {
		return nil, fmt.Errorf("ctap: list passkeys: %w", err)
	}

	discoverable := len(request.AllowList) == 0
	options := make([]Credential, 0, len(found))
	for _, entry := range found {
		if discoverable || matchesAllowList(request.AllowList, entry.CredentialID) {
			options = append(options, entry)
		}
	}
	if len(options) == 0 {
		return fail(statusNoCredentials), nil
	}

	chosen := options[0]
	if a.choose != nil {
		chosen, err = a.choose(ctx, request.RPID, options)
		if err != nil {
			return fail(statusOperationDenied), nil
		}
	}

	assertion, err := a.store.SignAssertion(chosen.ID, request.ClientDataHash, true, 0)
	if err != nil {
		return fail(statusOther), nil
	}

	response := assertionResponse{
		Credential: credentialDescriptor{
			Type: "public-key",
			ID:   chosen.CredentialID,
		},
		AuthData:  assertion.AuthenticatorData,
		Signature: assertion.Signature,
	}
	if discoverable {
		if len(assertion.UserHandle) == 0 {
			return fail(statusOther), nil
		}
		response.User = &userEntity{ID: assertion.UserHandle}
		response.UserSelected = true
	}

	raw, err := encodeCTAP(response)
	if err != nil {
		return nil, err
	}
	return succeed(raw), nil
}

func (a *Authenticator) makeCredential(ctx context.Context, params []byte) ([]byte, error) {
	if a.approve == nil {
		return fail(statusOperationDenied), nil
	}
	var request makeCredentialRequest
	if err := cbor.Unmarshal(params, &request); err != nil {
		return fail(statusInvalidCBOR), nil
	}
	if request.RP.ID == "" || len(request.ClientDataHash) == 0 || len(request.User.ID) == 0 {
		return fail(statusInvalidParameter), nil
	}
	if !slices.ContainsFunc(request.Params, func(p credentialParam) bool { return p.Type == "public-key" && p.Alg == algES256 }) {
		return fail(statusUnsupportedAlgorithm), nil
	}
	if len(request.ExcludeList) > 0 {
		found, err := a.store.Passkeys(request.RP.ID)
		if err != nil {
			return nil, fmt.Errorf("ctap: list passkeys: %w", err)
		}
		for _, entry := range found {
			if matchesAllowList(request.ExcludeList, entry.CredentialID) {
				return fail(statusCredentialExcluded), nil
			}
		}
	}
	c := NewCredential{
		RelyingParty:     request.RP.ID,
		RelyingPartyName: request.RP.Name,
		UserName:         request.User.Name,
		DisplayName:      request.User.DisplayName,
		UserHandle:       request.User.ID,
	}
	if err := a.approve(ctx, c); err != nil {
		return fail(statusOperationDenied), nil
	}
	reg, err := a.store.CreatePasskey(c)
	if err != nil {
		return fail(statusOther), nil
	}
	raw, err := encodeCTAP(attestationResponse{Format: "none", AuthData: reg.AuthenticatorData, Statement: map[string]any{}})
	if err != nil {
		return nil, err
	}
	return succeed(raw), nil
}
