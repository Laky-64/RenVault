package icons

import (
	"bytes"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"math"
)

const (
	maxSide    = 256
	alphaFloor = 16
	crispScale = 2
	plateSlack = 48
	plateAlpha = 230
	plateShare = 0.3
	inkFloor   = 40
	inkReach   = 8
	edgeGain   = 2.2
)

func process(src image.Image) ([]byte, error) {
	img := toNRGBA(src)
	box, ok := contentBounds(img)
	if !ok {
		return nil, fmt.Errorf("%w: no opaque pixels", ErrUnsupportedFormat)
	}
	logo := crop(img, box)
	flattenPlate(logo)

	w, h := logo.Bounds().Dx(), logo.Bounds().Dy()
	side := max(w, h)
	if side > maxSide {
		scale := float64(maxSide) / float64(side)
		w = max(1, int(math.Round(float64(w)*scale)))
		h = max(1, int(math.Round(float64(h)*scale)))
		logo = resample(logo, w, h)
		side = maxSide
	} else if side < maxSide {
		scale := float64(maxSide) / float64(side)
		w = max(1, int(math.Round(float64(w)*scale)))
		h = max(1, int(math.Round(float64(h)*scale)))
		logo = upscale(logo, w, h)
		if scale >= crispScale {
			logo = crisp(logo)
			if ground, ok := dominant(logo); ok {
				sharpenInk(logo, ground, int(math.Ceil(scale)))
			}
		}
		side = maxSide
	}

	tile := image.NewNRGBA(image.Rect(0, 0, side, side))
	offX, offY := (side-w)/2, (side-h)/2
	for y := range h {
		for x := range w {
			tile.SetNRGBA(offX+x, offY+y, logo.NRGBAAt(x, y))
		}
	}

	var buf bytes.Buffer
	if err := png.Encode(&buf, tile); err != nil {
		return nil, fmt.Errorf("png encoding: %w", err)
	}
	return buf.Bytes(), nil
}

func toNRGBA(src image.Image) *image.NRGBA {
	if out, ok := src.(*image.NRGBA); ok {
		return out
	}
	bounds := src.Bounds()
	out := image.NewNRGBA(image.Rect(0, 0, bounds.Dx(), bounds.Dy()))
	for y := range bounds.Dy() {
		for x := range bounds.Dx() {
			r, g, b, a := src.At(bounds.Min.X+x, bounds.Min.Y+y).RGBA()
			if a == 0 {
				continue
			}
			out.SetNRGBA(x, y, color.NRGBA{
				R: uint8(r * 0xFF / a),
				G: uint8(g * 0xFF / a),
				B: uint8(b * 0xFF / a),
				A: uint8(a >> 8),
			})
		}
	}
	return out
}

func contentBounds(img *image.NRGBA) (image.Rectangle, bool) {
	bounds := img.Bounds()
	minX, minY := bounds.Max.X, bounds.Max.Y
	maxX, maxY := bounds.Min.X, bounds.Min.Y
	found := false

	for y := bounds.Min.Y; y < bounds.Max.Y; y++ {
		for x := bounds.Min.X; x < bounds.Max.X; x++ {
			if img.NRGBAAt(x, y).A < alphaFloor {
				continue
			}
			found = true
			minX, minY = min(minX, x), min(minY, y)
			maxX, maxY = max(maxX, x), max(maxY, y)
		}
	}
	if !found {
		return image.Rectangle{}, false
	}
	return image.Rect(minX, minY, maxX+1, maxY+1), true
}

func crop(img *image.NRGBA, box image.Rectangle) *image.NRGBA {
	out := image.NewNRGBA(image.Rect(0, 0, box.Dx(), box.Dy()))
	for y := range box.Dy() {
		for x := range box.Dx() {
			out.SetNRGBA(x, y, img.NRGBAAt(box.Min.X+x, box.Min.Y+y))
		}
	}
	return out
}

func resample(src *image.NRGBA, dstW, dstH int) *image.NRGBA {
	srcW, srcH := src.Bounds().Dx(), src.Bounds().Dy()
	dst := image.NewNRGBA(image.Rect(0, 0, dstW, dstH))

	xRatio := float64(srcW) / float64(dstW)
	yRatio := float64(srcH) / float64(dstH)

	for y := range dstH {
		y0 := int(float64(y) * yRatio)
		y1 := max(y0+1, int(math.Ceil(float64(y+1)*yRatio)))
		y1 = min(y1, srcH)

		for x := range dstW {
			x0 := int(float64(x) * xRatio)
			x1 := max(x0+1, int(math.Ceil(float64(x+1)*xRatio)))
			x1 = min(x1, srcW)

			var sr, sg, sb, sa, n float64
			for sy := y0; sy < y1; sy++ {
				for sx := x0; sx < x1; sx++ {
					c := src.NRGBAAt(sx, sy)
					a := float64(c.A) / 0xFF
					sr += float64(c.R) * a
					sg += float64(c.G) * a
					sb += float64(c.B) * a
					sa += float64(c.A)
					n++
				}
			}
			if n == 0 {
				continue
			}

			alpha := sa / n
			if alpha < 0.5 {
				continue
			}
			k := n / (sa / 0xFF)
			dst.SetNRGBA(x, y, color.NRGBA{
				R: clamp8(sr * k / n),
				G: clamp8(sg * k / n),
				B: clamp8(sb * k / n),
				A: clamp8(alpha),
			})
		}
	}
	return dst
}

