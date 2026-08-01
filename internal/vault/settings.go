package vault

import "slices"

// Settings raccoglie le scelte dell'utente che devono sopravvivere al riavvio.
// Vivono dentro al payload cifrato come tutto il resto: fuori sarebbero un file
// modificabile da chiunque abbia accesso al disco.
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
