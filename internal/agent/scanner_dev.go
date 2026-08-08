//go:build hybriddev && !android

package agent

import (
	"context"
	"fmt"
	"os"
	"strings"
)

const (
	devScannerEnv  = "RENVAULT_HYBRID_QR_FILE"
	devScannerPath = "hybrid-qr.txt"
)

type FileScanner struct{}

func NewFileScanner() *FileScanner {
	return &FileScanner{}
}

func defaultScanner() Scanner { return NewFileScanner() }

func (*FileScanner) Scan(context.Context) (string, error) {
	path := os.Getenv(devScannerEnv)
	if path == "" {
		path = devScannerPath
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("agent: read %s: %w", path, err)
	}
	url := strings.TrimSpace(string(raw))
	if url == "" {
		return "", fmt.Errorf("agent: %s is empty", path)
	}
	return url, nil
}
