package vault

import (
	"errors"
	"slices"
	"time"
)

const quickTTL = 7 * 24 * time.Hour

var (
	ErrCancelled    = errors.New("vault: verification cancelled")
	errQuickOff     = errors.New("vault: quick unlock is off")
	errQuickExpired = errors.New("vault: quick unlock expired, use the master password")
	errQuickMethod  = errors.New("vault: quick unlock is not available on this device")
)

type QuickState struct {
	Method  string
	Sealed  []byte
	Ready   bool
	Expired bool
}

func QuickMethods() []string {
	var out []string
	for _, u := range availableUnlockers() {
		out = append(out, u.Method())
	}
	return out
}

func unlockerFor(method string) Unlocker {
	for _, u := range availableUnlockers() {
		if u.Method() == method {
			return u
		}
	}
	return nil
}

func (h header) quick() (wrappedKey, bool) {
	for _, w := range h.Wraps {
		if w.Method != methodPassword && len(w.Sealed) > 0 {
			return w, true
		}
	}
	return wrappedKey{}, false
}

func (v *Vault) QuickState() (QuickState, error) {
	vf, ok, err := loadVaultFile(v.base)
	if err != nil || !ok {
		return QuickState{}, err
	}
	w, ok := vf.Header.quick()
	if !ok {
		return QuickState{}, nil
	}
	expired := time.Now().Unix() >= w.Until
	return QuickState{Method: w.Method, Sealed: w.Sealed, Ready: !expired, Expired: expired}, nil
}

func (v *Vault) EnableQuick(method string, q, sealed []byte, credential bool) error {
	if method == "" || method == methodPassword {
		return errQuickMethod
	}
	if len(q) == 0 {
		u := unlockerFor(method)
		if u == nil {
			return errQuickMethod
		}
		fresh, err := genKey()
		if err != nil {
			return err
		}
		defer zero(fresh)
		if sealed, err = u.Protect(fresh, credential); err != nil {
			return err
		}
		q = fresh
	}
	if len(q) != 32 || len(sealed) == 0 {
		return errors.New("vault: invalid quick unlock key")
	}
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.vf == nil || v.k == nil {
		return errLocked
	}
	blob, err := wrapKey(q, v.k)
	if err != nil {
		return err
	}
	wraps := slices.DeleteFunc(slices.Clone(v.vf.Header.Wraps), func(w wrappedKey) bool { return w.Method != methodPassword })
	wraps = append(wraps, wrappedKey{Method: method, Blob: blob, Sealed: sealed, Until: time.Now().Add(quickTTL).Unix()})
	return v.rewrapLocked(wraps)
}

func (v *Vault) DisableQuick() error {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.vf == nil || v.k == nil {
		return errLocked
	}
	wraps := slices.DeleteFunc(slices.Clone(v.vf.Header.Wraps), func(w wrappedKey) bool { return w.Method != methodPassword })
	if len(wraps) == len(v.vf.Header.Wraps) {
		return nil
	}
	return v.rewrapLocked(wraps)
}

func (v *Vault) UnlockWithQuick(method string, q []byte) error {
	vf, ok, err := loadVaultFile(v.base)
	if err != nil {
		return err
	}
	if !ok {
		return errNoVault
	}
	w, ok := vf.Header.quick()
	if !ok || w.Method != method {
		return errQuickOff
	}
	if time.Now().Unix() >= w.Until {
		return errQuickExpired
	}
	if len(q) == 0 {
		u := unlockerFor(method)
		if u == nil {
			return errQuickMethod
		}
		if q, err = u.Unlock(w.Sealed); err != nil {
			return err
		}
	}
	defer zero(q)
	k, err := unwrapKey(q, w.Blob)
	if err != nil {
		return errQuickOff
	}
	p, err := decryptPayload(k, vf)
	if err != nil {
		zero(k)
		return err
	}
	v.mu.Lock()
	defer v.mu.Unlock()
	v.adoptLocked(vf, k, p)
	return nil
}

func (v *Vault) renewQuickLocked() {
	if _, ok := v.vf.Header.quick(); !ok {
		return
	}
	wraps := slices.Clone(v.vf.Header.Wraps)
	for i := range wraps {
		if wraps[i].Method != methodPassword {
			wraps[i].Until = time.Now().Add(quickTTL).Unix()
		}
	}
	_ = v.rewrapLocked(wraps)
}

func (v *Vault) rewrapLocked(wraps []wrappedKey) error {
	prev := v.vf.Header.Wraps
	v.vf.Header.Wraps = wraps
	if err := v.saveLocked(); err != nil {
		v.vf.Header.Wraps = prev
		return err
	}
	return nil
}
