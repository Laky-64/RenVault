//go:build android || jni

package main

/*
#include "jni_bridge.h"
*/
import "C"

import (
	"time"
	"unsafe"

	"github.com/Laky-64/renvault-core/bridge"
)

const (
	statusOK      byte = 0
	statusFailure byte = 1
)

//export Java_io_lauren_renvault_bridge_Native_call
func Java_io_lauren_renvault_bridge_Native_call(env *C.JNIEnv, self C.jobject, op C.jint, token C.jlong, request C.jbyteArray) C.jbyteArray {
	response, failure := bridge.Call(uint32(op), int64(token), readArray(env, request))
	if failure != nil {
		return newArray(env, frame(statusFailure, failure))
	}
	return newArray(env, frame(statusOK, response))
}

//export Java_io_lauren_renvault_bridge_Native_nextEvent
func Java_io_lauren_renvault_bridge_Native_nextEvent(env *C.JNIEnv, self C.jobject, timeoutMs C.jint) C.jbyteArray {
	event, alive := bridge.NextEvent(time.Duration(timeoutMs) * time.Millisecond)
	if !alive || len(event) == 0 {
		var absent C.jbyteArray
		return absent
	}
	return newArray(env, event)
}

//export Java_io_lauren_renvault_bridge_Native_cancel
func Java_io_lauren_renvault_bridge_Native_cancel(env *C.JNIEnv, self C.jobject, token C.jlong) {
	bridge.Cancel(int64(token))
}

//export Java_io_lauren_renvault_bridge_Native_shutdown
func Java_io_lauren_renvault_bridge_Native_shutdown(env *C.JNIEnv, self C.jobject) {
	bridge.Shutdown()
}

func readArray(env *C.JNIEnv, array C.jbyteArray) []byte {
	size := int(C.rv_jni_len(env, array))
	if size <= 0 {
		return nil
	}
	buffer := make([]byte, size)
	C.rv_jni_read(env, array, (*C.uint8_t)(unsafe.Pointer(&buffer[0])), C.int32_t(size))
	return buffer
}

func newArray(env *C.JNIEnv, payload []byte) C.jbyteArray {
	if len(payload) == 0 {
		return C.rv_jni_new(env, nil, 0)
	}
	return C.rv_jni_new(env, (*C.uint8_t)(unsafe.Pointer(&payload[0])), C.int32_t(len(payload)))
}

func frame(status byte, payload []byte) []byte {
	framed := make([]byte, 1+len(payload))
	framed[0] = status
	copy(framed[1:], payload)
	return framed
}
