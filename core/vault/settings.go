package vault

import (
	"slices"
	"time"
)

type Settings struct {
	SortField       string `cbor:"sort_field,omitempty" json:"sortField"`
	SortAscending   bool   `cbor:"sort_ascending,omitempty" json:"sortAscending"`
	AutoLockMinutes int    `cbor:"auto_lock_minutes,omitempty" json:"autoLockMinutes"`
}

var sortFields = []string{"title", "website", "modified", "created"}

var autoLockChoices = []int{1, 5, 15, 30, 60, 0}

func AutoLockChoices() []int {
	return slices.Clone(autoLockChoices)
}

func defaultSettings() Settings {
	return Settings{SortField: "title", SortAscending: true, AutoLockMinutes: 5}
}

func (s Settings) sanitized() Settings {
	d := defaultSettings()
	switch {
	case s.SortField == "":
		s.SortField = d.SortField
		s.SortAscending = d.SortAscending
	case !slices.Contains(sortFields, s.SortField):
		s.SortField = d.SortField
	}
	if !slices.Contains(autoLockChoices, s.AutoLockMinutes) {
		s.AutoLockMinutes = d.AutoLockMinutes
	}
	return s
}

func (s Settings) autoLock() time.Duration {
	return time.Duration(s.AutoLockMinutes) * time.Minute
}

func (v *Vault) Settings() Settings {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return defaultSettings()
	}
	return v.p.Settings.sanitized()
}

func (v *Vault) SetAutoLockMinutes(m int) error {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return errLocked
	}
	next := v.p.Settings
	next.AutoLockMinutes = m
	next = next.sanitized()
	v.autoLock = next.autoLock()
	v.resetTimerLocked()
	if v.p.Settings == next {
		return nil
	}
	v.p.Settings = next
	return v.saveLocked()
}

func (v *Vault) applyAutoLockLocked() {
	if v.p == nil {
		return
	}
	v.autoLock = v.p.Settings.sanitized().autoLock()
}

func (v *Vault) SetSort(field string, ascending bool) error {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return errLocked
	}
	v.resetTimerLocked()
	next := Settings{SortField: field, SortAscending: ascending}.sanitized()
	if v.p.Settings == next {
		return nil
	}
	v.p.Settings = next
	return v.saveLocked()
}
