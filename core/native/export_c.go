//go:build !android && !jni

package main

/*
#include <stdlib.h>
#include <stdint.h>
*/
import "C"

import (
	"time"
	"unsafe"

	"github.com/Laky-64/renvault-core/bridge"
)

const (
	resultOK       C.int32_t = 0
	resultFailure  C.int32_t = 1
	resultStopped  C.int32_t = 2
	resultInternal C.int32_t = -1
)

//export rv_call
func rv_call(op C.int32_t, token C.int64_t, request *C.uint8_t, requestLen C.int32_t, out **C.uint8_t, outLen *C.int32_t) C.int32_t {
	if out == nil || outLen == nil {
		return resultInternal
	}

	response, failure := bridge.Call(uint32(op), int64(token), readBytes(request, requestLen))
	if failure != nil {
		writeBytes(failure, out, outLen)
		return resultFailure
	}

	writeBytes(response, out, outLen)
	return resultOK
}

//export rv_next_event
func rv_next_event(timeoutMs C.int32_t, out **C.uint8_t, outLen *C.int32_t) C.int32_t {
	if out == nil || outLen == nil {
		return resultInternal
	}

	event, alive := bridge.NextEvent(time.Duration(timeoutMs) * time.Millisecond)
	if !alive {
		return resultStopped
	}

	writeBytes(event, out, outLen)
	return resultOK
}

//export rv_cancel
func rv_cancel(token C.int64_t) {
	bridge.Cancel(int64(token))
}

//export rv_shutdown
func rv_shutdown() {
	bridge.Shutdown()
}

//export rv_free
func rv_free(ptr *C.uint8_t) {
	if ptr != nil {
		C.free(unsafe.Pointer(ptr))
	}
}

func readBytes(ptr *C.uint8_t, size C.int32_t) []byte {
	if ptr == nil || size <= 0 {
		return nil
	}
	return C.GoBytes(unsafe.Pointer(ptr), C.int(size))
}

func writeBytes(payload []byte, out **C.uint8_t, outLen *C.int32_t) {
	if len(payload) == 0 {
		*out = nil
		*outLen = 0
		return
	}
	*out = (*C.uint8_t)(C.CBytes(payload))
	*outLen = C.int32_t(len(payload))
}
