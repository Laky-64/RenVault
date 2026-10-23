package vault

import (
	"bytes"
	"cmp"
	"crypto/sha256"
	"maps"
	"slices"
	"time"

	"github.com/Laky-64/appleservices/keychain"
	"github.com/fxamacker/cbor/v2"
)

const (
	fieldTitle    = "title"
	fieldSite     = "site"
	fieldUsername = "username"
	fieldPassword = "password"
	fieldTOTP     = "totp"
	fieldNote     = "note"
	fieldTrash    = "trash"
	fieldCreated  = "created"
)

type sealedField struct {
	Clock hlc    `cbor:"clock"`
	Box   []byte `cbor:"box"`
}

type sealedRecord struct {
	ID     string                 `cbor:"id"`
	Fields map[string]sealedField `cbor:"fields,omitempty"`
	Purge  *sealedField           `cbor:"purge,omitempty"`

	Clock  hlc    `cbor:"clock"`
	Purged bool   `cbor:"purged,omitempty"`
	Box    []byte `cbor:"box,omitempty"`
}

type recordSummary struct {
	Latest hlc    `cbor:"latest"`
	Sum    []byte `cbor:"sum"`
}

type siteValue struct {
	Domain  string   `cbor:"domain"`
	Domains []string `cbor:"domains,omitempty"`
	Website bool     `cbor:"website,omitempty"`
}

type trashValue struct {
	Deleted bool      `cbor:"deleted,omitempty"`
	At      time.Time `cbor:"at"`
}

type fieldPlain struct {
	At    time.Time       `cbor:"at"`
	Value cbor.RawMessage `cbor:"value"`
}

func fieldAD(id, name string, clock hlc) []byte { return adOf("field", id, name, clock) }

func purgeAD(id string, clock hlc) []byte { return adOf("purge", id, clock) }

func (r sealedRecord) legacyAD() []byte { return adOf("record", r.ID, r.Clock, r.Purged) }

func (r sealedRecord) legacy() bool { return len(r.Fields) == 0 && r.Purge == nil && len(r.Box) > 0 }

func webFields(w keychain.WebPassword) map[string]any {
	return map[string]any{
		fieldTitle:    w.Name,
		fieldSite:     siteValue{Domain: w.Domain, Domains: w.Domains, Website: w.Website},
		fieldUsername: w.Username,
		fieldPassword: w.Password,
		fieldTOTP:     w.TOTP,
		fieldNote:     w.Note,
		fieldTrash:    trashValue{Deleted: w.IsDeleted, At: w.DeletedAt},
		fieldCreated:  w.Created,
	}
}

func (r sealedRecord) clone() sealedRecord {
	r.Fields = maps.Clone(r.Fields)
	if r.Purge != nil {
		r.Purge = new(*r.Purge)
	}
	return r
}

func (r sealedRecord) latest() hlc {
	latest := r.Clock
	for _, f := range r.Fields {
		if f.Clock.compare(latest) > 0 {
			latest = f.Clock
		}
	}
	if r.Purge != nil && r.Purge.Clock.compare(latest) > 0 {
		latest = r.Purge.Clock
	}
	return latest
}

func (r sealedRecord) summary() recordSummary {
	type entry struct {
		Name  string
		Clock hlc
	}
	entries := make([]entry, 0, len(r.Fields)+2)
	for name, f := range r.Fields {
		entries = append(entries, entry{name, f.Clock})
	}
	slices.SortFunc(entries, func(a, b entry) int { return cmp.Compare(a.Name, b.Name) })
	if r.Purge != nil {
		entries = append(entries, entry{"\x00purge", r.Purge.Clock})
	}
	if r.legacy() {
		entries = append(entries, entry{"\x00legacy", r.Clock})
	}
	sum := sha256.Sum256(adOf(entries))
	return recordSummary{Latest: r.latest(), Sum: sum[:16]}
}

func openField(key []byte, id, name string, f sealedField) (fieldPlain, error) {
	var p fieldPlain
	plain, err := openWith(key, fieldAD(id, name, f.Clock), f.Box)
	if err != nil {
		return p, err
	}
	err = cborDec.Unmarshal(plain, &p)
	return p, err
}

