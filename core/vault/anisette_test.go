package vault

import "testing"

func TestNormalizeAnisette(t *testing.T) {
	cases := map[string]string{
		"":                             "",
		"   ":                          "",
		"ani.sidestore.io":             "https://ani.sidestore.io",
		"https://ani.example.com/":     "https://ani.example.com",
		"http://192.168.1.10:6969":     "http://192.168.1.10:6969",
		" https://ani.example.com/v3/": "https://ani.example.com/v3",
	}
	for in, want := range cases {
		got, err := normalizeAnisette(in)
		if err != nil || got != want {
			t.Errorf("normalizeAnisette(%q) = %q, %v; want %q", in, got, err, want)
		}
	}
	for _, bad := range []string{"ftp://ani.example.com", "https://", "://"} {
		if _, err := normalizeAnisette(bad); err == nil {
			t.Errorf("normalizeAnisette(%q) accepted a bad address", bad)
		}
	}
}

func TestAnisetteServerRoundTrip(t *testing.T) {
	v := New(t.TempDir())
	if got := v.AnisetteServer(); got != "" {
		t.Fatalf("fresh vault has server %q", got)
	}
	if err := v.SetAnisetteServer("ani.example.com"); err != nil {
		t.Fatal(err)
	}
	if got := New(v.base).AnisetteServer(); got != "https://ani.example.com" {
		t.Fatalf("reopened vault has server %q", got)
	}
	if err := v.SetAnisetteServer(""); err != nil {
		t.Fatal(err)
	}
	if got := v.AnisetteServer(); got != "" {
		t.Fatalf("reset left server %q", got)
	}
}
