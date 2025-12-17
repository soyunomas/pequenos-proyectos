// ------ internal/protocol/types.go ----
package protocol

import (
	"encoding/binary"
	"errors"
	"io"
)

// Tipo de mensaje
type MsgType uint8

const (
	Handshake      MsgType = 0
	MouseMove      MsgType = 1
	MouseDown      MsgType = 2
	MouseUp        MsgType = 3
	KeyDown        MsgType = 4
	KeyUp          MsgType = 5
	ClipboardText  MsgType = 6
	ClipboardImage MsgType = 7 // NUEVO: Tipo para imágenes
)

// Packet estructura los datos
type Packet struct {
	Type    MsgType
	X       int32
	Y       int32
	Button  string
	Payload []byte // CAMBIO: De string a []byte para soportar imágenes
}

// Encode escribe el paquete en formato binario
func (p *Packet) Encode(w io.Writer) error {
	// 1. Type
	if err := binary.Write(w, binary.BigEndian, p.Type); err != nil {
		return err
	}

	// 2. X, Y
	if err := binary.Write(w, binary.BigEndian, p.X); err != nil {
		return err
	}
	if err := binary.Write(w, binary.BigEndian, p.Y); err != nil {
		return err
	}

	// 3. Button
	btnBytes := []byte(p.Button)
	if err := binary.Write(w, binary.BigEndian, uint16(len(btnBytes))); err != nil {
		return err
	}
	if len(btnBytes) > 0 {
		if _, err := w.Write(btnBytes); err != nil {
			return err
		}
	}

	// 4. Payload (Len uint32 + Bytes)
	if err := binary.Write(w, binary.BigEndian, uint32(len(p.Payload))); err != nil {
		return err
	}
	if len(p.Payload) > 0 {
		if _, err := w.Write(p.Payload); err != nil {
			return err
		}
	}

	return nil
}

// Decode lee el paquete en formato binario
func (p *Packet) Decode(r io.Reader) error {
	// 1. Type
	if err := binary.Read(r, binary.BigEndian, &p.Type); err != nil {
		return err
	}

	// 2. X, Y
	if err := binary.Read(r, binary.BigEndian, &p.X); err != nil {
		return err
	}
	if err := binary.Read(r, binary.BigEndian, &p.Y); err != nil {
		return err
	}

	// 3. Button
	var btnLen uint16
	if err := binary.Read(r, binary.BigEndian, &btnLen); err != nil {
		return err
	}
	if btnLen > 0 {
		buf := make([]byte, btnLen)
		if _, err := io.ReadFull(r, buf); err != nil {
			return err
		}
		p.Button = string(buf)
	}

	// 4. Payload
	var payLen uint32
	if err := binary.Read(r, binary.BigEndian, &payLen); err != nil {
		return err
	}
	// Límite aumentado a 50MB para soportar imágenes HD sin comprimir
	if payLen > 50*1024*1024 {
		return errors.New("payload demasiado grande (max 50MB)")
	}

	if payLen > 0 {
		p.Payload = make([]byte, payLen)
		if _, err := io.ReadFull(r, p.Payload); err != nil {
			return err
		}
	}

	return nil
}
