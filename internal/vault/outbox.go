package vault

import (
	"crypto/rand"
	"errors"
	"fmt"
	"log"
	"net"
	"net/url"
	"slices"
	"syscall"
	"time"

	"github.com/Laky-64/RenVault/internal/apple"
	"github.com/Laky-64/appleservices"
	"github.com/Laky-64/appleservices/keychain"
)

type opKind string

const (
	opAdd     opKind = "add"
	opEdit    opKind = "edit"
	opDelete  opKind = "delete"
	opRestore opKind = "restore"
	opPurge   opKind = "purge"
)

type outboxOp struct {
	Seq         uint64 `cbor:"seq"`
	Kind        opKind `cbor:"kind"`
	Domain      string `cbor:"domain"`
	Username    string `cbor:"username"`
	NewDomain   string `cbor:"new_domain,omitempty"`
	NewUsername string `cbor:"new_username,omitempty"`
	Password    string `cbor:"password,omitempty"`
	Title       string `cbor:"title,omitempty"`
	Note        string `cbor:"note,omitempty"`
	SetNote     bool   `cbor:"set_note,omitempty"`
	TOTP        string `cbor:"totp,omitempty"`
	DropTOTP    bool   `cbor:"drop_totp,omitempty"`
	Manual      bool   `cbor:"manual,omitempty"`
	Rewrite     bool   `cbor:"rewrite,omitempty"`
}

func (o outboxOp) key() string { return o.Domain + "\x00" + o.Username }

func newLocalID() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "00000000-0000-4000-8000-000000000000"
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

func pushOp(p *payload, op outboxOp) {
	p.OutboxSeq++
	op.Seq = p.OutboxSeq
	p.Outbox = append(p.Outbox, op)
}

func pushEdit(p *payload, op outboxOp, inflight uint64) {
	last := len(p.Outbox) - 1
	if last < 0 {
		pushOp(p, op)
		return
	}
	prev := &p.Outbox[last]
	if prev.Seq == inflight || prev.Domain != op.Domain || prev.Username != op.Username {
		pushOp(p, op)
		return
	}
	switch {
	case prev.Kind == opEdit:
		mergeEdit(prev, op)
	case prev.Kind == opAdd && op.TOTP == "" && (!prev.Manual || op.NewDomain == prev.Domain):
		mergeAdd(prev, op)
	default:
		pushOp(p, op)
	}
}

func mergeEdit(prev *outboxOp, op outboxOp) {
	prev.NewDomain = op.NewDomain
	prev.NewUsername = op.NewUsername
	prev.Password = op.Password
	prev.Rewrite = prev.Rewrite || op.Rewrite
	if op.SetNote {
		prev.Note = op.Note
		prev.SetNote = true
	}
	switch {
	case op.TOTP != "":
		prev.TOTP = op.TOTP
		prev.DropTOTP = false
	case op.DropTOTP:
		prev.TOTP = ""
		prev.DropTOTP = true
	}
}

func mergeAdd(prev *outboxOp, op outboxOp) {
	if op.SetNote {
		prev.Note = op.Note
	}
	if !op.Rewrite {
		return
	}
	prev.Domain = op.NewDomain
	prev.Username = op.NewUsername
	prev.Password = op.Password
}

func findOp(p *payload, kind opKind, domain, username string, skip uint64) int {
	for i, op := range p.Outbox {
		if op.Seq != skip && op.Kind == kind && op.Domain == domain && op.Username == username {
			return i
		}
	}
	return -1
}

func dropOps(p *payload, domain, username string, skip uint64) {
	p.Outbox = slices.DeleteFunc(p.Outbox, func(op outboxOp) bool {
		return op.Seq != skip && op.Domain == domain && op.Username == username
	})
}

func (v *Vault) pendingOp() (outboxOp, bool) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.p == nil || len(v.p.Outbox) == 0 {
		return outboxOp{}, false
	}
	op := v.p.Outbox[0]
	v.inflight = op.Seq
	return op, true
}

func (v *Vault) settleOp(seq uint64) error {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.inflight = 0
	if v.p == nil || v.k == nil {
		return errLocked
	}
	if len(v.p.Outbox) == 0 || v.p.Outbox[0].Seq != seq {
		return nil
	}
	v.p.Outbox = slices.Delete(v.p.Outbox, 0, 1)
	return v.saveLocked()
}

func (v *Vault) releaseOp() {
	v.mu.Lock()
	v.inflight = 0
	v.mu.Unlock()
}

type SyncStatus struct {
	Pending  int       `json:"pending"`
	SyncedAt time.Time `json:"syncedAt"`
	Error    string    `json:"error"`
}

func (v *Vault) SyncState() SyncStatus {
	v.mu.Lock()
	defer v.mu.Unlock()
	st := SyncStatus{}
	if v.lastErr != nil {
		st.Error = v.lastErr.Error()
	}
	if v.p != nil {
		st.Pending = len(v.p.Outbox)
		st.SyncedAt = v.p.SyncedAt
	}
	return st
}

