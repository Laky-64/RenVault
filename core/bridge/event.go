package bridge

import (
	"sync"
	"time"

	"github.com/fxamacker/cbor/v2"
)

const (
	EventSyncChanged   uint32 = 0x0001
	EventLocked        uint32 = 0x0002
	EventSignRequested uint32 = 0x0003
	EventAgentProgress uint32 = 0x0004
)

const queueDepth = 64

type Event struct {
	Kind    uint32 `cbor:"kind"`
	Payload []byte `cbor:"payload"`
}

var encoder = func() cbor.EncMode {
	mode, err := cbor.EncOptions{NilContainers: cbor.NilContainerAsEmpty}.EncMode()
	if err != nil {
		panic(err)
	}
	return mode
}()

var (
	events   = make(chan []byte, queueDepth)
	stop     = make(chan struct{})
	stopOnce sync.Once
)

func Emit(kind uint32, payload any) {
	body, err := encoder.Marshal(payload)
	if err != nil {
		return
	}
	encoded, err := encoder.Marshal(Event{Kind: kind, Payload: body})
	if err != nil {
		return
	}

	select {
	case events <- encoded:
	case <-stop:
	default:
	}
}

func NextEvent(timeout time.Duration) ([]byte, bool) {
	if timeout <= 0 {
		select {
		case event := <-events:
			return event, true
		case <-stop:
			return nil, false
		}
	}

	timer := time.NewTimer(timeout)
	defer timer.Stop()

	select {
	case event := <-events:
		return event, true
	case <-timer.C:
		return nil, true
	case <-stop:
		return nil, false
	}
}

func Shutdown() {
	stopOnce.Do(func() { close(stop) })
}
