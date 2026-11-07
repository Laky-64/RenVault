package passgen

import (
	"crypto/rand"
	_ "embed"
	"errors"
	"math"
	"math/big"
	"slices"
	"strings"
	"sync"
	"unicode"
)

const (
	KindApple  = "apple"
	KindRandom = "random"
	KindWords  = "words"

	DefaultLength = 20
	DefaultWords  = 4
	MinLength     = 8
	MaxLength     = 64
	MinWords      = 3
	MaxWords      = 10

	appleGroups    = 3
	appleGroupSize = 6
	appleLength    = appleGroups*appleGroupSize + appleGroups - 1
	consonants     = "bcdfghjkmnpqrstvwxz"
	vowels         = "aeiouy"
	symbolSet      = "!#$%&*+-=?@^_"
	attempts       = 200
)

//go:embed words-en.txt
var wordsText string

var (
	wordsOnce sync.Once
	wordList  []string
)

var errUnsatisfiable = errors.New("passgen: the site rules cannot be satisfied")

type Options struct {
	Kind    string
	Length  int
	Digits  bool
	Symbols bool
	Words   int
}

type Result struct {
	Password string
	Kind     string
	Bits     float64
}

func Bounds(rules Rules) (int, int) {
	low := max(MinLength, rules.MinLength)
	high := MaxLength
	if rules.MaxLength > 0 {
		high = min(high, rules.MaxLength)
	}
	return min(low, high), high
}

func Generate(options Options, rules Rules) (Result, error) {
	switch options.Kind {
	case KindApple:
		if password, bits := apple(); fits(password, rules) {
			return Result{Password: password, Kind: KindApple, Bits: bits}, nil
		}
	case KindWords:
		if password, bits := words(options); fits(password, rules) {
			return Result{Password: password, Kind: KindWords, Bits: bits}, nil
		}
	}
	password, bits, err := random(options, rules)
	return Result{Password: password, Kind: KindRandom, Bits: bits}, err
}

func Blocked(options Options, rules Rules) []string {
	var blocked []string
	if password, _ := apple(); !fits(password, rules) {
		blocked = append(blocked, KindApple)
	}
	if password, _ := words(options); !fits(password, rules) {
		blocked = append(blocked, KindWords)
	}
	return blocked
}

func Needs(rules Rules) (digits, symbols bool) {
	letters := []rune(lowerSet + upperSet)
	numbers := []rune(digitSet)
	for _, group := range rules.Required {
		if len(intersect(group, letters)) > 0 {
			continue
		}
		if len(intersect(group, numbers)) > 0 {
			digits = true
		} else {
			symbols = true
		}
	}
	return digits, symbols
}

func random(options Options, rules Rules) (string, float64, error) {
	allowed := rules.allowedSet()
	symbols := []rune(symbolSet)
	if restricted := rules.Symbols(); restricted != "" {
		symbols = []rune(restricted)
	}
	needDigits, needSymbols := Needs(rules)
	options.Digits = options.Digits || needDigits
	options.Symbols = options.Symbols || needSymbols
	wanted := [][]rune{[]rune(lowerSet), []rune(upperSet)}
	if options.Digits {
		wanted = append(wanted, []rune(digitSet))
	}
	if options.Symbols {
		wanted = append(wanted, symbols)
	}
	var pool []rune
	for _, set := range wanted {
		pool = union(pool, intersect(set, allowed))
	}
	for _, group := range rules.Required {
		if len(intersect(group, pool)) == 0 {
			pool = union(pool, intersect(group, allowed))
		}
	}
	if len(pool) < 2 {
		return "", 0, errUnsatisfiable
	}
	var mandatory [][]rune
	for _, set := range append(slices.Clone(wanted), rules.Required...) {
		if part := intersect(set, pool); len(part) > 0 {
			mandatory = append(mandatory, part)
		}
	}
	low, high := Bounds(rules)
	length := min(max(options.Length, low, len(mandatory)), high)
	if length < len(mandatory) {
		mandatory = mandatory[:length]
	}
	for range attempts {
		chars := make([]rune, 0, length)
		for _, set := range mandatory {
			chars = append(chars, pickRune(set))
		}
		for len(chars) < length {
			chars = append(chars, pickRune(pool))
		}
		shuffle(chars)
		if password := string(chars); fits(password, rules) {
			return password, float64(length) * math.Log2(float64(len(pool))), nil
		}
	}
	return "", 0, errUnsatisfiable
}

