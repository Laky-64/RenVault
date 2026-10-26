package portal

import (
	"errors"
	"io"
	"os"
)

var ErrUnavailable = errors.New("portal: file chooser unavailable")

const maxFileBytes = 32 << 20

type Filter struct {
	Name     string
	Patterns []string
	Types    []string
}

func readFile(path string) ([]byte, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	data, err := io.ReadAll(io.LimitReader(file, maxFileBytes+1))
	if err != nil {
		return nil, err
	}
	if len(data) > maxFileBytes {
		return nil, errors.New("portal: file too large")
	}
	return data, nil
}
