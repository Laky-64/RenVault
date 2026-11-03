package vault

import (
	"archive/zip"
	"bytes"
	"compress/flate"
	"crypto/aes"
	"crypto/hmac"
	"crypto/pbkdf2"
	"crypto/sha1"
	"encoding/binary"
	"io"
)

const (
	zipEncryptedFlag = 0x1
	zipMethodAES     = 99
	zipExtraAES      = 0x9901
	zipAESIterations = 1000
	zipAESCheckSize  = 2
	zipAESMacSize    = 10
)

func isEncrypted(file *zip.File) bool { return file.Flags&zipEncryptedFlag != 0 }

func readZipEntry(file *zip.File, password string, limit int64) ([]byte, error) {
	if file.UncompressedSize64 > uint64(limit) {
		return nil, errArchiveFormat
	}
	if !isEncrypted(file) {
		body, err := file.Open()
		if err != nil {
			return nil, errArchiveFormat
		}
		defer body.Close()
		return readLimited(body, limit)
	}
	if file.Method != zipMethodAES {
		return nil, errArchiveFormat
	}
	keySize, method, ok := zipAESParams(file.Extra)
	if !ok {
		return nil, errArchiveFormat
	}
	raw, err := file.OpenRaw()
	if err != nil {
		return nil, errArchiveFormat
	}
	sealed, err := readLimited(raw, limit+int64(keySize))
	if err != nil {
		return nil, err
	}
	saltSize := keySize / 2
	if len(sealed) < saltSize+zipAESCheckSize+zipAESMacSize {
		return nil, errArchiveFormat
	}
	salt := sealed[:saltSize]
	check := sealed[saltSize : saltSize+zipAESCheckSize]
	body := sealed[saltSize+zipAESCheckSize : len(sealed)-zipAESMacSize]
	mac := sealed[len(sealed)-zipAESMacSize:]

	derived, err := pbkdf2.Key(sha1.New, password, salt, zipAESIterations, 2*keySize+zipAESCheckSize)
	if err != nil {
		return nil, err
	}
	defer zero(derived)
	if !hmac.Equal(derived[2*keySize:], check) {
		return nil, errArchivePassword
	}
	sum := hmac.New(sha1.New, derived[keySize:2*keySize])
	sum.Write(body)
	if !hmac.Equal(sum.Sum(nil)[:zipAESMacSize], mac) {
		return nil, errArchivePassword
	}

	block, err := aes.NewCipher(derived[:keySize])
	if err != nil {
		return nil, err
	}
	plain := make([]byte, len(body))
	var counter, stream [aes.BlockSize]byte
	for offset, index := 0, uint64(1); offset < len(body); offset, index = offset+aes.BlockSize, index+1 {
		binary.LittleEndian.PutUint64(counter[:8], index)
		block.Encrypt(stream[:], counter[:])
		end := min(offset+aes.BlockSize, len(body))
		for i := offset; i < end; i++ {
			plain[i] = body[i] ^ stream[i-offset]
		}
	}

	switch method {
	case zip.Store:
		return plain, nil
	case zip.Deflate:
		defer zero(plain)
		return readLimited(flate.NewReader(bytes.NewReader(plain)), limit)
	}
	zero(plain)
	return nil, errArchiveFormat
}

func zipAESParams(extra []byte) (int, uint16, bool) {
	for len(extra) >= 4 {
		id := binary.LittleEndian.Uint16(extra[:2])
		size := int(binary.LittleEndian.Uint16(extra[2:4]))
		if len(extra) < 4+size {
			return 0, 0, false
		}
		field := extra[4 : 4+size]
		extra = extra[4+size:]
		if id != zipExtraAES || size < 7 || string(field[2:4]) != "AE" {
			continue
		}
		keySize := map[byte]int{1: 16, 2: 24, 3: 32}[field[4]]
		if keySize == 0 {
			return 0, 0, false
		}
		return keySize, binary.LittleEndian.Uint16(field[5:7]), true
	}
	return 0, 0, false
}

func readLimited(r io.Reader, limit int64) ([]byte, error) {
	data, err := io.ReadAll(io.LimitReader(r, limit+1))
	if err != nil {
		return nil, errArchiveFormat
	}
	if int64(len(data)) > limit {
		zero(data)
		return nil, errArchiveFormat
	}
	return data, nil
}
