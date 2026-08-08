package ctaphid

import (
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"time"

	"github.com/Laky-64/RenVault/internal/ctap"
)

const (
	PacketSize   = 64
	initDataSize = PacketSize - 7
	contDataSize = PacketSize - 5
	maxMessage   = 7609
	broadcastCID = 0xffffffff
)

const (
	cmdPing      byte = 0x01
	cmdMsg       byte = 0x03
	cmdLock      byte = 0x04
	cmdInit      byte = 0x06
	cmdWink      byte = 0x08
	cmdCBOR      byte = 0x10
	cmdCancel    byte = 0x11
	cmdKeepalive byte = 0x3b
	cmdError     byte = 0x3f
)

const (
	errInvalidCommand byte = 0x01
	errInvalidLength  byte = 0x03
	errInvalidSeq     byte = 0x04
	errInvalidChannel byte = 0x0b
	errOther          byte = 0x7f
)

const (
	statusProcessing byte = 0x01
	statusUpNeeded   byte = 0x02
	keepaliveEvery        = 100 * time.Millisecond
)

const (
	capabilityWink = 0x01
	capabilityCBOR = 0x04
	capabilityNMSG = 0x08
)

type Device interface {
	ReadReport(ctx context.Context) ([]byte, error)
	WriteReport(ctx context.Context, report []byte) error
	Close() error
}

type Server struct {
	device Device
	ctap   *ctap.Authenticator
	nextID uint32
}

func NewServer(device Device, authenticator *ctap.Authenticator) *Server {
	return &Server{device: device, ctap: authenticator, nextID: 1}
}

func (s *Server) Serve(ctx context.Context) error {
	for {
		channel, command, payload, err := s.receive(ctx)
		if err != nil {
			if errors.Is(err, io.EOF) || errors.Is(err, context.Canceled) {
				return nil
			}
			return err
		}
		if err := s.dispatch(ctx, channel, command, payload); err != nil {
			return err
		}
	}
}

func (s *Server) dispatch(ctx context.Context, channel uint32, command byte, payload []byte) error {
	switch command {
	case cmdInit:
		return s.handleInit(ctx, channel, payload)
	case cmdPing:
		return s.send(ctx, channel, cmdPing, payload)
	case cmdCBOR:
		if len(payload) == 0 {
			return s.fail(ctx, channel, errInvalidLength)
		}
		return s.handleCBOR(ctx, channel, payload)
	case cmdCancel:
		return nil
	case cmdMsg:
		return s.fail(ctx, channel, errInvalidCommand)
	case cmdWink, cmdLock:
		return s.fail(ctx, channel, errInvalidCommand)
	default:
		return s.fail(ctx, channel, errInvalidCommand)
	}
}

func (s *Server) handleCBOR(ctx context.Context, channel uint32, payload []byte) error {
	type outcome struct {
		response []byte
		err      error
	}
	done := make(chan outcome, 1)
	go func() {
		response, err := s.ctap.Handle(ctx, payload)
		done <- outcome{response: response, err: err}
	}()

	for {
		select {
		case got := <-done:
			if got.err != nil {
				return s.fail(ctx, channel, errOther)
			}
			return s.send(ctx, channel, cmdCBOR, got.response)
		case <-time.After(keepaliveEvery):
			if err := s.send(ctx, channel, cmdKeepalive, []byte{statusUpNeeded}); err != nil {
				return err
			}
		case <-ctx.Done():
			return ctx.Err()
		}
	}
}

func (s *Server) handleInit(ctx context.Context, channel uint32, payload []byte) error {
	if len(payload) != 8 {
		return s.fail(ctx, channel, errInvalidLength)
	}
	assigned := channel
	if channel == broadcastCID {
		assigned = s.allocate()
	}
	response := make([]byte, 0, 17)
	response = append(response, payload...)
	response = binary.BigEndian.AppendUint32(response, assigned)
	response = append(response, 2, 1, 0, 0, capabilityCBOR|capabilityNMSG)
	return s.send(ctx, channel, cmdInit, response)
}

func (s *Server) allocate() uint32 {
	s.nextID++
	if s.nextID == 0 || s.nextID == broadcastCID {
		s.nextID = 1
	}
	return s.nextID
}

func (s *Server) fail(ctx context.Context, channel uint32, code byte) error {
	return s.send(ctx, channel, cmdError, []byte{code})
}

func (s *Server) receive(ctx context.Context) (uint32, byte, []byte, error) {
	report, err := s.device.ReadReport(ctx)
	if err != nil {
		return 0, 0, nil, err
	}
	if len(report) < PacketSize {
		return 0, 0, nil, fmt.Errorf("ctaphid: short report of %d bytes", len(report))
	}
	channel := binary.BigEndian.Uint32(report[:4])
	if report[4]&0x80 == 0 {
		return 0, 0, nil, fmt.Errorf("ctaphid: expected an initialisation packet")
	}
	command := report[4] &^ 0x80
	total := int(binary.BigEndian.Uint16(report[5:7]))
	if total > maxMessage {
		return channel, command, nil, s.fail(ctx, channel, errInvalidLength)
	}

	payload := make([]byte, 0, total)
	chunk := min(total, initDataSize)
	payload = append(payload, report[7:7+chunk]...)

	for seq := 0; len(payload) < total; seq++ {
		report, err = s.device.ReadReport(ctx)
		if err != nil {
			return 0, 0, nil, err
		}
		if len(report) < PacketSize {
			return 0, 0, nil, fmt.Errorf("ctaphid: short continuation of %d bytes", len(report))
		}
		if binary.BigEndian.Uint32(report[:4]) != channel {
			return 0, 0, nil, fmt.Errorf("ctaphid: continuation on another channel")
		}
		if int(report[4]) != seq {
			return channel, command, nil, s.fail(ctx, channel, errInvalidSeq)
		}
		chunk = min(total-len(payload), contDataSize)
		payload = append(payload, report[5:5+chunk]...)
	}
	return channel, command, payload, nil
}

func (s *Server) send(ctx context.Context, channel uint32, command byte, payload []byte) error {
	if len(payload) > maxMessage {
		return fmt.Errorf("ctaphid: response of %d bytes is too long", len(payload))
	}
	packet := make([]byte, PacketSize)
	binary.BigEndian.PutUint32(packet[:4], channel)
	packet[4] = command | 0x80
	binary.BigEndian.PutUint16(packet[5:7], uint16(len(payload)))
	sent := copy(packet[7:], payload)
	if err := s.device.WriteReport(ctx, packet); err != nil {
		return err
	}
	for seq := 0; sent < len(payload); seq++ {
		clear(packet)
		binary.BigEndian.PutUint32(packet[:4], channel)
		packet[4] = byte(seq)
		sent += copy(packet[5:], payload[sent:])
		if err := s.device.WriteReport(ctx, packet); err != nil {
			return err
		}
	}
	return nil
}
