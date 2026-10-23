package vault

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"log"
	"math/big"
	"net"
	"net/url"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/flynn/noise"
	"golang.org/x/crypto/chacha20poly1305"
	"rsc.io/qr"
)

const (
	listenPort     = 47823
	dialTimeout    = 3 * time.Second
	pairingTTL     = 10 * time.Minute
	pairCodeDigits = 8
	pairScheme     = "renvault"
	maxInbound     = 4
	seenTTL        = 30 * time.Minute
	leaveTimeout   = 6 * time.Second
	seenWait       = 8 * time.Second
	seenPoll       = 250 * time.Millisecond
)

type hello struct {
	Node  string   `cbor:"node"`
	Name  string   `cbor:"name"`
	Addrs []string `cbor:"addrs,omitempty"`
}

type pairing struct {
	code    string
	session []byte
	expires time.Time
	done    chan pairResult
}

type pairResult struct {
	name string
	err  error
}

type seenHost struct {
	addr string
	at   time.Time
}

type PairInfo struct {
	Code    string
	Payload string
	Size    int
	Modules []byte
}

type NetInfo struct {
	Node string
	Port int
}

type welcome struct {
	Key []byte `cbor:"key"`
}

type peerNet struct {
	ln   net.Listener
	wake chan struct{}
	stop chan struct{}
}

func (v *Vault) startNetLocked() {
	if v.net != nil || v.sh == nil || v.sh.identity == nil {
		return
	}
	ln, err := net.Listen("tcp", ":"+strconv.Itoa(listenPort))
	if err != nil {
		ln, err = net.Listen("tcp", ":0")
	}
	if err != nil {
		log.Printf("vault: peer listener: %v", err)
		return
	}
	n := &peerNet{ln: ln, wake: make(chan struct{}, 1), stop: make(chan struct{})}
	v.net = n
	go n.accept(v)
	go n.run(v)
}

func (v *Vault) stopNetLocked() {
	if v.net == nil {
		return
	}
	close(v.net.stop)
	_ = v.net.ln.Close()
	v.net = nil
}

func (n *peerNet) accept(v *Vault) {
	slots := make(chan struct{}, maxInbound)
	for {
		conn, err := n.ln.Accept()
		if err != nil {
			return
		}
		select {
		case slots <- struct{}{}:
			go func() {
				defer func() { <-slots }()
				v.serve(conn)
			}()
		default:
			_ = conn.Close()
		}
	}
}

func (n *peerNet) run(v *Vault) {
	for {
		_ = v.syncPeers()
		select {
		case <-n.stop:
			return
		case <-n.wake:
		case <-time.After(syncInterval):
		}
	}
}

func (n *peerNet) port() int {
	if n == nil {
		return 0
	}
	return n.ln.Addr().(*net.TCPAddr).Port
}

func (n *peerNet) kick() {
	if n == nil {
		return
	}
	select {
	case n.wake <- struct{}{}:
	default:
	}
}

func (v *Vault) NetInfo() NetInfo {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.sh == nil || v.net == nil {
		return NetInfo{}
	}
	return NetInfo{Node: v.sh.Node, Port: v.net.port()}
}

func (v *Vault) SetAddresses(ips []string) {
	v.mu.Lock()
	defer v.mu.Unlock()
	v.ips = slices.Clone(ips)
}

func (v *Vault) PeerSeen(node, host string, port int) {
	if node == "" || host == "" || port <= 0 {
		return
	}
	addr := net.JoinHostPort(host, strconv.Itoa(port))
	v.mu.Lock()
	if v.sh != nil && node == v.sh.Node {
		v.mu.Unlock()
		return
	}
	if v.seen == nil {
		v.seen = map[string]seenHost{}
	}
	v.seen[node] = seenHost{addr: addr, at: time.Now()}
	known := false
	if v.sh != nil {
		if i := v.sh.device(node); i >= 0 && !v.sh.Devices[i].Removed {
			v.sh.remember(node, addr)
			known = true
		}
	}
	v.mu.Unlock()
	if known {
		v.kick()
	}
}

