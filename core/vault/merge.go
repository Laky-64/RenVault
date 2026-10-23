package vault

import "slices"

type digest struct {
	Records map[string]hlc `cbor:"records"`
	Profile hlc            `cbor:"profile"`
	Names   hlc            `cbor:"names"`
}

type wants struct {
	Records []string `cbor:"records,omitempty"`
	Profile bool     `cbor:"profile,omitempty"`
	Names   bool     `cbor:"names,omitempty"`
}

type delta struct {
	Records []sealedRecord `cbor:"records,omitempty"`
	Devices []sealedDevice `cbor:"devices,omitempty"`
	Profile *sealedBlob    `cbor:"profile,omitempty"`
	Names   *sealedBlob    `cbor:"names,omitempty"`
	Want    wants          `cbor:"want"`
}

type mergeResult struct {
	changed bool
	profile bool
}

func (sh *share) digest() digest {
	d := digest{Records: make(map[string]hlc, len(sh.Records)), Profile: sh.Profile.Clock, Names: sh.Names.Clock}
	for _, r := range sh.Records {
		d.Records[r.ID] = r.Clock
	}
	return d
}

func (sh *share) answer(remote digest) delta {
	out := delta{Devices: slices.Clone(sh.Devices)}
	mine := make(map[string]struct{}, len(sh.Records))
	for _, r := range sh.Records {
		mine[r.ID] = struct{}{}
		theirs, ok := remote.Records[r.ID]
		switch {
		case !ok || r.Clock.compare(theirs) > 0:
			out.Records = append(out.Records, r)
		case r.Clock.compare(theirs) < 0:
			out.Want.Records = append(out.Want.Records, r.ID)
		}
	}
	for id := range remote.Records {
		if _, ok := mine[id]; !ok {
			out.Want.Records = append(out.Want.Records, id)
		}
	}
	switch c := sh.Profile.Clock.compare(remote.Profile); {
	case c > 0:
		out.Profile = new(sh.Profile)
	case c < 0:
		out.Want.Profile = true
	}
	switch c := sh.Names.Clock.compare(remote.Names); {
	case c > 0:
		out.Names = new(sh.Names)
	case c < 0:
		out.Want.Names = true
	}
	return out
}

func (sh *share) fulfil(w wants) delta {
	out := delta{Devices: slices.Clone(sh.Devices)}
	for _, r := range sh.Records {
		if slices.Contains(w.Records, r.ID) {
			out.Records = append(out.Records, r)
		}
	}
	if w.Profile {
		out.Profile = new(sh.Profile)
	}
	if w.Names {
		out.Names = new(sh.Names)
	}
	return out
}

func (sh *share) merge(in delta) mergeResult {
	var res mergeResult
	for _, r := range in.Records {
		if r.ID == "" || len(r.Box) == 0 {
			continue
		}
		sh.observe(r.Clock)
		i := sh.record(r.ID)
		switch {
		case i < 0:
			sh.Records = append(sh.Records, r)
		case r.Clock.compare(sh.Records[i].Clock) > 0:
			sh.Records[i] = r
		default:
			continue
		}
		res.changed = true
	}
	for _, d := range in.Devices {
		if d.ID == "" || len(d.Key) == 0 {
			continue
		}
		sh.observe(d.Clock)
		i := sh.device(d.ID)
		switch {
		case i < 0:
			sh.Devices = append(sh.Devices, d)
		case d.ID == sh.Node && !d.Removed:
			continue
		case d.Clock.compare(sh.Devices[i].Clock) > 0:
			sh.Devices[i] = d
		default:
			continue
		}
		res.changed = true
	}
	if b := in.Profile; b != nil && len(b.Box) > 0 && b.Clock.compare(sh.Profile.Clock) > 0 {
		sh.observe(b.Clock)
		sh.Profile = *b
		res.changed = true
		res.profile = true
	}
	if b := in.Names; b != nil && len(b.Box) > 0 && b.Clock.compare(sh.Names.Clock) > 0 {
		sh.observe(b.Clock)
		sh.Names = *b
		res.changed = true
		res.profile = true
	}
	return res
}
