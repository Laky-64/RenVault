package vault

import (
	"net"
	"time"
)

const onlineWindow = 3 * time.Minute

type deviceAbout struct {
	Platform string    `cbor:"platform,omitempty"`
	Model    string    `cbor:"model,omitempty"`
	Version  string    `cbor:"version,omitempty"`
	Joined   time.Time `cbor:"joined,omitempty"`
}

func aboutAD(id string) []byte { return adOf("device-about", id) }

func sealAbout(key []byte, id string, about deviceAbout) ([]byte, error) {
	plain, err := cborEnc.Marshal(about)
	if err != nil {
		return nil, err
	}
	return sealWith(key, aboutAD(id), plain)
}

func openAbout(key []byte, d sealedDevice) deviceAbout {
	var about deviceAbout
	if key == nil || len(d.About) == 0 {
		return about
	}
	plain, err := openWith(key, aboutAD(d.ID), d.About)
	if err != nil {
		return about
	}
	_ = cborDec.Unmarshal(plain, &about)
	return about
}

func (v *Vault) SetAbout(platform, model, version string) error {
	v.mu.Lock()
	v.about = deviceAbout{Platform: platform, Model: model, Version: version}
	ready := v.sh != nil && v.p != nil
	v.mu.Unlock()
	if !ready {
		return nil
	}
	return v.mutate(func(*payload) error {
		if v.sh == nil {
			return nil
		}
		i := v.sh.device(v.sh.Node)
		key := v.sharedKeyLocked()
		if i < 0 || key == nil {
			return nil
		}
		d := v.sh.Devices[i]
		cur := openAbout(key, d)
		next := v.about
		next.Joined = cur.Joined
		if next == cur {
			return nil
		}
		box, err := sealAbout(key, d.ID, next)
		if err != nil {
			return err
		}
		d.About = box
		return v.sh.putDevice(key, d, v.sh.deviceName(key, d))
	})
}

func (v *Vault) addressOfLocked(id string) string {
	if v.sh != nil && id == v.sh.Node {
		ips := v.ips
		if len(ips) == 0 {
			ips = interfaceIPs()
		}
		if len(ips) > 0 {
			return ips[0]
		}
		return ""
	}
	addr := v.seen[id].addr
	if addr == "" && v.sh != nil && len(v.sh.Peers[id].Addrs) > 0 {
		addr = v.sh.Peers[id].Addrs[0]
	}
	if host, _, err := net.SplitHostPort(addr); err == nil {
		return host
	}
	return addr
}
