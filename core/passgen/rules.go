package passgen

import (
	"slices"
	"strconv"
	"strings"
)

const (
	lowerSet   = "abcdefghijklmnopqrstuvwxyz"
	upperSet   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
	digitSet   = "0123456789"
	specialSet = "-~!@#$%^&*_+=`|(){}[:;\"'<>,.?]"

	symbolSlack = 6
)

type Rules struct {
	Required    [][]rune
	Allowed     []rune
	MinLength   int
	MaxLength   int
	Consecutive int
}

func (r Rules) Empty() bool {
	return len(r.Required) == 0 && len(r.Allowed) == 0 && r.MinLength == 0 && r.MaxLength == 0 && r.Consecutive == 0
}

func (r Rules) allowedSet() []rune {
	set := slices.Clone(r.Allowed)
	for _, group := range r.Required {
		set = union(set, group)
	}
	if len(set) == 0 {
		return []rune(lowerSet + upperSet + digitSet + specialSet)
	}
	return set
}

func (r Rules) Symbols() string {
	if len(r.Required) == 0 && len(r.Allowed) == 0 {
		return ""
	}
	var out []rune
	for _, c := range r.allowedSet() {
		if !strings.ContainsRune(lowerSet+upperSet+digitSet, c) {
			out = append(out, c)
		}
	}
	if len(out) == 0 || len(out) >= len([]rune(specialSet))-symbolSlack {
		return ""
	}
	return string(out)
}

func RulesFor(domain string) Rules {
	host := strings.ToLower(strings.TrimSuffix(strings.TrimSpace(domain), "."))
	for host != "" {
		if text, ok := quirkFor(host); ok {
			return ParseRules(text)
		}
		_, rest, found := strings.Cut(host, ".")
		if !found || !strings.Contains(rest, ".") {
			break
		}
		host = rest
	}
	return Rules{}
}

func ParseRules(text string) Rules {
	var rules Rules
	for _, part := range splitRules(text) {
		name, value, ok := strings.Cut(part, ":")
		if !ok {
			continue
		}
		value = strings.TrimSpace(value)
		switch strings.ToLower(strings.TrimSpace(name)) {
		case "required":
			if group := classes(value); len(group) > 0 {
				rules.Required = append(rules.Required, group)
			}
		case "allowed":
			rules.Allowed = union(rules.Allowed, classes(value))
		case "minlength":
			if n, err := strconv.Atoi(value); err == nil && n > 0 {
				rules.MinLength = max(rules.MinLength, n)
			}
		case "maxlength":
			if n, err := strconv.Atoi(value); err == nil && n > 0 && (rules.MaxLength == 0 || n < rules.MaxLength) {
				rules.MaxLength = n
			}
		case "max-consecutive":
			if n, err := strconv.Atoi(value); err == nil && n > 0 && (rules.Consecutive == 0 || n < rules.Consecutive) {
				rules.Consecutive = n
			}
		}
	}
	return rules
}

func splitRules(text string) []string {
	var parts []string
	var current strings.Builder
	depth := false
	runes := []rune(text)
	for i := 0; i < len(runes); i++ {
		c := runes[i]
		switch {
		case c == '[' && !depth:
			depth = true
		case c == ']' && depth:
			if i+1 < len(runes) && runes[i+1] == ']' {
				current.WriteRune(c)
				i++
			} else {
				depth = false
			}
		case c == ';' && !depth:
			parts = append(parts, current.String())
			current.Reset()
			continue
		}
		current.WriteRune(c)
	}
	if strings.TrimSpace(current.String()) != "" {
		parts = append(parts, current.String())
	}
	return parts
}

func classes(value string) []rune {
	var set []rune
	runes := []rune(value)
	for i := 0; i < len(runes); i++ {
		c := runes[i]
		if c == '[' {
			var custom []rune
			for i++; i < len(runes); i++ {
				if runes[i] == ']' {
					if i+1 < len(runes) && runes[i+1] == ']' {
						custom = append(custom, ']')
						i++
						continue
					}
					break
				}
				custom = append(custom, runes[i])
			}
			set = union(set, custom)
			continue
		}
		if c == ',' || c == ' ' {
			continue
		}
		start := i
		for i < len(runes) && runes[i] != ',' && runes[i] != ' ' && runes[i] != '[' {
			i++
		}
		name := strings.ToLower(string(runes[start:i]))
		i--
		switch name {
		case "upper":
			set = union(set, []rune(upperSet))
		case "lower":
			set = union(set, []rune(lowerSet))
		case "digit":
			set = union(set, []rune(digitSet))
		case "special":
			set = union(set, []rune(specialSet))
		case "ascii-printable", "unicode":
			set = union(set, []rune(lowerSet+upperSet+digitSet+specialSet))
		}
	}
	return set
}

func union(a, b []rune) []rune {
	out := slices.Clone(a)
	for _, c := range b {
		if !slices.Contains(out, c) {
			out = append(out, c)
		}
	}
	return out
}

func intersect(a, b []rune) []rune {
	var out []rune
	for _, c := range a {
		if slices.Contains(b, c) && !slices.Contains(out, c) {
			out = append(out, c)
		}
	}
	return out
}
