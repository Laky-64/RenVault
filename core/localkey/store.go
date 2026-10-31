package localkey

import (
	"encoding/hex"
	"strings"

	"github.com/Laky-64/renvault-core/ctap"
)

type store struct {
	v Vault
}

func NewStore(v Vault) ctap.Store {
	return store{v: v}
}

func (s store) Passkeys(relyingParty string) ([]ctap.Credential, error) {
	target := strings.ToLower(strings.TrimSpace(relyingParty))
	var out []ctap.Credential
	for _, m := range s.v.ListPasskey() {
		if m.IsDeleted || !strings.EqualFold(strings.TrimSpace(m.RelyingParty), target) {
			continue
		}
		credentialID, err := hex.DecodeString(m.CredentialID)
		if err != nil || len(credentialID) == 0 {
			continue
		}
		out = append(out, ctap.Credential{
			ID:           m.ID,
			CredentialID: credentialID,
			RelyingParty: m.RelyingParty,
			UserName:     m.UserName,
			DisplayName:  m.DisplayName,
		})
	}
	return out, nil
}

func (s store) SignAssertion(id string, clientDataHash []byte, userVerified bool, signCount uint32) (ctap.Assertion, error) {
	got, err := s.v.SignAssertion(id, clientDataHash, userVerified, signCount)
	if err != nil {
		return ctap.Assertion{}, err
	}
	return ctap.Assertion{
		AuthenticatorData: got.AuthenticatorData,
		Signature:         got.Signature,
		UserHandle:        got.UserHandle,
	}, nil
}

func (s store) CreatePasskey(c ctap.NewCredential) (ctap.Registration, error) {
	got, err := s.v.CreatePasskey(c.RelyingParty, c.UserName, c.DisplayName, c.UserHandle, true)
	if err != nil {
		return ctap.Registration{}, err
	}
	return ctap.Registration{CredentialID: got.CredentialID, AuthenticatorData: got.AuthenticatorData}, nil
}