func clamp8(v float64) uint8 {
	return uint8(math.Max(0, math.Min(255, math.Round(v))))
}

const kernelReach = 2

func mitchell(x float64) float64 {
	const b, c = 1.0 / 3, 1.0 / 3
	x = math.Abs(x)
	switch {
	case x < 1:
		return ((12-9*b-6*c)*x*x*x + (-18+12*b+6*c)*x*x + (6 - 2*b)) / 6
	case x < 2:
		return ((-b-6*c)*x*x*x + (6*b+30*c)*x*x + (-12*b-48*c)*x + (8*b + 24*c)) / 6
	}
	return 0
}

type tap struct {
	index  int
	weight float64
}

func taps(src, dst int) [][]tap {
	out := make([][]tap, dst)
	ratio := float64(src) / float64(dst)
	for i := range dst {
		center := (float64(i)+0.5)*ratio - 0.5
		first := int(math.Floor(center)) - kernelReach + 1
		var row []tap
		var total float64
		for k := first; k < first+2*kernelReach; k++ {
			weight := mitchell(center - float64(k))
			if weight == 0 {
				continue
			}
			row = append(row, tap{index: min(max(k, 0), src-1), weight: weight})
			total += weight
		}
		for j := range row {
			row[j].weight /= total
		}
		out[i] = row
	}
	return out
}

func upscale(src *image.NRGBA, dstW, dstH int) *image.NRGBA {
	srcW, srcH := src.Bounds().Dx(), src.Bounds().Dy()
	columns := taps(srcW, dstW)
	rows := taps(srcH, dstH)

	mid := make([][4]float64, dstW*srcH)
	for y := range srcH {
		for x, row := range columns {
			var acc [4]float64
			for _, t := range row {
				c := src.NRGBAAt(t.index, y)
				a := float64(c.A) / 0xFF
				acc[0] += float64(c.R) * a * t.weight
				acc[1] += float64(c.G) * a * t.weight
				acc[2] += float64(c.B) * a * t.weight
				acc[3] += float64(c.A) * t.weight
			}
			mid[y*dstW+x] = acc
		}
	}

	dst := image.NewNRGBA(image.Rect(0, 0, dstW, dstH))
	for y, row := range rows {
		for x := range dstW {
			var acc [4]float64
			for _, t := range row {
				m := mid[t.index*dstW+x]
				for c := range acc {
					acc[c] += m[c] * t.weight
				}
			}
			alpha := math.Max(0, math.Min(255, acc[3]))
			if alpha < 1 {
				continue
			}
			k := 255 / alpha
			dst.SetNRGBA(x, y, color.NRGBA{R: clamp8(acc[0] * k), G: clamp8(acc[1] * k), B: clamp8(acc[2] * k), A: clamp8(alpha)})
		}
	}
	return dst
}

func crisp(src *image.NRGBA) *image.NRGBA {
	w, h := src.Bounds().Dx(), src.Bounds().Dy()
	dst := image.NewNRGBA(image.Rect(0, 0, w, h))
	for y := range h {
		for x := range w {
			c := src.NRGBAAt(x, y)
			if c.A == 0 {
				continue
			}
			if c.A < 255 {
				c.A = clamp8((float64(c.A)-128)*edgeGain + 128)
			}
			dst.SetNRGBA(x, y, c)
		}
	}
	return dst
}

func plateOf(img *image.NRGBA, slack int) (color.NRGBA, bool) {
	w, h := img.Bounds().Dx(), img.Bounds().Dy()
	if w < 8 || h < 8 {
		return color.NRGBA{}, false
	}
	inset := max(1, min(w, h)/24)
	sides := []color.NRGBA{
		img.NRGBAAt(w/2, inset),
		img.NRGBAAt(w/2, h-1-inset),
		img.NRGBAAt(inset, h/2),
		img.NRGBAAt(w-1-inset, h/2),
	}
	var sum [3]int
	for _, c := range sides {
		if c.A <= plateAlpha {
			return color.NRGBA{}, false
		}
		sum[0] += int(c.R)
		sum[1] += int(c.G)
		sum[2] += int(c.B)
	}
	n := len(sides)
	plate := color.NRGBA{R: uint8(sum[0] / n), G: uint8(sum[1] / n), B: uint8(sum[2] / n), A: 0xFF}
	for _, c := range sides {
		if distance(c, plate) > slack {
			return color.NRGBA{}, false
		}
	}
	return plate, true
}

