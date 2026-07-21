package icons

import (
	"bytes"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"math"
	"slices"
)

const (
	canvasSize   = 64
	contentRatio = 0.625
	maxUpscale   = 2.0
	alphaFloor   = 16

	tileLuma           = 0.024
	contrastThreshold  = 2.5
	readablePercentile = 0.75

	haloScale        = 1.5
	haloRadius       = 12
	haloAlpha        = 0.3
	haloAlphaOnLight = 0.35
)

var lightTile = color.NRGBA{R: 0xEC, G: 0xEE, B: 0xF2, A: 0xFF}

func process(src image.Image) ([]byte, error) {
	box, ok := contentBounds(toNRGBA(src))
	if !ok {
		return nil, fmt.Errorf("%w: no opaque pixels", ErrUnsupportedFormat)
	}
	logo := crop(toNRGBA(src), box)

	crisp := fit(logo, contentRatio)
	needsLight := contrast(luminanceOf(crisp), tileLuma) < contrastThreshold

	alpha := haloAlpha
	if needsLight {
		alpha = haloAlphaOnLight
	}

	tile := over(crisp, halo(logo, alpha))
	if needsLight {
		tile = over(tile, filled(lightTile))
	}

	var buf bytes.Buffer
	if err := png.Encode(&buf, tile); err != nil {
		return nil, fmt.Errorf("png encoding: %w", err)
	}
	return buf.Bytes(), nil
}

func filled(c color.NRGBA) *image.NRGBA {
	out := image.NewNRGBA(image.Rect(0, 0, canvasSize, canvasSize))
	for i := 0; i+3 < len(out.Pix); i += 4 {
		out.Pix[i], out.Pix[i+1], out.Pix[i+2], out.Pix[i+3] = c.R, c.G, c.B, c.A
	}
	return out
}

func halo(logo *image.NRGBA, alpha float64) *image.NRGBA {
	spread := fit(logo, haloScale)
	blurred := blur(spread, haloRadius)

	for i := 3; i < len(blurred.Pix); i += 4 {
		blurred.Pix[i] = clamp8(float64(blurred.Pix[i]) * alpha)
	}
	return blurred
}

func over(top, bottom *image.NRGBA) *image.NRGBA {
	out := image.NewNRGBA(image.Rect(0, 0, canvasSize, canvasSize))
	for i := 0; i+3 < len(out.Pix); i += 4 {
		ta := float64(top.Pix[i+3]) / 0xFF
		ba := float64(bottom.Pix[i+3]) / 0xFF
		oa := ta + ba*(1-ta)
		if oa == 0 {
			continue
		}
		for c := range 3 {
			t := float64(top.Pix[i+c])
			b := float64(bottom.Pix[i+c])
			out.Pix[i+c] = clamp8((t*ta + b*ba*(1-ta)) / oa)
		}
		out.Pix[i+3] = clamp8(oa * 0xFF)
	}
	return out
}

func blur(img *image.NRGBA, radius int) *image.NRGBA {
	side := img.Bounds().Dx()
	buf := make([]float64, side*side*4)
	for i, v := range img.Pix {
		if i%4 == 3 {
			buf[i] = float64(v)
			continue
		}
		buf[i] = float64(v) * float64(img.Pix[i/4*4+3]) / 0xFF
	}

	tmp := make([]float64, len(buf))
	for range 3 {
		boxPass(buf, tmp, side, radius, true)
		boxPass(tmp, buf, side, radius, false)
	}

	out := image.NewNRGBA(image.Rect(0, 0, side, side))
	for i := 0; i < len(buf); i += 4 {
		a := buf[i+3]
		out.Pix[i+3] = clamp8(a)
		if a < 1 {
			continue
		}
		for c := range 3 {
			out.Pix[i+c] = clamp8(buf[i+c] * 0xFF / a)
		}
	}
	return out
}

func boxPass(src, dst []float64, side, radius int, horizontal bool) {
	for line := range side {
		for c := range 4 {
			var sum float64
			var n int
			at := func(i int) int {
				if horizontal {
					return (line*side+i)*4 + c
				}
				return (i*side+line)*4 + c
			}
			for i := 0; i <= radius && i < side; i++ {
				sum += src[at(i)]
				n++
			}
			for i := range side {
				dst[at(i)] = sum / float64(n)
				if add := i + radius + 1; add < side {
					sum += src[at(add)]
					n++
				}
				if drop := i - radius; drop >= 0 {
					sum -= src[at(drop)]
					n--
				}
			}
		}
	}
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

func fit(img *image.NRGBA, ratio float64) *image.NRGBA {
	w, h := img.Bounds().Dx(), img.Bounds().Dy()
	target := canvasSize * ratio

	scale := min(target/float64(w), target/float64(h))
	scale = min(scale, maxUpscale*ratio)

	dstW := max(1, int(math.Round(float64(w)*scale)))
	dstH := max(1, int(math.Round(float64(h)*scale)))

	scaled := resample(img, dstW, dstH)

	out := image.NewNRGBA(image.Rect(0, 0, canvasSize, canvasSize))
	offX, offY := (canvasSize-dstW)/2, (canvasSize-dstH)/2
	for y := range dstH {
		for x := range dstW {
			if offX+x < 0 || offY+y < 0 || offX+x >= canvasSize || offY+y >= canvasSize {
				continue
			}
			out.SetNRGBA(offX+x, offY+y, scaled.NRGBAAt(x, y))
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

func luminanceOf(img *image.NRGBA) float64 {
	var lumaList []float64
	for i := 0; i+3 < len(img.Pix); i += 4 {
		if img.Pix[i+3] < alphaFloor {
			continue
		}
		lumaList = append(lumaList, relativeLuminance(img.Pix[i], img.Pix[i+1], img.Pix[i+2]))
	}
	if len(lumaList) == 0 {
		return 1
	}
	slices.Sort(lumaList)
	return lumaList[int(float64(len(lumaList)-1)*readablePercentile)]
}

func relativeLuminance(r, g, b uint8) float64 {
	return 0.2126*linearize(r) + 0.7152*linearize(g) + 0.0722*linearize(b)
}

func linearize(v uint8) float64 {
	c := float64(v) / 0xFF
	if c <= 0.04045 {
		return c / 12.92
	}
	return math.Pow((c+0.055)/1.055, 2.4)
}

func contrast(a, b float64) float64 {
	hi, lo := math.Max(a, b), math.Min(a, b)
	return (hi + 0.05) / (lo + 0.05)
}
