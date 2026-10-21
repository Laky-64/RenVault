package vault

import (
	"time"
)

const (
	syncInterval = 30 * time.Second
	retryMin     = 15 * time.Second
	retryMax     = 5 * time.Minute
)

type syncer struct {
	v        *Vault
	wake     chan struct{}
	stop     chan struct{}
	lastFull time.Time
}

func (v *Vault) startSyncLocked(fresh bool) {
	v.stopSyncLocked()
	s := &syncer{v: v, wake: make(chan struct{}, 1), stop: make(chan struct{})}
	if fresh {
		s.lastFull = time.Now()
	}
	v.syncer = s
	go s.run()
}

func (v *Vault) stopSyncLocked() {
	if v.syncer == nil {
		return
	}
	close(v.syncer.stop)
	v.syncer = nil
}

func (v *Vault) kick() {
	v.mu.Lock()
	s := v.syncer
	v.mu.Unlock()
	if s == nil {
		return
	}
	select {
	case s.wake <- struct{}{}:
	default:
	}
}

func (s *syncer) run() {
	backoff := time.Duration(0)
	for {
		if s.done() {
			return
		}
		delay := syncInterval
		if err := s.cycle(); err != nil {
			backoff = nextBackoff(backoff)
			delay = backoff
		} else {
			backoff = 0
		}
		select {
		case <-s.stop:
			return
		case <-s.wake:
		case <-time.After(delay):
		}
	}
}

func (s *syncer) done() bool {
	select {
	case <-s.stop:
		return true
	default:
		return false
	}
}

func nextBackoff(cur time.Duration) time.Duration {
	if cur <= 0 {
		return retryMin
	}
	next := cur * 2
	if next > retryMax {
		return retryMax
	}
	return next
}

func (s *syncer) cycle() error {
	v := s.v
	src, err := v.keychainSession()
	if err != nil {
		return err
	}
	changed, dropped, ferr := v.flushOutbox(src)
	if ferr != nil {
		if changed {
			v.notify()
		}
		return ferr
	}
	switch {
	case time.Since(s.lastFull) >= syncInterval:
		if err := v.syncAll(src); err != nil {
			return err
		}
		s.lastFull = time.Now()
	case dropped:
		if err := v.refreshWeb(src); err != nil {
			return err
		}
	case changed:
	default:
		return nil
	}
	v.notify()
	return nil
}
