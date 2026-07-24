package apple

import "errors"

var (
	ErrTwoFactorRequired = errors.New("apple: two-factor code required")
	ErrNoBottles         = errors.New("apple: no recoverable devices for this account")
	ErrNoLogin           = errors.New("apple: login not started")
	ErrNoPeer            = errors.New("apple: no recovered peer")
	ErrBottleIndex       = errors.New("apple: bottle index out of range")
	ErrNoProfile         = errors.New("apple: profile not loaded")
)
