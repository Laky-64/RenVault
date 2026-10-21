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
	"unsafe"
)

const (
	platformAdvertises = true
	platformReason     = "advertising is available"
)

func newAdvertiser() Advertiser { return &androidAdvertiser{} }

type androidAdvertiser struct{}

func (*androidAdvertiser) Start(_ context.Context, advert []byte) error {
	if len(advert) == 0 {
		return errors.New("agent: empty advert")
	}
	return androidError(C.renvault_advertise_start(unsafe.Pointer(&advert[0]), C.int(len(advert))))
}

func (*androidAdvertiser) Stop() error {
	return androidError(C.renvault_advertise_stop())
}

func androidError(msg *C.char) error {
	if msg == nil {
		return nil
	}
	defer C.free(unsafe.Pointer(msg))
	return errors.New("agent: " + C.GoString(msg))
}
