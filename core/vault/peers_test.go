package vault

import (
	"context"
	"os"
	"slices"
	"testing"
	"time"

	"github.com/Laky-64/appleservices/keychain"
)

func TestMain(m *testing.M) {
	SetPlatformSecret(make([]byte, 32))
	os.Exit(m.Run())
}

func newLocalVault(t *testing.T, name, deviceName string) *Vault {
	t.Helper()
	v := New(t.TempDir())
	if err := v.FinishLocalSetup("correct horse battery", name, deviceName, nil); err != nil {
		t.Fatalf("setup %s: %v", deviceName, err)
	}
	v.SetAddresses([]string{"127.0.0.1"})
	t.Cleanup(v.Lock)
	return v
}

func pair(t *testing.T, host, joiner *Vault) {
	t.Helper()
	info, err := host.StartPairing()
	if err != nil {
		t.Fatalf("start pairing: %v", err)
	}
	ctx, cancel := context.WithTimeout(t.Context(), 20*time.Second)
	defer cancel()
	waited := make(chan error, 1)
	go func() {
		_, err := host.WaitPairing(ctx)
		waited <- err
	}()
	if _, err := joiner.JoinPairing(ctx, info.Payload); err != nil {
		t.Fatalf("join: %v", err)
	}
	if err := <-waited; err != nil {
		t.Fatalf("wait: %v", err)
	}
}

func titles(v *Vault) []string {
	var out []string
	for _, w := range v.ListWeb() {
		if !w.IsDeleted {
			out = append(out, w.Title)
		}
	}
	slices.Sort(out)
	return out
}

func TestPairAndMerge(t *testing.T) {
	a := newLocalVault(t, "Lauren", "Phone")
	b := newLocalVault(t, "", "Laptop")

	if err := a.AddPassword("", "example.com", "me", "one", ""); err != nil {
		t.Fatal(err)
	}
	if err := b.AddPassword("Router", "", "admin", "two", ""); err != nil {
		t.Fatal(err)
	}

	pair(t, a, b)
	if err := a.syncPeers(); err != nil {
		t.Fatal(err)
	}

	want := []string{"Router", "example.com"}
	if got := titles(a); !slices.Equal(got, want) {
		t.Fatalf("a titles = %v", got)
	}
	if got := titles(b); !slices.Equal(got, want) {
		t.Fatalf("b titles = %v", got)
	}
	if got := b.ProfileInfo().Name; got != "Lauren" {
		t.Fatalf("b profile = %q", got)
	}

	id := ""
	for _, w := range b.ListWeb() {
		if w.Title == "Router" {
			id = w.ID
		}
	}
	if err := b.DeletePassword(id); err != nil {
		t.Fatal(err)
	}
	if err := b.syncPeers(); err != nil {
		t.Fatal(err)
	}
	if got := titles(a); !slices.Equal(got, []string{"example.com"}) {
		t.Fatalf("a after delete = %v", got)
	}

	d := newLocalVault(t, "", "Old phone")
	pair(t, a, d)
	if err := d.SignOut(); err != nil {
		t.Fatal(err)
	}
	if devices, _ := a.Devices(); len(devices) != 2 {
		t.Fatalf("a devices after d left = %+v", devices)
	}

	c := newLocalVault(t, "", "Tablet")
	pair(t, b, c)
	if err := c.SetProfile("Lauren R", nil, false); err != nil {
		t.Fatal(err)
	}
	if err := c.syncPeers(); err != nil {
		t.Fatal(err)
	}
	if err := a.syncPeers(); err != nil {
		t.Fatal(err)
	}
	devices, err := a.Devices()
	if err != nil {
		t.Fatal(err)
	}
	if len(devices) != 3 {
		t.Fatalf("a devices = %+v", devices)
	}
	if got := a.ProfileInfo().Name; got != "Lauren R" {
		t.Fatalf("a profile after c = %q", got)
	}

	if err := a.RemoveDevice(c.NetInfo().Node); err != nil {
		t.Fatal(err)
	}
	if err := a.syncPeers(); err != nil {
		t.Fatal(err)
	}
	if err := c.SetProfile("Nope", nil, false); err != nil {
		t.Fatal(err)
	}
	if err := c.syncPeers(); err != nil {
		t.Fatal(err)
	}
	if got := b.ProfileInfo().Name; got == "Nope" {
		t.Fatal("removed device still syncs")
	}
}