func (v *Vault) SyncPeers() error {
	if !v.Local() {
		return errLocalOnly
	}
	return v.syncPeers()
}

func (v *Vault) advertised() []string {
	port := v.net.port()
	if port == 0 {
		return nil
	}
	ips := slices.Clone(v.ips)
	if len(ips) == 0 {
		ips = interfaceIPs()
	}
	out := make([]string, 0, len(ips))
	for _, ip := range ips {
		out = append(out, net.JoinHostPort(ip, strconv.Itoa(port)))
	}
	return out
}

func interfaceIPs() []string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return nil
	}
	var out []string
	for _, a := range addrs {
		n, ok := a.(*net.IPNet)
		if !ok || n.IP.IsLoopback() || n.IP.IsLinkLocalUnicast() {
			continue
		}
		out = append(out, n.IP.String())
	}
	return out
}

func (v *Vault) selfHello() (hello, noise.DHKey, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.sh == nil || v.sh.identity == nil {
		return hello{}, noise.DHKey{}, errNoShare
	}
	key, err := identityKey(v.sh.identity)
	if err != nil {
		return hello{}, noise.DHKey{}, err
	}
	name := ""
	if i := v.sh.device(v.sh.Node); i >= 0 {
		name = v.sh.deviceName(v.sharedKeyLocked(), v.sh.Devices[i])
	}
	return hello{Node: v.sh.Node, Name: name, Addrs: v.advertised()}, key, nil
}

func (v *Vault) serve(conn net.Conn) {
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(linkTimeout))
	mode, err := readFrame(conn)
	if err != nil || len(mode) != 1 {
		return
	}
	self, key, err := v.selfHello()
	if err != nil {
		return
	}
	switch mode[0] {
	case modeSync:
		l, err := handshake(conn, false, key, nil)
		if err != nil {
			return
		}
		if _, ok := v.trustedPeer(l.peer); !ok {
			return
		}
		if err := v.respond(l, self, remoteIP(conn)); err != nil {
			log.Printf("vault: inbound sync: %v", err)
		}
	case modePair:
		v.hostPair(conn, self, key)
	}
}

func remoteIP(conn net.Conn) string {
	if a, ok := conn.RemoteAddr().(*net.TCPAddr); ok {
		return a.IP.String()
	}
	return ""
}

func (v *Vault) trustedPeer(key []byte) (sealedDevice, bool) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.sh == nil {
		return sealedDevice{}, false
	}
	return v.sh.trusted(key)
}

func (v *Vault) learn(peer hello, key []byte, ip string, pairing bool) error {
	v.mu.Lock()
	defer v.mu.Unlock()
	sh := v.sh
	if sh == nil {
		return errNoShare
	}
	if peer.Node == "" || peer.Node == sh.Node {
		return errUntrusted
	}
	i := sh.device(peer.Node)
	switch {
	case pairing && (i < 0 || sh.Devices[i].Removed):
		shared := v.sharedKeyLocked()
		if shared == nil {
			return errLocked
		}
		if err := sh.putDevice(shared, sealedDevice{ID: peer.Node, Key: key}, peer.Name); err != nil {
			return err
		}
	case i < 0 || sh.Devices[i].Removed || !slices.Equal(sh.Devices[i].Key, key):
		return errUntrusted
	}
	addrs := slices.Clone(peer.Addrs)
	if ip != "" {
		for _, a := range peer.Addrs {
			if _, port, err := net.SplitHostPort(a); err == nil {
				addrs = append([]string{net.JoinHostPort(ip, port)}, addrs...)
				break
			}
		}
	}
	sh.remember(peer.Node, addrs...)
	return nil
}

func (v *Vault) respond(l *link, self hello, ip string) error {
	var peer hello
	if err := l.read(&peer); err != nil {
		return err
	}
	if err := l.write(self); err != nil {
		return err
	}
	if err := v.learn(peer, l.peer, ip, false); err != nil {
		return err
	}
	return v.answerSync(l, peer.Node)
}

