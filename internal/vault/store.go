package vault

import (
	"crypto/rand"
	"errors"
	"os"
	"path/filepath"
	"time"

	"github.com/Laky-64/appleservices"
	"github.com/Laky-64/appleservices/keychain"
	"github.com/fxamacker/cbor/v2"
	"golang.org/x/crypto/chacha20poly1305"
)

const (
	vaultVersion   = 1
	methodPassword = "password"
)

var errNoVault = errors.New("vault: file not found")

var (
	cborEnc    = mustCBOREncMode()
	cborDec, _ = cbor.DecOptions{}.DecMode()
)

func mustCBOREncMode() cbor.EncMode {
	em, err := cbor.EncOptions{Time: cbor.TimeRFC3339Nano}.EncMode()
	if err != nil {
		panic("vault: cbor enc mode: " + err.Error())
	}
	return em
}

type wrappedKey struct {
	Method string `cbor:"method"`
	Blob   []byte `cbor:"blob"`
}

type header struct {
	Version int          `cbor:"version"`
	Salt    []byte       `cbor:"salt"`
	Argon   argonParams  `cbor:"argon"`
	Wraps   []wrappedKey `cbor:"wraps"`
}

type payload struct {
	Credentials appleservices.Credentials `cbor:"credentials"`
	Session     *appleservices.Session    `cbor:"session,omitempty"`
	Peer        appleservices.PeerKey     `cbor:"peer"`
	Web         []keychain.WebPassword    `cbor:"web,omitempty"`
	WiFi        []keychain.WiFiPassword   `cbor:"wifi,omitempty"`
	Passkey     []passkeyEntry            `cbor:"passkey,omitempty"`
	SyncedAt    time.Time                 `cbor:"synced_at"`

	Pwned   []string  `cbor:"pwned,omitempty"`
	PwnedAt time.Time `cbor:"pwned_at,omitempty"`

	ProfileName      string `cbor:"profile_name,omitempty"`
	ProfilePhoto     []byte `cbor:"profile_photo,omitempty"`
	ProfilePhotoType string `cbor:"profile_photo_type,omitempty"`

	Settings Settings `cbor:"settings,omitempty"`
}

type vaultFile struct {
	Header header `cbor:"header"`
	Nonce  []byte `cbor:"nonce"`
	Cipher []byte `cbor:"cipher"`
}

func encodeHeaderAAD(h header) ([]byte, error) {
	return cborEnc.Marshal(h)
}

func encryptPayload(k []byte, h header, p payload) (nonce, cipher []byte, err error) {
	aead, err := chacha20poly1305.NewX(k)
	if err != nil {
		return nil, nil, err
	}
	plain, err := cborEnc.Marshal(p)
	if err != nil {
		return nil, nil, err
	}
	aad, err := encodeHeaderAAD(h)
	if err != nil {
		return nil, nil, err
	}
	nonce = make([]byte, aead.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return nil, nil, err
	}
	cipher = aead.Seal(nil, nonce, plain, aad)
	return nonce, cipher, nil
}

func decryptPayload(k []byte, vf vaultFile) (payload, error) {
	aead, err := chacha20poly1305.NewX(k)
	if err != nil {
		return payload{}, err
	}
	aad, err := encodeHeaderAAD(vf.Header)
	if err != nil {
		return payload{}, err
	}
	plain, err := aead.Open(nil, vf.Nonce, vf.Cipher, aad)
	if err != nil {
		return payload{}, err
	}
	var p payload
	if err := cborDec.Unmarshal(plain, &p); err != nil {
		return payload{}, err
	}
	return p, nil
}

func newVaultFile(masterPassword, k []byte, p payload) (vaultFile, error) {
	salt, err := newSalt()
	if err != nil {
		return vaultFile{}, err
	}
	argon := defaultArgon()
	kek := deriveKEK(masterPassword, salt, argon)
	defer zero(kek)
	_ = lockMemory(kek)
	wrapped, err := wrapKey(kek, k)
	if err != nil {
		return vaultFile{}, err
	}
	h := header{
		Version: vaultVersion,
		Salt:    salt,
		Argon:   argon,
		Wraps:   []wrappedKey{{Method: methodPassword, Blob: wrapped}},
	}
	nonce, cipher, err := encryptPayload(k, h, p)
	if err != nil {
		return vaultFile{}, err
	}
	return vaultFile{Header: h, Nonce: nonce, Cipher: cipher}, nil
}

func (vf vaultFile) passwordWrap() ([]byte, bool) {
	for _, w := range vf.Header.Wraps {
		if w.Method == methodPassword {
			return w.Blob, true
		}
	}
	return nil, false
}

func (vf vaultFile) unlockWithPassword(masterPassword []byte) ([]byte, payload, error) {
	blob, ok := vf.passwordWrap()
	if !ok {
		return nil, payload{}, errors.New("vault: no password wrap in header")
	}
	kek := deriveKEK(masterPassword, vf.Header.Salt, vf.Header.Argon)
	defer zero(kek)
	_ = lockMemory(kek)
	k, err := unwrapKey(kek, blob)
	if err != nil {
		return nil, payload{}, err
	}
	p, err := decryptPayload(k, vf)
	if err != nil {
		zero(k)
		return nil, payload{}, err
	}
	return k, p, nil
}

func (vf vaultFile) reencryptPayload(k []byte, p payload) (vaultFile, error) {
	nonce, cipher, err := encryptPayload(k, vf.Header, p)
	if err != nil {
		return vaultFile{}, err
	}
	vf.Nonce = nonce
	vf.Cipher = cipher
	return vf, nil
}

func loadVaultFile(base string) (vaultFile, bool, error) {
	data, err := os.ReadFile(vaultPath(base))
	if errors.Is(err, os.ErrNotExist) {
		return vaultFile{}, false, nil
	}
	if err != nil {
		return vaultFile{}, false, err
	}
	var vf vaultFile
	if err := cborDec.Unmarshal(data, &vf); err != nil {
		return vaultFile{}, false, err
	}
	return vf, true, nil
}

func writeFileAtomic(path string, data []byte, perm os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return err
	}
	tmp := path + ".tmp"
	f, err := os.OpenFile(tmp, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, perm)
	if err != nil {
		return err
	}
	if _, err = f.Write(data); err != nil {
		_ = f.Close()
		_ = os.Remove(tmp)
		return err
	}
	if err = f.Sync(); err != nil {
		_ = f.Close()
		_ = os.Remove(tmp)
		return err
	}
	if err = f.Close(); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	if err = os.Rename(tmp, path); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	return nil
}

func saveVaultFile(base string, vf vaultFile) error {
	data, err := cborEnc.Marshal(vf)
	if err != nil {
		return err
	}
	return writeFileAtomic(vaultPath(base), data, 0o600)
}
