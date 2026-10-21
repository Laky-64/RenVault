//go:build linux

package ctaphid

import (
	"context"
	"encoding/binary"
	"fmt"
	"os"
)

const (
	uhidDestroy uint32 = 1
	uhidStart   uint32 = 2
	uhidStop    uint32 = 3
	uhidOpen    uint32 = 4
	uhidClose   uint32 = 5
	uhidOutput  uint32 = 6
	uhidCreate2 uint32 = 11
	uhidInput2  uint32 = 12
)

const (
	uhidDataMax   = 4096
	uhidEventSize = 4 + 128 + 64 + 64 + 2 + 2 + 4 + 4 + 4 + 4 + uhidDataMax
	busUSB        = 0x03
)

var reportDescriptor = []byte{
	0x06, 0xd0, 0xf1,
	0x09, 0x01,
	0xa1, 0x01,
	0x09, 0x20,
	0x15, 0x00,
	0x26, 0xff, 0x00,
	0x75, 0x08,
	0x95, 0x40,
	0x81, 0x02,
	0x09, 0x21,
	0x15, 0x00,
	0x26, 0xff, 0x00,
	0x75, 0x08,
	0x95, 0x40,
	0x91, 0x02,
	0xc0,
}

type UHID struct {
	file *os.File
}

func Open(name string) (*UHID, error) {
	file, err := os.OpenFile("/dev/uhid", os.O_RDWR, 0)
	if err != nil {
		return nil, fmt.Errorf("ctaphid: open /dev/uhid: %w", err)
	}
	device := &UHID{file: file}
	if err := device.create(name); err != nil {
		file.Close()
		return nil, err
	}
	return device, nil
}

func (d *UHID) create(name string) error {
	event := make([]byte, uhidEventSize)
	binary.LittleEndian.PutUint32(event[0:], uhidCreate2)
	copy(event[4:4+127], name)
	copy(event[4+128:4+128+63], "renvault")
	offset := 4 + 128 + 64 + 64
	binary.LittleEndian.PutUint16(event[offset:], uint16(len(reportDescriptor)))
	binary.LittleEndian.PutUint16(event[offset+2:], busUSB)
	binary.LittleEndian.PutUint32(event[offset+4:], 0x1209)
	binary.LittleEndian.PutUint32(event[offset+8:], 0xa1b2)
	binary.LittleEndian.PutUint32(event[offset+12:], 1)
	copy(event[offset+20:], reportDescriptor)
	if _, err := d.file.Write(event); err != nil {
		return fmt.Errorf("ctaphid: create hid device: %w", err)
	}
	return nil
}

func (d *UHID) ReadReport(ctx context.Context) ([]byte, error) {
	buf := make([]byte, uhidEventSize)
	for {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		n, err := d.file.Read(buf)
		if err != nil {
			return nil, fmt.Errorf("ctaphid: read uhid: %w", err)
		}
		if n < 4 {
			continue
		}
		switch binary.LittleEndian.Uint32(buf[0:]) {
		case uhidOutput:
			size := int(binary.LittleEndian.Uint16(buf[4+uhidDataMax:]))
			data := buf[4 : 4+min(size, uhidDataMax)]
			if len(data) > PacketSize && data[0] == 0 {
				data = data[1:]
			}
			if len(data) < PacketSize {
				continue
			}
			report := make([]byte, PacketSize)
			copy(report, data)
			return report, nil
		case uhidStart, uhidStop, uhidOpen, uhidClose:
			continue
		default:
			continue
		}
	}
}

func (d *UHID) WriteReport(_ context.Context, report []byte) error {
	if len(report) != PacketSize {
		return fmt.Errorf("ctaphid: report is %d bytes, want %d", len(report), PacketSize)
	}
	event := make([]byte, 4+2+uhidDataMax)
	binary.LittleEndian.PutUint32(event[0:], uhidInput2)
	binary.LittleEndian.PutUint16(event[4:], uint16(len(report)))
	copy(event[6:], report)
	if _, err := d.file.Write(event); err != nil {
		return fmt.Errorf("ctaphid: write report: %w", err)
	}
	return nil
}

func (d *UHID) Close() error {
	event := make([]byte, 4)
	binary.LittleEndian.PutUint32(event, uhidDestroy)
	d.file.Write(event)
	return d.file.Close()
}

func Available() bool {
	file, err := os.OpenFile("/dev/uhid", os.O_RDWR, 0)
	if err != nil {
		return false
	}
	file.Close()
	return true
}
