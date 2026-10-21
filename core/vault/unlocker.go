package vault

type Unlocker interface {
	Method() string
	Available() bool
	Protect(k []byte) (blob []byte, err error)
	Unlock(blob []byte) (k []byte, err error)
}

func availableUnlockers() []Unlocker {
	var out []Unlocker
	for _, u := range platformUnlockers() {
		if u.Available() {
			out = append(out, u)
		}
	}
	return out
}
