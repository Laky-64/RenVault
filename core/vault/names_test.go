package vault

import (
	"errors"
	"slices"
	"strings"
	"testing"
)

func TestSanitizeNames(t *testing.T) {
	got := sanitizeNames([]NameSwap{
		{From: "  Giulia ", To: " Luca "},
		{From: "", To: "Nobody"},
		{From: "giulia", To: "Duplicate"},
		{From: "Rossi", To: ""},
		{From: strings.Repeat("x", maxNameLength+1), To: "Long"},
	})
	want := []NameSwap{{From: "Giulia", To: "Luca"}, {From: "Rossi", To: ""}}
	if !slices.Equal(got, want) {
		t.Fatalf("sanitizeNames = %#v; want %#v", got, want)
	}

	many := make([]NameSwap, maxNames+5)
	for i := range many {
		many[i] = NameSwap{From: strings.Repeat("a", i+1), To: "b"}
	}
	if got := sanitizeNames(many); len(got) != maxNames {
		t.Fatalf("sanitizeNames kept %d entries; want %d", len(got), maxNames)
	}
}

func TestNamesNeedUnlockedVault(t *testing.T) {
	v := New(t.TempDir())
	if _, err := v.Names(); !errors.Is(err, errLocked) {
		t.Fatalf("Names on a locked vault = %v; want errLocked", err)
	}
	if err := v.SetNames([]NameSwap{{From: "Giulia", To: "Luca"}}); !errors.Is(err, errLocked) {
		t.Fatalf("SetNames on a locked vault = %v; want errLocked", err)
	}
}
