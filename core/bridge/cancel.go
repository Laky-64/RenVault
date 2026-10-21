package bridge

import (
	"context"
	"sync"
)

type running struct {
	cancel context.CancelFunc
}

var (
	livemu sync.Mutex
	live   = make(map[int64]*running)
)

func track(token int64) (context.Context, func()) {
	if token == 0 {
		return context.Background(), func() {}
	}

	ctx, cancel := context.WithCancel(context.Background())
	self := &running{cancel: cancel}

	livemu.Lock()
	if previous, busy := live[token]; busy {
		previous.cancel()
	}
	live[token] = self
	livemu.Unlock()

	return ctx, func() {
		livemu.Lock()
		if live[token] == self {
			delete(live, token)
		}
		livemu.Unlock()
		cancel()
	}
}

func Cancel(token int64) {
	livemu.Lock()
	current, busy := live[token]
	if busy {
		delete(live, token)
	}
	livemu.Unlock()

	if busy {
		current.cancel()
	}
}
