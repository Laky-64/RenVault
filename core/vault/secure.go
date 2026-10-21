package vault

func zero(b []byte) {
	for i := range b {
		b[i] = 0
	}
}