func distance(a, b color.NRGBA) int {
	d := func(x, y uint8) int {
		if x > y {
			return int(x - y)
		}
		return int(y - x)
	}
	return d(a.R, b.R) + d(a.G, b.G) + d(a.B, b.B)
}

func flattenPlate(img *image.NRGBA) {
	plate, ok := plateOf(img, plateSlack/2)
	if !ok {
		return
	}
	w, h := img.Bounds().Dx(), img.Bounds().Dy()
	for y := range h {
		for x := range w {
			c := img.NRGBAAt(x, y)
			if c.A > plateAlpha && distance(c, plate) <= plateSlack/2 {
				img.SetNRGBA(x, y, plate)
			}
		}
	}
}

func dominant(img *image.NRGBA) (color.NRGBA, bool) {
	type bucket struct {
		count   int
		r, g, b int
	}
	buckets := map[uint32]*bucket{}
	opaque := 0
	var best *bucket
	w, h := img.Bounds().Dx(), img.Bounds().Dy()
	for y := range h {
		for x := range w {
			c := img.NRGBAAt(x, y)
			if c.A <= plateAlpha {
				continue
			}
			opaque++
			key := uint32(c.R>>3)<<10 | uint32(c.G>>3)<<5 | uint32(c.B>>3)
			b := buckets[key]
			if b == nil {
				b = &bucket{}
				buckets[key] = b
			}
			b.count++
			b.r += int(c.R)
			b.g += int(c.G)
			b.b += int(c.B)
			if best == nil || b.count > best.count {
				best = b
			}
		}
	}
	if best == nil || float64(best.count) < float64(opaque)*plateShare {
		return color.NRGBA{}, false
	}
	ground := color.NRGBA{R: uint8(best.r / best.count), G: uint8(best.g / best.count), B: uint8(best.b / best.count), A: 0xFF}
	inset := max(1, min(w, h)/24)
	touching := 0
	for _, c := range []color.NRGBA{
		img.NRGBAAt(w/2, inset),
		img.NRGBAAt(w/2, h-1-inset),
		img.NRGBAAt(inset, h/2),
		img.NRGBAAt(w-1-inset, h/2),
	} {
		if c.A > 0x80 && distance(c, ground) <= plateSlack {
			touching++
		}
	}
	return ground, touching >= 2
}

func sharpenInk(img *image.NRGBA, plate color.NRGBA, reach int) {
	reach = min(max(reach, 1), inkReach)
	w, h := img.Bounds().Dx(), img.Bounds().Dy()
	size := make([]float64, w*h)
	for y := range h {
		for x := range w {
			c := img.NRGBAAt(x, y)
			if c.A == 0 {
				continue
			}
			dr, dg, db := float64(c.R)-float64(plate.R), float64(c.G)-float64(plate.G), float64(c.B)-float64(plate.B)
			size[y*w+x] = math.Sqrt(dr*dr + dg*dg + db*db)
		}
	}
	rows := make([]float64, w*h)
	for y := range h {
		for x := range w {
			top := 0.0
			for dx := -reach; dx <= reach; dx++ {
				if nx := x + dx; nx >= 0 && nx < w {
					top = math.Max(top, size[y*w+nx])
				}
			}
			rows[y*w+x] = top
		}
	}
	for y := range h {
		for x := range w {
			c := img.NRGBAAt(x, y)
			own := size[y*w+x]
			if c.A == 0 || own == 0 {
				continue
			}
			top := 0.0
			for dy := -reach; dy <= reach; dy++ {
				if ny := y + dy; ny >= 0 && ny < h {
					top = math.Max(top, rows[ny*w+x])
				}
			}
			if top < inkFloor {
				continue
			}
			t := own / top
			k := math.Max(0, math.Min(1, (t-0.5)*edgeGain+0.5)) / t
			img.SetNRGBA(x, y, color.NRGBA{
				R: clamp8(float64(plate.R) + (float64(c.R)-float64(plate.R))*k),
				G: clamp8(float64(plate.G) + (float64(c.G)-float64(plate.G))*k),
				B: clamp8(float64(plate.B) + (float64(c.B)-float64(plate.B))*k),
				A: c.A,
			})
		}
	}
}
