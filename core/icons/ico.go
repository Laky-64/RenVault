package icons

import (
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"image"
	"image/color"
	"image/gif"
	"image/jpeg"
	"image/png"
)

var (
	magicPNG  = []byte{0x89, 'P', 'N', 'G'}
	magicGIF  = []byte("GIF8")
	magicJPEG = []byte{0xFF, 0xD8}
)

var ErrUnsupportedFormat = errors.New("unknown icon format")

func decode(data []byte) (image.Image, error) {
	switch {
	case bytes.HasPrefix(data, magicPNG):
		return png.Decode(bytes.NewReader(data))
	case bytes.HasPrefix(data, magicGIF):
		return gif.Decode(bytes.NewReader(data))
	case bytes.HasPrefix(data, magicJPEG):
		return jpeg.Decode(bytes.NewReader(data))
	case isICO(data):
		return decodeICO(data)
	default:
		return nil, ErrUnsupportedFormat
	}
}

func isICO(data []byte) bool {
	if len(data) < 6 {
		return false
	}
	if binary.LittleEndian.Uint16(data[0:2]) != 0 {
		return false
	}
	kind := binary.LittleEndian.Uint16(data[2:4])
	if kind != 1 && kind != 2 {
		return false
	}
	return binary.LittleEndian.Uint16(data[4:6]) > 0
}

type icoEntry struct {
	width  int
	height int
	bits   int
	offset uint32
	length uint32
}

func decodeICO(data []byte) (image.Image, error) {
	count := int(binary.LittleEndian.Uint16(data[4:6]))
	if len(data) < 6+count*16 {
		return nil, fmt.Errorf("ICO troncato: servono %d byte di directory", 6+count*16)
	}

	var best *icoEntry
	for i := range count {
		raw := data[6+i*16 : 6+(i+1)*16]
		entry := icoEntry{
			width:  sizeOrElse(raw[0], 256),
			height: sizeOrElse(raw[1], 256),
			bits:   int(binary.LittleEndian.Uint16(raw[6:8])),
			length: binary.LittleEndian.Uint32(raw[8:12]),
			offset: binary.LittleEndian.Uint32(raw[12:16]),
		}
		if int(entry.offset)+int(entry.length) > len(data) {
			continue
		}
		if best == nil || better(entry, *best) {
			best = new(entry)
		}
	}
	if best == nil {
		return nil, errors.New("ICO senza frame utilizzabili")
	}

	frame := data[best.offset : best.offset+best.length]
	if bytes.HasPrefix(frame, magicPNG) {
		return png.Decode(bytes.NewReader(frame))
	}
	return decodeDIB(frame)
}

func sizeOrElse(value byte, fallback int) int {
	if value == 0 {
		return fallback
	}
	return int(value)
}

func better(candidate, current icoEntry) bool {
	a, b := candidate.width*candidate.height, current.width*current.height
	if a != b {
		return a > b
	}
	return candidate.bits > current.bits
}

func decodeDIB(frame []byte) (image.Image, error) {
	const headerSize = 40
	if len(frame) < headerSize {
		return nil, errors.New("DIB troncato")
	}

	width := int(int32(binary.LittleEndian.Uint32(frame[4:8])))
	height := int(int32(binary.LittleEndian.Uint32(frame[8:12]))) / 2
	bits := int(binary.LittleEndian.Uint16(frame[14:16]))
	compression := binary.LittleEndian.Uint32(frame[16:20])
	paletteLen := int(binary.LittleEndian.Uint32(frame[32:36]))

	if width <= 0 || height <= 0 || width > 1024 || height > 1024 {
		return nil, fmt.Errorf("DIB con dimensioni implausibili: %dx%d", width, height)
	}
	if compression != 0 {
		return nil, fmt.Errorf("DIB compresso non supportato (compression=%d)", compression)
	}

	palette, err := readPalette(frame[headerSize:], bits, paletteLen)
	if err != nil {
		return nil, err
	}

	pixels := frame[headerSize+len(palette)*4:]
	xorStride := ((width*bits + 31) / 32) * 4
	andStride := ((width + 31) / 32) * 4

	if len(pixels) < xorStride*height {
		return nil, errors.New("DIB con dati XOR insufficienti")
	}
	andMask := []byte(nil)
	if len(pixels) >= xorStride*height+andStride*height {
		andMask = pixels[xorStride*height:]
	}

	img := image.NewNRGBA(image.Rect(0, 0, width, height))
	for y := range height {
		row := pixels[(height-1-y)*xorStride:]
		for x := range width {
			c, err := pixelAt(row, x, bits, palette)
			if err != nil {
				return nil, err
			}
			if andMask != nil && transparentAt(andMask, x, height-1-y, andStride) {
				c.A = 0
			}
			img.SetNRGBA(x, y, c)
		}
	}

	if bits == 32 && andMask == nil && fullyTransparent(img) {
		for i := 3; i < len(img.Pix); i += 4 {
			img.Pix[i] = 0xFF
		}
	}

	return img, nil
}

func readPalette(data []byte, bits, declared int) ([]color.NRGBA, error) {
	if bits > 8 {
		return nil, nil
	}
	size := declared
	if size == 0 {
		size = 1 << bits
	}
	if len(data) < size*4 {
		return nil, errors.New("DIB con palette troncata")
	}
	palette := make([]color.NRGBA, size)
	for i := range size {
		palette[i] = color.NRGBA{
			B: data[i*4],
			G: data[i*4+1],
			R: data[i*4+2],
			A: 0xFF,
		}
	}
	return palette, nil
}

func pixelAt(row []byte, x, bits int, palette []color.NRGBA) (color.NRGBA, error) {
	switch bits {
	case 1, 4, 8:
		index := paletteIndex(row, x, bits)
		if index >= len(palette) {
			return color.NRGBA{}, fmt.Errorf("indice palette %d fuori dai %d colori", index, len(palette))
		}
		return palette[index], nil
	case 24:
		off := x * 3
		return color.NRGBA{B: row[off], G: row[off+1], R: row[off+2], A: 0xFF}, nil
	case 32:
		off := x * 4
		return color.NRGBA{B: row[off], G: row[off+1], R: row[off+2], A: row[off+3]}, nil
	default:
		return color.NRGBA{}, fmt.Errorf("DIB a %d bit per pixel non supportato", bits)
	}
}

func paletteIndex(row []byte, x, bits int) int {
	switch bits {
	case 8:
		return int(row[x])
	case 4:
		b := row[x/2]
		if x%2 == 0 {
			return int(b >> 4)
		}
		return int(b & 0x0F)
	default:
		b := row[x/8]
		return int((b >> (7 - uint(x%8))) & 1)
	}
}

func transparentAt(mask []byte, x, row, stride int) bool {
	offset := row*stride + x/8
	if offset >= len(mask) {
		return false
	}
	return (mask[offset]>>(7-uint(x%8)))&1 == 1
}

func fullyTransparent(img *image.NRGBA) bool {
	for i := 3; i < len(img.Pix); i += 4 {
		if img.Pix[i] != 0 {
			return false
		}
	}
	return true
}
