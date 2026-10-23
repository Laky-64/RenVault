package service

import (
	"context"

	"github.com/Laky-64/renvault-core/bridge"
)

func installDevices() {
	command(bridge.OpVaultLocalSetup, func(_ context.Context, request localSetupRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.FinishLocalSetup(request.Secret, request.Name, request.Device, request.Photo)
	})

	command(bridge.OpVaultSetProfile, func(_ context.Context, request profileRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.SetProfile(request.Name, request.Photo, request.SetPhoto)
	})

	query(bridge.OpDevices, func(context.Context) ([]deviceEntry, error) {
		active, err := unlocked()
		if err != nil {
			return nil, err
		}
		devices, err := active.Devices()
		if err != nil {
			return nil, err
		}
		out := make([]deviceEntry, 0, len(devices))
		for _, d := range devices {
			out = append(out, deviceEntry{ID: d.ID, Name: d.Name, Self: d.Self, SyncedAt: millis(d.SyncedAt)})
		}
		return out, nil
	})

	command(bridge.OpDeviceRemove, func(_ context.Context, request identifierRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.RemoveDevice(request.ID)
	})

	query(bridge.OpPairStart, func(context.Context) (pairResponse, error) {
		active, err := unlocked()
		if err != nil {
			return pairResponse{}, err
		}
		info, err := active.StartPairing()
		if err != nil {
			return pairResponse{}, err
		}
		return pairResponse{Code: info.Code, Payload: info.Payload, Size: info.Size, Modules: info.Modules}, nil
	})

	query(bridge.OpPairWait, func(ctx context.Context) (nameResponse, error) {
		active, err := unlocked()
		if err != nil {
			return nameResponse{}, err
		}
		name, err := active.WaitPairing(ctx)
		return nameResponse{Name: name}, err
	})

	handle(bridge.OpPairJoin, func(ctx context.Context, request joinRequest) (nameResponse, error) {
		active, err := unlocked()
		if err != nil {
			return nameResponse{}, err
		}
		name, err := active.JoinPairing(ctx, request.Text)
		return nameResponse{Name: name}, err
	})

	query(bridge.OpNetInfo, func(context.Context) (netInfoResponse, error) {
		active, err := opened()
		if err != nil {
			return netInfoResponse{}, err
		}
		info := active.NetInfo()
		return netInfoResponse{Node: info.Node, Port: info.Port}, nil
	})

	command(bridge.OpNetAddresses, func(_ context.Context, request addressesRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		active.SetAddresses(request.IPs)
		return nil
	})

	trigger(bridge.OpPeersSync, func(context.Context) error {
		active, err := opened()
		if err != nil {
			return err
		}
		return active.SyncPeers()
	})

	command(bridge.OpNetPeerSeen, func(_ context.Context, request peerSeenRequest) error {
		active, err := opened()
		if err != nil {
			return err
		}
		active.PeerSeen(request.Node, request.Host, request.Port)
		return nil
	})
}
