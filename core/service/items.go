package service

import (
	"context"

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
}
