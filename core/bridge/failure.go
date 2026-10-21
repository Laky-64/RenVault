package bridge

type Kind uint32

const (
	KindUnknown Kind = iota
	KindBadOp
	KindBadRequest
	KindNotInitialised
	KindLocked
	KindNoVault
	KindNoLogin
	KindTwoFactorRequired
	KindNoBottles
	KindBottleIndex
	KindNoPeer
	KindNoProfile
	KindCancelled
)

type Failure struct {
	Kind    Kind   `cbor:"kind"`
	Message string `cbor:"message"`
}

func (f Failure) Error() string { return f.Message }

func Fail(kind Kind, message string) error {
	return Failure{Kind: kind, Message: message}
}

func encodeFailure(err error) []byte {
	failure, ok := err.(Failure)
	if !ok {
		failure = Failure{Kind: KindUnknown, Message: err.Error()}
	}
	encoded, marshalErr := encoder.Marshal(failure)
	if marshalErr != nil {
		return nil
	}
	return encoded
}
