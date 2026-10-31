package bridge

import (
	"context"
	"sync"
)

type Handler func(ctx context.Context, request []byte) ([]byte, error)

var (
	handlersMu sync.RWMutex
	handlers   = make(map[uint32]Handler)
)

func Register(op uint32, handler Handler) {
	handlersMu.Lock()
	defer handlersMu.Unlock()
	handlers[op] = handler
}

func Call(op uint32, token int64, request []byte) ([]byte, []byte) {
	handlersMu.RLock()
	handler, known := handlers[op]
	handlersMu.RUnlock()
	if !known {
		return nil, encodeFailure(Fail(KindBadOp, "bridge: unknown operation"))
	}

	ctx, release := track(token)
	defer release()

	response, err := handler(ctx, request)
	if err != nil {
		if ctx.Err() != nil {
			return nil, encodeFailure(Fail(KindCancelled, "bridge: cancelled"))
		}
		return nil, encodeFailure(err)
	}
	return response, nil
}
