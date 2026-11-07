package passgen

import (
	"math"
	"unicode"
	"unicode/utf8"
)

type Strength int

const (
	StrengthNone Strength = iota
	StrengthWeak
	StrengthFair
	StrengthStrong
	StrengthVeryStrong
)

const (
	strengthMinLength = 8
	lowerPool         = 26
	upperPool         = 26
	digitPool         = 10
	symbolPool        = 33
	fairBits          = 45.0
	strongBits        = 70.0
	veryStrongBits    = 100.0
)

func StrengthOf(password string) Strength {
	if utf8.RuneCountInString(password) < strengthMinLength {
		return StrengthNone
	}
	bits := entropy(password)
	switch {
	case bits < fairBits:
		return StrengthWeak
	case bits < strongBits:
		return StrengthFair
	case bits < veryStrongBits:
		return StrengthStrong
	default:
		return StrengthVeryStrong
	}
}

func (s Strength) Weak() bool {
	return s <= StrengthWeak
}

func entropy(password string) float64 {
	var lower, upper, digit, symbol bool
	for _, c := range password {
		switch {
		case c >= 'a' && c <= 'z':
			lower = true
		case c >= 'A' && c <= 'Z':
			upper = true
		case unicode.IsDigit(c):
			digit = true
		case !unicode.IsLetter(c):
			symbol = true
		}
	}
	pool := 0
	for _, used := range []struct {
		on   bool
		size int
	}{{lower, lowerPool}, {upper, upperPool}, {digit, digitPool}, {symbol, symbolPool}} {
		if used.on {
			pool += used.size
		}
	}
	if pool == 0 {
		return 0
	}
	effective := 1
	runes := []rune(password)
	for i := 1; i < len(runes); i++ {
		if step := runes[i] - runes[i-1]; step != 0 && step != 1 {
			effective++
		}
	}
	return float64(effective) * math.Log2(float64(pool))
}
