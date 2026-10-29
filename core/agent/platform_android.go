//go:build android

package agent

const (
	platformAdvertises = false
	platformReason     = "the app advertises over bluetooth"
)

func newAdvertiser() Advertiser { return nil }