func (v *Vault) flushOutbox(src *appleservices.KeychainVault) (bool, error) {
	var (
		items   []keychain.Item
		web     []keychain.WebPassword
		touched = map[string]bool{}
		fetched bool
		changed bool
		dropped bool
	)
	for {
		op, ok := v.pendingOp()
		if !ok {
			if !dropped {
				v.clearFailure()
			}
			return changed, nil
		}
		if !fetched || touched[op.key()] {
			fresh, err := src.Items()
			if err != nil {
				v.releaseOp()
				return changed, err
			}
			items = fresh
			web = keychain.WebPasswords(fresh)
			fetched = true
			clear(touched)
		}
		if err := runOp(src, items, web, op); err != nil {
			v.releaseOp()
			if retryable(err) {
				return changed, err
			}
			v.recordFailure(op, err)
			if serr := v.settleOp(op.Seq); serr != nil {
				return changed, serr
			}
			changed = true
			dropped = true
			continue
		}
		touched[op.key()] = true
		if op.Rewrite {
			touched[op.NewDomain+"\x00"+op.NewUsername] = true
		}
		if err := v.settleOp(op.Seq); err != nil {
			return changed, err
		}
		changed = true
	}
}

func runOp(src *appleservices.KeychainVault, items []keychain.Item, web []keychain.WebPassword, op outboxOp) error {
	if op.Kind == opAdd {
		if w, ok := matchWeb(web, op, false); ok {
			return reconcileAdd(src, items, op, w)
		}
		if op.Manual {
			return src.AddManualPasswordWithID(op.Domain, op.Title, op.Username, op.Password, op.Note)
		}
		return src.AddWebPassword(op.Domain, op.Username, op.Password, op.Note)
	}

	deleted := op.Kind == opRestore || op.Kind == opPurge
	w, ok := matchWeb(web, op, deleted)
	if !ok {
		return nil
	}
	switch op.Kind {
	case opEdit:
		return runEdit(src, items, op, w)
	case opDelete:
		return src.DeleteWebPassword(w)
	case opRestore:
		return src.RestoreWebPassword(w)
	case opPurge:
		return src.PurgeWebPassword(w)
	}
	return fmt.Errorf("vault: unknown pending operation %q", op.Kind)
}

func reconcileAdd(src *appleservices.KeychainVault, items []keychain.Item, op outboxOp, w keychain.WebPassword) error {
	if op.Password != w.Password {
		if err := src.EditWebPasswordIn(items, w, op.Domain, op.Username, op.Password); err != nil {
			return err
		}
		w.Password = op.Password
	}
	cur := metadataOf(w)
	meta := cur
	if op.Manual && op.Title != "" {
		meta.Title = op.Title
	}
	meta.Note = op.Note
	if meta == cur {
		return nil
	}
	return src.SetMetadataIn(items, w, meta)
}

func metadataOf(w keychain.WebPassword) appleservices.Metadata {
	return appleservices.Metadata{Title: w.Name, Note: w.Note, TOTP: w.TOTP}
}

func runEdit(src *appleservices.KeychainVault, items []keychain.Item, op outboxOp, w keychain.WebPassword) error {
	if op.Rewrite {
		if err := src.EditWebPasswordIn(items, w, op.NewDomain, op.NewUsername, op.Password); err != nil {
			return err
		}
		w.Domain = op.NewDomain
		w.Username = op.NewUsername
		w.Password = op.Password
	}
	cur := metadataOf(w)
	meta := cur
	if op.SetNote {
		meta.Note = op.Note
	}
	switch {
	case op.TOTP != "":
		meta.TOTP = op.TOTP
	case op.DropTOTP:
		meta.TOTP = ""
	}
	if meta == cur {
		return nil
	}
	return src.SetMetadataIn(items, w, meta)
}

func matchWeb(web []keychain.WebPassword, op outboxOp, deleted bool) (keychain.WebPassword, bool) {
	for _, w := range web {
		if w.Domain == op.Domain && w.Username == op.Username && w.IsDeleted == deleted {
			return w, true
		}
	}
	return keychain.WebPassword{}, false
}

func retryable(err error) bool {
	if err == nil {
		return false
	}
	if errors.Is(err, apple.ErrTwoFactorRequired) || errors.Is(err, errLocked) {
		return true
	}
	if _, ok := errors.AsType[net.Error](err); ok {
		return true
	}
	if _, ok := errors.AsType[*url.Error](err); ok {
		return true
	}
	if _, ok := errors.AsType[*net.DNSError](err); ok {
		return true
	}
	return errors.Is(err, syscall.ECONNREFUSED) ||
		errors.Is(err, syscall.ECONNRESET) ||
		errors.Is(err, syscall.EHOSTUNREACH) ||
		errors.Is(err, syscall.ENETUNREACH) ||
		errors.Is(err, syscall.ENETDOWN) ||
		errors.Is(err, syscall.EPIPE)
}

func (v *Vault) recordFailure(op outboxOp, err error) {
	failure := fmt.Errorf("vault: dropped pending %s for %q: %w", op.Kind, op.Domain, err)
	log.Print(failure)
	v.mu.Lock()
	v.lastErr = failure
	v.mu.Unlock()
}

func (v *Vault) clearFailure() {
	v.mu.Lock()
	v.lastErr = nil
	v.mu.Unlock()
}
