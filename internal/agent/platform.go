package agent

import "errors"

var ErrUnsupported = errors.New("agent: " + platformReason)

func Supported() bool {
	return platformAdvertises && defaultScanner() != nil
}

func Reason() string {
	if Supported() {
		return ""
	}
	if !platformAdvertises {
		return platformReason
	}
	return "no way to read the sign-in QR code on this device"
}
