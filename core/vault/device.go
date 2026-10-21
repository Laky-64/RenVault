package vault

import (
	"crypto/rand"
	"encoding/json"
	"errors"
	"os"
)

type deviceFile struct {
	Identifier       []byte `json:"identifier"`
	ProvisioningBlob []byte `json:"provisioning_blob,omitempty"`
}

func newDeviceFile() deviceFile {
	id := make([]byte, 16)
	if _, err := rand.Read(id); err != nil {
		panic("vault: crypto/rand failed: " + err.Error())
	}
	return deviceFile{Identifier: id}
}

func loadDevice(base string) (deviceFile, error) {
	data, err := os.ReadFile(devicePath(base))
	if errors.Is(err, os.ErrNotExist) {
		return newDeviceFile(), nil
	}
	if err != nil {
		return deviceFile{}, err
	}
	var d deviceFile
	if err := json.Unmarshal(data, &d); err != nil {
		return deviceFile{}, err
	}
	if len(d.Identifier) != 16 {
		return newDeviceFile(), nil
	}
	return d, nil
}

func saveDevice(base string, d deviceFile) error {
	data, err := json.Marshal(d)
	if err != nil {
		return err
	}
	return writeFileAtomic(devicePath(base), data, 0o600)
}
