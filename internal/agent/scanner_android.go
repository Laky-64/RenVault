//go:build android

package agent

/*
#include "jni_android.h"
#include <stdlib.h>
*/
import "C"

import (
	"context"
	"errors"
	"sync"
	"unsafe"
)

type scanOutcome struct {
	url string
	err error
}

var (
	scanMu      sync.Mutex
	scanPending chan scanOutcome
)

func defaultScanner() Scanner { return &androidScanner{} }

type androidScanner struct{}

func (*androidScanner) Scan(ctx context.Context) (string, error) {
	replies := make(chan scanOutcome, 1)

	scanMu.Lock()
	if scanPending != nil {
		scanMu.Unlock()
		return "", errors.New("agent: a scan is already running")
	}
	scanPending = replies
	scanMu.Unlock()

	defer func() {
		scanMu.Lock()
		scanPending = nil
		scanMu.Unlock()
	}()

	if err := androidError(C.renvault_scan_start()); err != nil {
		return "", err
	}

	select {
	case got := <-replies:
		return got.url, got.err
	case <-ctx.Done():
		return "", ctx.Err()
	}
}

func deliverScan(url string, failure string) {
	scanMu.Lock()
	replies := scanPending
	scanPending = nil
	scanMu.Unlock()
	if replies == nil {
		return
	}
	switch {
	case failure != "":
		replies <- scanOutcome{err: errors.New("agent: " + failure)}
	case url == "":
		replies <- scanOutcome{err: errors.New("agent: the scanner returned nothing")}
	default:
		replies <- scanOutcome{url: url}
	}
}

//export Java_com_wails_app_RenVaultScanner_nativeDeliver
func Java_com_wails_app_RenVaultScanner_nativeDeliver(env *C.JNIEnv, class C.jclass, url C.jstring, failure C.jstring) {
	deliverScan(readJString(env, url), readJString(env, failure))
}

func readJString(env *C.JNIEnv, value C.jstring) string {
	raw := C.renvault_copy_jstring(env, value)
	if raw == nil {
		return ""
	}
	defer C.free(unsafe.Pointer(raw))
	return C.GoString(raw)
}
