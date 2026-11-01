package icons

import (
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"time"
)

func (s *IconService) SetDir(dir string) {
	s.mu.Lock()
	s.dir = dir
	s.mu.Unlock()
}

func (s *IconService) Clear() {
	s.mu.Lock()
	dir := s.dir
	s.cache = make(map[string]*entry)
	s.mu.Unlock()
	if dir != "" {
		_ = os.RemoveAll(dir)
	}
}

func (s *IconService) diskPath(key string) string {
	s.mu.Lock()
	dir := s.dir
	s.mu.Unlock()
	if dir == "" {
		return ""
	}
	sum := sha256.Sum256([]byte(key))
	return filepath.Join(dir, hex.EncodeToString(sum[:])+".png")
}

func (s *IconService) loadDisk(key string) ([]byte, bool) {
	path := s.diskPath(key)
	if path == "" {
		return nil, false
	}
	info, err := os.Stat(path)
	if err != nil || time.Since(info.ModTime()) > hitTTL {
		return nil, false
	}
	data, err := os.ReadFile(path)
	if err != nil || len(data) == 0 {
		return nil, false
	}
	return data, true
}

func (s *IconService) saveDisk(key string, data []byte) {
	path := s.diskPath(key)
	if path == "" || len(data) == 0 {
		return
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return
	}
	if err := os.Rename(tmp, path); err != nil {
		_ = os.Remove(tmp)
	}
}