func TestPairWrongCode(t *testing.T) {
	a := newLocalVault(t, "", "Phone")
	b := newLocalVault(t, "", "Laptop")
	info, err := a.StartPairing()
	if err != nil {
		t.Fatal(err)
	}
	wrong := "00000000"
	if info.Code == wrong {
		wrong = "11111111"
	}
	payload := "renvault:pair?a=" + a.advertisedFirst() + "&c=" + wrong
	if _, err := b.JoinPairing(t.Context(), payload); err == nil {
		t.Fatal("joined with a wrong code")
	}
	if _, err := b.JoinPairing(t.Context(), info.Payload); err == nil {
		t.Fatal("pairing session survived a failed attempt")
	}
}

func (v *Vault) advertisedFirst() string {
	v.mu.Lock()
	defer v.mu.Unlock()
	return v.advertised()[0]
}

func TestMergeLastWriterWins(t *testing.T) {
	key := make([]byte, 32)
	a := &share{shareFile: shareFile{Node: "a"}}
	b := &share{shareFile: shareFile{Node: "b"}}
	if err := a.putWeb(key, "rec", webFixture("site.com", "old")); err != nil {
		t.Fatal(err)
	}
	b.merge(a.answer(b.digest()))
	if len(b.Records) != 1 {
		t.Fatalf("b records = %d", len(b.Records))
	}
	_ = a.putWeb(key, "rec", webFixture("site.com", "from-a"))
	time.Sleep(2 * time.Millisecond)
	_ = b.putWeb(key, "rec", webFixture("site.com", "from-b"))
	a.merge(b.answer(a.digest()))
	b.merge(a.answer(b.digest()))
	for name, sh := range map[string]*share{"a": a, "b": b} {
		plain, err := openWith(key, sh.Records[0].ad(), sh.Records[0].Box)
		if err != nil {
			t.Fatal(err)
		}
		var w keychain.WebPassword
		_ = cborDec.Unmarshal(plain, &w)
		if w.Password != "from-b" {
			t.Fatalf("%s password = %q", name, w.Password)
		}
	}
}

func TestSyncWhileLocked(t *testing.T) {
	a := newLocalVault(t, "Lauren", "Phone")
	b := newLocalVault(t, "", "Laptop")
	if err := b.AddPassword("Router", "", "admin", "two", ""); err != nil {
		t.Fatal(err)
	}
	pair(t, a, b)
	if got := titles(a); !slices.Equal(got, []string{"Router"}) {
		t.Fatalf("a titles after pairing = %v", got)
	}

	b.Lock()
	if err := a.AddPassword("", "example.com", "me", "one", ""); err != nil {
		t.Fatal(err)
	}
	if err := a.syncPeers(); err != nil {
		t.Fatal(err)
	}
	reopened := New(b.base)
	t.Cleanup(reopened.Lock)
	b.Lock()
	if err := reopened.UnlockWithPassword("correct horse battery"); err != nil {
		t.Fatal(err)
	}
	if got := titles(reopened); !slices.Equal(got, []string{"Router", "example.com"}) {
		t.Fatalf("b titles after locked sync = %v", got)
	}
	if got := reopened.ProfileInfo().Name; got != "Lauren" {
		t.Fatalf("b profile = %q", got)
	}
}

func webFixture(domain, password string) keychain.WebPassword {
	return keychain.WebPassword{Name: domain, Domain: domain, Website: true, Username: "me", Password: password}
}