func apple() (string, float64) {
	groups := make([][]rune, appleGroups)
	for i := range groups {
		group := make([]rune, appleGroupSize)
		for j := range group {
			if j%3 == 1 {
				group[j] = pickRune([]rune(vowels))
			} else {
				group[j] = pickRune([]rune(consonants))
			}
		}
		groups[i] = group
	}
	edges := appleGroups * 2
	slot := pick(edges)
	digitGroup, digitAt := slot/2, 0
	if slot%2 == 1 {
		digitAt = appleGroupSize - 1
	}
	groups[digitGroup][digitAt] = rune('0' + pick(10))
	letters := appleGroups*appleGroupSize - 1
	upper := pick(letters)
	for i, group := range groups {
		for j := range group {
			if i == digitGroup && j == digitAt {
				continue
			}
			if upper == 0 {
				group[j] = unicode.ToUpper(group[j])
			}
			upper--
		}
	}
	parts := make([]string, appleGroups)
	for i, group := range groups {
		parts[i] = string(group)
	}
	perGroup := 4*math.Log2(float64(len(consonants))) + 2*math.Log2(float64(len(vowels)))
	bits := float64(appleGroups)*perGroup + math.Log2(float64(edges*10)) + math.Log2(float64(letters))
	return strings.Join(parts, "-"), bits
}

func words(options Options) (string, float64) {
	wordsOnce.Do(func() {
		wordList = strings.Fields(wordsText)
	})
	count := options.Words
	if count == 0 {
		count = DefaultWords
	}
	count = min(max(count, MinWords), MaxWords)
	parts := make([]string, count)
	for i := range parts {
		word := []rune(wordList[pick(len(wordList))])
		word[0] = unicode.ToUpper(word[0])
		parts[i] = string(word)
	}
	bits := float64(count) * math.Log2(float64(len(wordList)))
	if options.Digits {
		parts[count-1] += string(rune('0' + pick(10)))
		bits += math.Log2(10)
	}
	return strings.Join(parts, "-"), bits
}

func Check(password string, rules Rules) []string {
	chars := []rune(password)
	if len(chars) == 0 || rules.Empty() {
		return nil
	}
	var problems []string
	if rules.MaxLength > 0 && len(chars) > rules.MaxLength {
		problems = append(problems, "long")
	}
	if rules.MinLength > 0 && len(chars) < rules.MinLength {
		problems = append(problems, "short")
	}
	if len(rules.Required) > 0 || len(rules.Allowed) > 0 {
		allowed := rules.allowedSet()
		for _, c := range chars {
			if !slices.Contains(allowed, c) {
				problems = append(problems, "char:"+string(c))
				break
			}
		}
	}
	for _, group := range rules.Required {
		if slices.ContainsFunc(chars, func(c rune) bool { return slices.Contains(group, c) }) {
			continue
		}
		problems = append(problems, missing(group))
	}
	if rules.Consecutive > 0 && !fits(password, Rules{Consecutive: rules.Consecutive}) {
		problems = append(problems, "repeat")
	}
	return slices.Compact(problems)
}

func missing(group []rune) string {
	lower := len(intersect(group, []rune(lowerSet))) > 0
	upper := len(intersect(group, []rune(upperSet))) > 0
	digit := len(intersect(group, []rune(digitSet))) > 0
	switch {
	case lower && upper:
		return "letter"
	case upper:
		return "upper"
	case lower:
		return "lower"
	case digit:
		return "digit"
	default:
		return "symbol"
	}
}

func fits(password string, rules Rules) bool {
	chars := []rune(password)
	if rules.MinLength > 0 && len(chars) < rules.MinLength {
		return false
	}
	if rules.MaxLength > 0 && len(chars) > rules.MaxLength {
		return false
	}
	allowed := rules.allowedSet()
	for _, c := range chars {
		if !slices.Contains(allowed, c) {
			return false
		}
	}
	for _, group := range rules.Required {
		if !slices.ContainsFunc(chars, func(c rune) bool { return slices.Contains(group, c) }) {
			return false
		}
	}
	if rules.Consecutive > 0 {
		run := 0
		for i := range chars {
			if i > 0 && chars[i] == chars[i-1] {
				run++
			} else {
				run = 1
			}
			if run > rules.Consecutive {
				return false
			}
		}
	}
	return true
}

func pick(n int) int {
	value, err := rand.Int(rand.Reader, big.NewInt(int64(n)))
	if err != nil {
		panic(err)
	}
	return int(value.Int64())
}

func pickRune(set []rune) rune {
	return set[pick(len(set))]
}

func shuffle(chars []rune) {
	for i := len(chars) - 1; i > 0; i-- {
		j := pick(i + 1)
		chars[i], chars[j] = chars[j], chars[i]
	}
}
