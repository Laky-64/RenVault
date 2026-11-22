package service

import (
	"context"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/updates"
)

type updateCheckRequest struct {
	Current    string `cbor:"current"`
	Platform   string `cbor:"platform"`
	Prerelease bool   `cbor:"prerelease"`
	Dir        string `cbor:"dir"`
}

type updateNotes struct {
	Version string `cbor:"version"`
	Body    string `cbor:"body"`
}

type updateRelease struct {
	Available bool          `cbor:"available"`
	Version   string        `cbor:"version"`
	Tag       string        `cbor:"tag"`
	Page      string        `cbor:"page"`
	Published int64         `cbor:"published"`
	Asset     string        `cbor:"asset"`
	URL       string        `cbor:"url"`
	Size      int64         `cbor:"size"`
	Sums      string        `cbor:"sums"`
	Signature string        `cbor:"signature"`
	Notes     []updateNotes `cbor:"notes"`
}

type updateDownloadRequest struct {
	Release updateRelease `cbor:"release"`
	Dir     string        `cbor:"dir"`
}

type updateDirRequest struct {
	Dir string `cbor:"dir"`
}

type updatePath struct {
	Path string `cbor:"path"`
}

type updateProgress struct {
	Asset string `cbor:"asset"`
	Done  int64  `cbor:"done"`
	Total int64  `cbor:"total"`
}

func installUpdates() {
	handle(bridge.OpUpdateCheck, func(ctx context.Context, request updateCheckRequest) (updateRelease, error) {
		r, err := updates.Check(ctx, request.Current, request.Platform, request.Prerelease, request.Dir)
		if err != nil || r == nil {
			return updateRelease{}, err
		}
		out := updateRelease{
			Available: true,
			Version:   r.Version,
			Tag:       r.Tag,
			Page:      r.Page,
			Published: millis(r.Published),
			Asset:     r.Asset,
			URL:       r.URL,
			Size:      r.Size,
			Sums:      r.Sums,
			Signature: r.Signature,
		}
		for _, n := range r.Notes {
			out.Notes = append(out.Notes, updateNotes{Version: n.Version, Body: n.Body})
		}
		return out, nil
	})

	handle(bridge.OpUpdateDownload, func(ctx context.Context, request updateDownloadRequest) (updatePath, error) {
		r := request.Release
		path, err := updates.Download(ctx, updates.Release{
			Version:   r.Version,
			Asset:     r.Asset,
			URL:       r.URL,
			Size:      r.Size,
			Sums:      r.Sums,
			Signature: r.Signature,
		}, request.Dir, func(done, total int64) {
			bridge.Emit(bridge.EventUpdateProgress, updateProgress{Asset: r.Asset, Done: done, Total: total})
		})
		return updatePath{Path: path}, err
	})

	command(bridge.OpUpdateClean, func(_ context.Context, request updateDirRequest) error {
		updates.Clean(request.Dir)
		return nil
	})
}