func (v *Vault) answerSync(l *link, node string) error {
	var remote digest
	if err := l.read(&remote); err != nil {
		return err
	}
	v.mu.Lock()
	if v.sh == nil {
		v.mu.Unlock()
		return errNoShare
	}
	out := v.sh.answer(remote)
	v.mu.Unlock()
	if err := l.write(out); err != nil {
		return err
	}
	var back delta
	if err := l.read(&back); err != nil {
		return err
	}
	if err := v.absorb(back, node); err != nil {
		return err
	}
	return l.write(true)
}

func (v *Vault) driveSync(l *link, node string) error {
	v.mu.Lock()
	if v.sh == nil {
		v.mu.Unlock()
		return errNoShare
	}
	mine := v.sh.digest()
	v.mu.Unlock()
	if err := l.write(mine); err != nil {
		return err
	}
	var in delta
	if err := l.read(&in); err != nil {
		return err
	}
	if err := v.absorb(in, node); err != nil {
		return err
	}
	v.mu.Lock()
	if v.sh == nil {
		v.mu.Unlock()
		return errNoShare
	}
	out := v.sh.fulfil(in.Want)
	v.mu.Unlock()
	if err := l.write(out); err != nil {
		return err
	}
	var ack bool
	return l.read(&ack)
}

func (v *Vault) absorb(in delta, node string) error {
	v.mu.Lock()
	sh := v.sh
	if sh == nil {
		v.mu.Unlock()
		return errNoShare
	}
	res := sh.merge(in)
	if sh.Peers == nil {
		sh.Peers = map[string]peerState{}
	}
	st := sh.Peers[node]
	st.SyncedAt = time.Now().UTC()
	sh.Peers[node] = st
	if res.changed && v.sharedKeyLocked() != nil {
		v.rebuildShareLocked()
	}
	err := saveShare(v.base, sh)
	onSync, onProfile, onPeers := v.onSync, v.onProfile, v.onPeers
	v.mu.Unlock()
	if err != nil {
		return err
	}
	if res.changed && onSync != nil {
		onSync()
	}
	if res.profile && onProfile != nil {
		onProfile()
	}
	if onPeers != nil {
		onPeers()
	}
	return nil
}

type peerTarget struct {
	node  string
	key   []byte
	addrs []string
}

func (v *Vault) peerTargets() ([]peerTarget, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.sh == nil || v.sh.identity == nil {
		return nil, errNoShare
	}
	var out []peerTarget
	for _, d := range v.sh.Devices {
		if d.Removed || d.ID == v.sh.Node {
			continue
		}
		addrs := slices.Clone(v.sh.Peers[d.ID].Addrs)
		if s, ok := v.seen[d.ID]; ok && time.Since(s.at) < seenTTL && !slices.Contains(addrs, s.addr) {
			addrs = append([]string{s.addr}, addrs...)
		}
		out = append(out, peerTarget{node: d.ID, key: d.Key, addrs: addrs})
	}
	return out, nil
}

func (v *Vault) syncPeers() error {
	targets, err := v.peerTargets()
	if err != nil {
		return err
	}
	for _, t := range targets {
		for _, addr := range t.addrs {
			err := v.syncWith(t, addr)
			if err == nil {
				break
			}
			if !errors.Is(err, errDial) {
				log.Printf("vault: sync with %s: %v", addr, err)
			}
		}
	}
	return nil
}

var errDial = errors.New("vault: peer unreachable")

func (v *Vault) syncWith(t peerTarget, addr string) error {
	self, key, err := v.selfHello()
	if err != nil {
		return err
	}
	conn, err := net.DialTimeout("tcp", addr, dialTimeout)
	if err != nil {
		return errDial
	}
	defer conn.Close()
	l, err := dialSync(conn, key)
	if err != nil {
		return err
	}
	if !slices.Equal(l.peer, t.key) {
		return errUntrusted
	}
	if err := l.write(self); err != nil {
		return err
	}
	var peer hello
	if err := l.read(&peer); err != nil {
		return err
	}
	if peer.Node != t.node {
		return errUntrusted
	}
	if err := v.learn(peer, l.peer, remoteIP(conn), false); err != nil {
		return err
	}
	return v.driveSync(l, peer.Node)
}

