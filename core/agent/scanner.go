package agent

import "context"

type Scanner interface {
	Scan(ctx context.Context) (string, error)
}
