package vault

import (
	"bytes"
	"errors"

	"github.com/ProtonMail/go-crypto/openpgp"
	"github.com/ProtonMail/go-crypto/openpgp/armor"
	pgperrors "github.com/ProtonMail/go-crypto/openpgp/errors"
)

func openPGPMessage(sealed []byte, passphrase string, limit int64) ([]byte, error) {
	block, err := armor.Decode(bytes.NewReader(sealed))
	if err != nil {
		return nil, errArchiveFormat
	}
	tried := false
	prompt := func([]openpgp.Key, bool) ([]byte, error) {
		if tried {
			return nil, errArchivePassword
		}
		tried = true
		return []byte(passphrase), nil
	}
	message, err := openpgp.ReadMessage(block.Body, nil, prompt, nil)
	if err != nil {
		return nil, pgpFailure(err)
	}
	plain, err := readLimited(message.UnverifiedBody, limit)
	if err != nil {
		return nil, err
	}
	if message.SignatureError != nil && !errors.Is(message.SignatureError, pgperrors.ErrUnknownIssuer) {
		zero(plain)
		return nil, errArchiveFormat
	}
	return plain, nil
}

func pgpFailure(err error) error {
	if errors.Is(err, errArchivePassword) || errors.Is(err, pgperrors.ErrKeyIncorrect) {
		return errArchivePassword
	}
	if _, garbled := errors.AsType[pgperrors.DecryptWithSessionKeyError](err); garbled {
		return errArchivePassword
	}
	return errArchiveFormat
}
