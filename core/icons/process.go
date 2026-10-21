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
)

func process(src image.Image) ([]byte, error) {
	img := toNRGBA(src)
	box, ok := contentBounds(img)
	if !ok {
		return nil, fmt.Errorf("%w: no opaque pixels", ErrUnsupportedFormat)
	}
	logo := crop(img, box)

	w, h := logo.Bounds().Dx(), logo.Bounds().Dy()
	side := max(w, h)
	if side > maxSide {
		scale := float64(maxSide) / float64(side)
		w = max(1, int(math.Round(float64(w)*scale)))
		h = max(1, int(math.Round(float64(h)*scale)))
		logo = resample(logo, w, h)
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