func pairCode() (string, error) {
	limit := big.NewInt(1)
	for range pairCodeDigits {
		limit.Mul(limit, big.NewInt(10))
	}
	n, err := rand.Int(rand.Reader, limit)
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%0*d", pairCodeDigits, n), nil
}

func (v *Vault) StartPairing() (PairInfo, error) {
	code, err := pairCode()
	if err != nil {
		return PairInfo{}, err
	}
	session := make([]byte, 16)
	if _, err := rand.Read(session); err != nil {
		return PairInfo{}, err
	}
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.sh == nil {
		return PairInfo{}, errLocalOnly
	}
	if v.sharedKeyLocked() == nil {
		return PairInfo{}, errLocked
	}
	addrs := v.advertised()
	if len(addrs) == 0 {
		return PairInfo{}, errors.New("vault: not connected to a network")
	}
	v.resetTimerLocked()
	v.pairing = &pairing{
		code:    code,
		session: session,
		expires: time.Now().Add(pairingTTL),
		done:    make(chan pairResult, 1),
	}
	q := url.Values{"c": {code}, "a": {strings.Join(addrs, ",")}}
	payload := pairScheme + ":pair?" + q.Encode()
	matrix, err := qr.Encode(payload, qr.M)
	if err != nil {
		return PairInfo{}, err
	}
	modules := make([]byte, matrix.Size*matrix.Size)
	for y := range matrix.Size {
		for x := range matrix.Size {
			if matrix.Black(x, y) {
				modules[y*matrix.Size+x] = 1
			}
		}
	}
	return PairInfo{Code: code, Payload: payload, Size: matrix.Size, Modules: modules}, nil
}

func (v *Vault) WaitPairing(ctx context.Context) (string, error) {
	v.mu.Lock()
	pending := v.pairing
	v.mu.Unlock()
	if pending == nil {
		return "", errNoPairing
	}
	timer := time.NewTimer(time.Until(pending.expires))
	defer timer.Stop()
	select {
	case res := <-pending.done:
		return res.name, res.err
	case <-ctx.Done():
		v.cancelPairing(pending)
		return "", ctx.Err()
	case <-timer.C:
		v.cancelPairing(pending)
		return "", errNoPairing
	}
}

func (v *Vault) cancelPairing(p *pairing) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.pairing == p {
		v.pairing = nil
	}
}

func (v *Vault) takePairing() *pairing {
	v.mu.Lock()
	defer v.mu.Unlock()
	p := v.pairing
	v.pairing = nil
	if p == nil || time.Now().After(p.expires) {
		return nil
	}
	return p
}

func (v *Vault) hostPair(conn net.Conn, self hello, key noise.DHKey) {
	p := v.takePairing()
	if p == nil {
		_ = writeFrame(conn, nil)
		return
	}
	name, err := v.completePair(conn, p, self, key)
	p.done <- pairResult{name: name, err: err}
}

func (v *Vault) completePair(conn net.Conn, p *pairing, self hello, key noise.DHKey) (string, error) {
	l, err := acceptPair(conn, p.code, p.session, key)
	if err != nil {
		return "", errPairRefused
	}
	var peer hello
	if err := l.read(&peer); err != nil {
		return "", err
	}
	if err := l.write(self); err != nil {
		return "", err
	}
	if err := v.learn(peer, l.peer, remoteIP(conn), true); err != nil {
		return "", err
	}
	v.mu.Lock()
	shared := slices.Clone(v.sharedKeyLocked())
	v.mu.Unlock()
	if shared == nil {
		return "", errLocked
	}
	if err := l.write(welcome{Key: shared}); err != nil {
		return "", err
	}
	if err := v.answerSync(l, peer.Node); err != nil {
		return "", err
	}
	go v.kick()
	return cleanDeviceName(peer.Name), nil
}

