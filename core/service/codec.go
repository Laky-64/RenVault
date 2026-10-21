package service

import (
	"context"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/fxamacker/cbor/v2"
)

type none struct{}

var encoder = func() cbor.EncMode {
	mode, err := cbor.EncOptions{
		Sort:          cbor.SortCanonical,
		NilContainers: cbor.NilContainerAsEmpty,
	}.EncMode()
	if err != nil {
		panic(err)
	}
	return mode
}()

func decode[Q any](payload []byte) (Q, error) {
	var request Q
	if len(payload) == 0 {
		return request, nil
	}
	if err := cbor.Unmarshal(payload, &request); err != nil {
		return request, bridge.Fail(bridge.KindBadRequest, "service: "+err.Error())
	}
	return request, nil
}

func encode(response any) ([]byte, error) {
	if _, empty := response.(none); empty {
		return nil, nil
	}
	encoded, err := encoder.Marshal(response)
	if err != nil {
		return nil, bridge.Fail(bridge.KindUnknown, "service: "+err.Error())
	}
	return encoded, nil
}

func handle[Q any, R any](op uint32, action func(context.Context, Q) (R, error)) {
	bridge.Register(op, func(ctx context.Context, payload []byte) ([]byte, error) {
		request, err := decode[Q](payload)
		if err != nil {
			return nil, err
		}
		response, err := action(ctx, request)
		if err != nil {
			return nil, err
		}
		return encode(response)
	})
}

func query[R any](op uint32, action func(context.Context) (R, error)) {
	handle(op, func(ctx context.Context, _ none) (R, error) { return action(ctx) })
}

func command[Q any](op uint32, action func(context.Context, Q) error) {
	handle(op, func(ctx context.Context, request Q) (none, error) {
		return none{}, action(ctx, request)
	})
}

func trigger(op uint32, action func(context.Context) error) {
	handle(op, func(ctx context.Context, _ none) (none, error) {
		return none{}, action(ctx)
	})
}
