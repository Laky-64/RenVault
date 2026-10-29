package service

import (
	"context"
	"errors"

	"github.com/Laky-64/renvault-core/bridge"
)

func installItems() {
	query(bridge.OpListWeb, func(context.Context) ([]webEntry, error) {
		active, err := unlocked()
		if err != nil {
			return nil, err
		}
		return webEntriesOf(active.ListWeb()), nil
	})

	query(bridge.OpListWiFi, func(context.Context) ([]wifiEntry, error) {
		active, err := unlocked()
		if err != nil {
			return nil, err
		}
		return wifiEntriesOf(active.ListWiFi()), nil
	})

	query(bridge.OpListPasskey, func(context.Context) ([]passkeyEntry, error) {
		active, err := unlocked()
		if err != nil {
			return nil, err
		}
		return passkeyEntriesOf(active.ListPasskey()), nil
	})

	handle(bridge.OpGetPassword, func(_ context.Context, request identifierRequest) (secretResponse, error) {
		active, err := unlocked()
		if err != nil {
			return secretResponse{}, err
		}
		secret, err := active.GetPassword(request.ID)
		return secretResponse{Secret: secret}, err
	})

	handle(bridge.OpGetTOTP, func(_ context.Context, request identifierRequest) (secretResponse, error) {
		active, err := unlocked()
		if err != nil {
			return secretResponse{}, err
		}
		secret, err := active.GetTOTP(request.ID)
		return secretResponse{Secret: secret}, err
	})

	command(bridge.OpAddPassword, func(_ context.Context, request addPasswordRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.AddPassword(
			request.Title,
			request.Website,
			request.Username,
			request.Password,
			request.Note,
		)
	})

	handle(bridge.OpEditPassword, func(_ context.Context, request editPasswordRequest) (identifierResponse, error) {
		active, err := unlocked()
		if err != nil {
			return identifierResponse{}, err
		}
		id, err := active.EditPassword(
			request.ID,
			request.Title,
			request.Website,
			request.Username,
			request.Password,
			request.Note,
			request.TOTPURL,
			request.Domains,
			request.DropTOTP,
		)
		return identifierResponse{ID: id}, err
	})

	command(bridge.OpDeletePasswords, func(_ context.Context, request identifiersRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.DeletePasswords(request.IDs)
	})

	command(bridge.OpRestorePassword, func(_ context.Context, request identifierRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.RestorePassword(request.ID)
	})

	command(bridge.OpPurgePasswords, func(_ context.Context, request identifiersRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.PurgePasswords(request.IDs)
	})

	command(bridge.OpDeletePasskeys, func(_ context.Context, request identifiersRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.DeletePasskeys(request.IDs)
	})

	command(bridge.OpRestorePasskey, func(_ context.Context, request identifierRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.RestorePasskey(request.ID)
	})

	command(bridge.OpPurgePasskeys, func(_ context.Context, request identifiersRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.PurgePasskeys(request.IDs)
	})

	handle(bridge.OpCreatePasskey, func(_ context.Context, request createPasskeyRequest) (createPasskeyResponse, error) {
		active, err := unlocked()
		if err != nil {
			return createPasskeyResponse{}, err
		}
		reg, err := active.CreatePasskey(request.RelyingParty, request.Username, request.DisplayName, request.UserHandle, true)
		return createPasskeyResponse{CredentialID: reg.CredentialID, AuthenticatorData: reg.AuthenticatorData}, err
	})

	handle(bridge.OpSignPasskey, func(_ context.Context, request signPasskeyRequest) (signPasskeyResponse, error) {
		active, err := unlocked()
		if err != nil {
			return signPasskeyResponse{}, err
		}
		if len(request.ClientDataHash) != 32 {
			return signPasskeyResponse{}, errors.New("service: client data hash must be 32 bytes")
		}
		got, err := active.SignAssertion(request.ID, request.ClientDataHash, true, 0)
		return signPasskeyResponse{AuthenticatorData: got.AuthenticatorData, Signature: got.Signature, UserHandle: got.UserHandle}, err
	})

	handle(bridge.OpEditPasskey, func(_ context.Context, request editPasskeyRequest) (identifierResponse, error) {
		active, err := unlocked()
		if err != nil {
			return identifierResponse{}, err
		}
		id, err := active.EditPasskey(request.ID, request.Title, request.Username, request.Note, request.Sites)
		return identifierResponse{ID: id}, err
	})
}