func parsePairing(text string) (string, []string, error) {
	text = strings.TrimSpace(text)
	if rest, ok := strings.CutPrefix(text, pairScheme+":pair?"); ok {
		q, err := url.ParseQuery(rest)
		if err != nil {
			return "", nil, errPairRefused
		}
		var addrs []string
		for a := range strings.SplitSeq(q.Get("a"), ",") {
			if a != "" {
				addrs = append(addrs, a)
			}
		}
		return q.Get("c"), addrs, nil
	}
	code := strings.Map(func(r rune) rune {
		if r >= '0' && r <= '9' {
			return r
		}
		return -1
	}, text)
	if len(code) != pairCodeDigits {
		return "", nil, errPairRefused
	}
	return code, nil, nil
}

func (v *Vault) JoinPairing(ctx context.Context, text string) (string, error) {
	code, addrs, err := parsePairing(text)
	if err != nil {
		return "", err
	}
	if len(addrs) == 0 {
		addrs = v.awaitSeen(ctx)
	}
	if len(addrs) == 0 {
		return "", errNoPairing
	}
	self, key, err := v.selfHello()
	if err != nil {
		return "", err
	}
	last := errNoPairing
	for _, addr := range addrs {
		if ctx.Err() != nil {
			return "", ctx.Err()
		}
		name, err := v.joinAt(ctx, addr, code, self, key)
		if err == nil {
			go v.kick()
			return name, nil
		}
		if !errors.Is(err, errDial) && !errors.Is(err, errNoPairing) {
			return "", err
		}
		last = err
	}
	if errors.Is(last, errDial) {
		return "", errNoPairing
	}
	return "", last
}

func (v *Vault) joinAt(ctx context.Context, addr, code string, self hello, key noise.DHKey) (string, error) {
	dialer := net.Dialer{Timeout: dialTimeout}
	conn, err := dialer.DialContext(ctx, "tcp", addr)
	if err != nil {
		return "", errDial
	}
	defer conn.Close()
	stop := context.AfterFunc(ctx, func() { _ = conn.Close() })
	defer stop()
	l, err := dialPair(conn, code, key)
	if err != nil {
		return "", err
	}
	if err := l.write(self); err != nil {
		return "", errPairRefused
	}
	var peer hello
	if err := l.read(&peer); err != nil {
		return "", errPairRefused
	}
	if err := v.learn(peer, l.peer, remoteIP(conn), true); err != nil {
		return "", err
	}
	var w welcome
	if err := l.read(&w); err != nil {
		return "", errPairRefused
	}
	if err := v.adoptKey(w.Key); err != nil {
		return "", err
	}
	if err := v.driveSync(l, peer.Node); err != nil {
		return "", err
	}
	return cleanDeviceName(peer.Name), nil
}

func (v *Vault) seenAddrs() []string {
	v.mu.Lock()
	defer v.mu.Unlock()
	var out []string
	for _, s := range v.seen {
		if time.Since(s.at) < seenTTL {
			out = append(out, s.addr)
		}
	}
	return out
}

func (v *Vault) awaitSeen(ctx context.Context) []string {
	deadline := time.Now().Add(seenWait)
	for {
		if addrs := v.seenAddrs(); len(addrs) > 0 || time.Now().After(deadline) {
			return addrs
		}
		select {
		case <-ctx.Done():
			return nil
		case <-time.After(seenPoll):
		}
	}
}

func (v *Vault) leave() {
	v.mu.Lock()
	if v.sh == nil {
		v.mu.Unlock()
		return
	}
	if i := v.sh.device(v.sh.Node); i >= 0 {
		d := v.sh.Devices[i]
		d.Removed = true
		_ = v.sh.putDevice(nil, d, "")
	}
	v.mu.Unlock()
	done := make(chan struct{})
	go func() {
		_ = v.syncPeers()
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(leaveTimeout):
	}
}

func (v *Vault) adoptKey(key []byte) error {
	if len(key) != chacha20poly1305.KeySize {
		return errPairRefused
	}
	v.mu.Lock()
	defer v.mu.Unlock()
	current := v.sharedKeyLocked()
	if current == nil {
		return errLocked
	}
	if slices.Equal(current, key) {
		return nil
	}
	v.sh.reseal(current, key)
	zero(current)
	v.p.Local.Key = slices.Clone(key)
	v.rebuildShareLocked()
	return v.saveLocked()
}
