package service

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"sync"
	"time"

	"github.com/Laky-64/renvault-core/agent"
	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/ctap"
)

const hybridTimeout = 3 * time.Minute

var errHybridDeclined = errors.New("service: the request was declined")

type hybridStartRequest struct {
	QR string `cbor:"qr"`
}

type hybridStartResponse struct {
	Session string `cbor:"session"`
	Advert  []byte `cbor:"advert"`
}

type hybridDecideRequest struct {
	ID      string `cbor:"id"`
	Approve bool   `cbor:"approve"`
	Account int    `cbor:"account"`
}

type hybridSessionRequest struct {
	Session string `cbor:"session"`
}

type hybridProgress struct {
	Session string `cbor:"session"`
	Stage   string `cbor:"stage"`
	Error   string `cbor:"error"`
	Command int    `cbor:"command"`
	Status  int    `cbor:"status"`
}

type hybridAccount struct {
	Title    string `cbor:"title"`
	Username string `cbor:"username"`
}

type hybridPrompt struct {
	ID           string          `cbor:"id"`
	Session      string          `cbor:"session"`
	RelyingParty string          `cbor:"relying_party"`
	Create       bool            `cbor:"create"`
	Username     string          `cbor:"username"`
	Accounts     []hybridAccount `cbor:"accounts"`
}

type hybridDecision struct {
	approve bool
	account int
}

var hybridState = struct {
	sync.Mutex
	sessions map[string]context.CancelFunc
	prompts  map[string]chan hybridDecision
}{
	sessions: map[string]context.CancelFunc{},
	prompts:  map[string]chan hybridDecision{},
}

func hybridID() string {
	var b [12]byte
	_, _ = rand.Read(b[:])
	return hex.EncodeToString(b[:])
}

func installHybrid() {
	handle(bridge.OpHybridStart, func(_ context.Context, request hybridStartRequest) (hybridStartResponse, error) {
		active, err := unlocked()
		if err != nil {
			return hybridStartResponse{}, err
		}
		session := hybridID()
		ctx, cancel := context.WithTimeout(context.Background(), hybridTimeout)
		choose := func(ctx context.Context, rp string, options []ctap.Credential) (ctap.Credential, error) {
			accounts := make([]hybridAccount, len(options))
			for i, o := range options {
				accounts[i] = hybridAccount{Title: o.DisplayName, Username: o.UserName}
			}
			d, err := askHybrid(ctx, hybridPrompt{Session: session, RelyingParty: rp, Accounts: accounts})
			if err != nil {
				return ctap.Credential{}, err
			}
			if d.account < 0 || d.account >= len(options) {
				return ctap.Credential{}, errHybridDeclined
			}
			return options[d.account], nil
		}
		approve := func(ctx context.Context, c ctap.NewCredential) error {
			_, err := askHybrid(ctx, hybridPrompt{Session: session, RelyingParty: c.RelyingParty, Create: true, Username: c.UserName})
			return err
		}
		connected := func() { bridge.Emit(bridge.EventAgentProgress, hybridProgress{Session: session, Stage: "connected"}) }
		observe := func(command, status byte) {
			bridge.Emit(bridge.EventAgentProgress, hybridProgress{Session: session, Stage: "ctap", Command: int(command), Status: int(status)})
		}
		advert, done, err := agent.Start(ctx, active, request.QR, choose, approve, connected, observe)
		if err != nil {
			cancel()
			return hybridStartResponse{}, err
		}
		hybridState.Lock()
		hybridState.sessions[session] = cancel
		hybridState.Unlock()
		go func() {
			err := <-done
			hybridState.Lock()
			delete(hybridState.sessions, session)
			hybridState.Unlock()
			cancel()
			progress := hybridProgress{Session: session, Stage: "done"}
			if err != nil {
				progress = hybridProgress{Session: session, Stage: "failed", Error: err.Error()}
			}
			bridge.Emit(bridge.EventAgentProgress, progress)
		}()
		return hybridStartResponse{Session: session, Advert: advert}, nil
	})

	command(bridge.OpHybridDecide, func(_ context.Context, request hybridDecideRequest) error {
		hybridState.Lock()
		replies, ok := hybridState.prompts[request.ID]
		delete(hybridState.prompts, request.ID)
		hybridState.Unlock()
		if !ok {
			return errors.New("service: no such request")
		}
		replies <- hybridDecision{approve: request.Approve, account: request.Account}
		return nil
	})

	command(bridge.OpHybridCancel, func(_ context.Context, request hybridSessionRequest) error {
		hybridState.Lock()
		cancel, ok := hybridState.sessions[request.Session]
		hybridState.Unlock()
		if ok {
			cancel()
		}
		return nil
	})
}

func askHybrid(ctx context.Context, prompt hybridPrompt) (hybridDecision, error) {
	prompt.ID = hybridID()
	replies := make(chan hybridDecision, 1)
	hybridState.Lock()
	hybridState.prompts[prompt.ID] = replies
	hybridState.Unlock()
	defer func() {
		hybridState.Lock()
		delete(hybridState.prompts, prompt.ID)
		hybridState.Unlock()
	}()
	bridge.Emit(bridge.EventSignRequested, prompt)
	select {
	case d := <-replies:
		if !d.approve {
			return d, errHybridDeclined
		}
		return d, nil
	case <-ctx.Done():
		return hybridDecision{}, ctx.Err()
	}
}
