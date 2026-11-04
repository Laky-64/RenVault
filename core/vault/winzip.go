package vault

import (
	"archive/zip"
	"bytes"
	"compress/flate"
	"crypto/aes"
	"crypto/hmac"
	"crypto/pbkdf2"
	"crypto/rand"
	"crypto/sha1"
	"encoding/binary"
	"io"
	"time"
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

const (
	zipAESVersion2  = 2
	zipAESStrength  = 3
	zipAESKeySize   = 32
	zipAESExtraSize = 7
)

func writeEncryptedZip(name string, data []byte, password, comment string) ([]byte, error) {
	var compressed bytes.Buffer
	deflater, err := flate.NewWriter(&compressed, flate.BestCompression)
	if err != nil {
		return nil, err
	}
	if _, err := deflater.Write(data); err != nil {
		return nil, err
	}
	if err := deflater.Close(); err != nil {
		return nil, err
	}
	plain := compressed.Bytes()
	defer zero(plain)

	salt := make([]byte, zipAESKeySize/2)
	if _, err := rand.Read(salt); err != nil {
		return nil, err
	}
	derived, err := pbkdf2.Key(sha1.New, password, salt, zipAESIterations, 2*zipAESKeySize+zipAESCheckSize)
	if err != nil {
		return nil, err
	}
	defer zero(derived)
	block, err := aes.NewCipher(derived[:zipAESKeySize])
	if err != nil {
		return nil, err
	}
	sealed := make([]byte, len(plain))
	var counter, stream [aes.BlockSize]byte
	for offset, index := 0, uint64(1); offset < len(plain); offset, index = offset+aes.BlockSize, index+1 {
		binary.LittleEndian.PutUint64(counter[:8], index)
		block.Encrypt(stream[:], counter[:])
		end := min(offset+aes.BlockSize, len(plain))
		for i := offset; i < end; i++ {
			sealed[i] = plain[i] ^ stream[i-offset]
		}
	}
	sum := hmac.New(sha1.New, derived[zipAESKeySize:2*zipAESKeySize])
	sum.Write(sealed)

	body := make([]byte, 0, len(salt)+zipAESCheckSize+len(sealed)+zipAESMacSize)
	body = append(body, salt...)
	body = append(body, derived[2*zipAESKeySize:]...)
	body = append(body, sealed...)
	body = append(body, sum.Sum(nil)[:zipAESMacSize]...)

	extra := make([]byte, 4+zipAESExtraSize)
	binary.LittleEndian.PutUint16(extra[0:], zipExtraAES)
	binary.LittleEndian.PutUint16(extra[2:], zipAESExtraSize)
	binary.LittleEndian.PutUint16(extra[4:], zipAESVersion2)
	copy(extra[6:], "AE")
	extra[8] = zipAESStrength
	binary.LittleEndian.PutUint16(extra[9:], zip.Deflate)

	var out bytes.Buffer
	writer := zip.NewWriter(&out)
	if err := writer.SetComment(comment); err != nil {
		return nil, err
	}
	header := &zip.FileHeader{
		Name:               name,
		Method:             zipMethodAES,
		Flags:              zipEncryptedFlag,
		Extra:              extra,
		CompressedSize64:   uint64(len(body)),
		UncompressedSize64: uint64(len(data)),
		Modified:           time.Now(),
	}
	entry, err := writer.CreateRaw(header)
	if err != nil {
		return nil, err
	}
	if _, err := entry.Write(body); err != nil {
		return nil, err
	}
	if err := writer.Close(); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}