func (sh *share) putWeb(key []byte, id string, w keychain.WebPassword) error {
	now := time.Now().UTC()
	rec := sealedRecord{ID: cmp.Or(id, newLocalID())}
	i := sh.record(rec.ID)
	if i >= 0 && sh.Records[i].Purge == nil && !sh.Records[i].legacy() {
		rec.Fields = sh.Records[i].Fields
	}
	if rec.Fields == nil {
		rec.Fields = map[string]sealedField{}
	}
	clock := sh.tick()
	for name, value := range webFields(w) {
		raw, err := cborEnc.Marshal(value)
		if err != nil {
			return err
		}
		if cur, ok := rec.Fields[name]; ok {
			if old, err := openField(key, rec.ID, name, cur); err == nil && bytes.Equal(old.Value, raw) {
				continue
			}
		}
		plain, err := cborEnc.Marshal(fieldPlain{At: now, Value: raw})
		if err != nil {
			return err
		}
		box, err := sealWith(key, fieldAD(rec.ID, name, clock), plain)
		if err != nil {
			return err
		}
		rec.Fields[name] = sealedField{Clock: clock, Box: box}
	}
	if i >= 0 {
		sh.Records[i] = rec
	} else {
		sh.Records = append(sh.Records, rec)
	}
	return nil
}

func (sh *share) purge(key []byte, id string) error {
	i := sh.record(id)
	if i < 0 {
		return errNotFound
	}
	clock := sh.tick()
	box, err := sealWith(key, purgeAD(id, clock), nil)
	if err != nil {
		return err
	}
	sh.Records[i] = sealedRecord{ID: id, Purge: &sealedField{Clock: clock, Box: box}}
	return nil
}

func openRecord(key []byte, r sealedRecord) (keychain.WebPassword, bool) {
	var w keychain.WebPassword
	if r.Purge != nil {
		return w, false
	}
	if r.legacy() {
		plain, err := openWith(key, r.legacyAD(), r.Box)
		if err != nil || r.Purged {
			return w, false
		}
		return w, cborDec.Unmarshal(plain, &w) == nil
	}
	found := false
	for name, f := range r.Fields {
		p, err := openField(key, r.ID, name, f)
		if err != nil {
			continue
		}
		found = true
		if name != fieldCreated && p.At.After(w.Modified) {
			w.Modified = p.At
		}
		switch name {
		case fieldTitle:
			_ = cborDec.Unmarshal(p.Value, &w.Name)
		case fieldSite:
			var site siteValue
			if cborDec.Unmarshal(p.Value, &site) == nil {
				w.Domain, w.Domains, w.Website = site.Domain, site.Domains, site.Website
			}
		case fieldUsername:
			_ = cborDec.Unmarshal(p.Value, &w.Username)
		case fieldPassword:
			_ = cborDec.Unmarshal(p.Value, &w.Password)
		case fieldTOTP:
			_ = cborDec.Unmarshal(p.Value, &w.TOTP)
		case fieldNote:
			_ = cborDec.Unmarshal(p.Value, &w.Note)
		case fieldTrash:
			var trash trashValue
			if cborDec.Unmarshal(p.Value, &trash) == nil {
				w.IsDeleted, w.DeletedAt = trash.Deleted, trash.At
			}
		case fieldCreated:
			_ = cborDec.Unmarshal(p.Value, &w.Created)
		}
	}
	return w, found && w.Domain != ""
}

func mergeRecord(have *sealedRecord, in sealedRecord) bool {
	if have.Purge != nil {
		return false
	}
	if in.Purge != nil {
		*have = sealedRecord{ID: have.ID, Purge: in.Purge}
		return true
	}
	if in.legacy() {
		if len(have.Fields) > 0 || in.Clock.compare(have.Clock) <= 0 {
			return false
		}
		*have = in
		return true
	}
	if have.legacy() {
		*have = sealedRecord{ID: have.ID}
	}
	if have.Fields == nil {
		have.Fields = map[string]sealedField{}
	}
	changed := false
	for name, f := range in.Fields {
		if len(f.Box) == 0 {
			continue
		}
		if cur, ok := have.Fields[name]; ok && f.Clock.compare(cur.Clock) <= 0 {
			continue
		}
		have.Fields[name] = f
		changed = true
	}
	return changed
}

func (sh *share) resealRecords(from, to []byte) {
	for i, r := range sh.Records {
		if r.Purge != nil {
			if _, err := openWith(from, purgeAD(r.ID, r.Purge.Clock), r.Purge.Box); err == nil {
				if box, err := sealWith(to, purgeAD(r.ID, r.Purge.Clock), nil); err == nil {
					sh.Records[i].Purge = &sealedField{Clock: r.Purge.Clock, Box: box}
				}
			}
			continue
		}
		if r.legacy() {
			if plain, err := openWith(from, r.legacyAD(), r.Box); err == nil {
				if box, err := sealWith(to, r.legacyAD(), plain); err == nil {
					sh.Records[i].Box = box
				}
			}
			continue
		}
		for name, f := range r.Fields {
			plain, err := openWith(from, fieldAD(r.ID, name, f.Clock), f.Box)
			if err != nil {
				continue
			}
			if box, err := sealWith(to, fieldAD(r.ID, name, f.Clock), plain); err == nil {
				r.Fields[name] = sealedField{Clock: f.Clock, Box: box}
			}
		}
	}
}
