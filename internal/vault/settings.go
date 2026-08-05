package vault

import "slices"

type Settings struct {
	SortField     string `cbor:"sort_field,omitempty" json:"sortField"`
	SortAscending bool   `cbor:"sort_ascending,omitempty" json:"sortAscending"`
}

var sortFields = []string{"title", "website", "modified", "created"}

func defaultSettings() Settings {
	return Settings{SortField: "title", SortAscending: true}
}

func (s Settings) sanitized() Settings {
	if s.SortField == "" {
		return defaultSettings()
	}
	if !slices.Contains(sortFields, s.SortField) {
		s.SortField = defaultSettings().SortField
	}
	return s
}

func (v *Vault) Settings() Settings {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil {
		return defaultSettings()
	}
	return v.p.Settings.sanitized()
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
