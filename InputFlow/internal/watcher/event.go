package watcher

import (
	"encoding/binary"
	"os"
)

// Constantes del Kernel Linux (input-event-codes.h)
// Exportadas (Mayúscula) para ser usadas por engine y scanner.
const (
	EV_SYN = 0x00
	EV_KEY = 0x01
	EV_REL = 0x02
	EV_ABS = 0x03
	EV_MSC = 0x04
)

// Configuración de Buffer
const (
	BatchSize = 64 // Leemos en bloques de 64 eventos
	EventSize = 24 // Tamaño exacto de struct input_event en 64-bit
)

// Estructura alineada a lo que devuelve el kernel de Linux (C struct input_event)
// En 64-bit: TimeSec(8) + TimeUsec(8) + Type(2) + Code(2) + Value(4) = 24 bytes.
type InputEvent struct {
	TimeSec  int64
	TimeUsec int64
	Type     uint16
	Code     uint16
	Value    int32
}

// ReadBatch optimizado:
// - NO retorna slice para evitar escapes al Heap (Zero GC Pressure).
// - Recibe punteros a memoria pre-asignada (Stack o Pool).
func ReadBatch(f *os.File, events *[BatchSize]InputEvent, rawBuf []byte) (int, error) {
	// Verificación de seguridad para evitar pánicos (Bounds Check Elimination hint)
	if len(rawBuf) < BatchSize*EventSize {
		return 0, nil
	}

	// 1. Syscall de lectura (Directamente al buffer de bytes)
	n, err := f.Read(rawBuf)
	if err != nil {
		return 0, err
	}

	// 2. Decodificación manual (Más rápido que binary.Read)
	count := 0
	limit := n - (n % EventSize) // Aseguramos alineación perfecta

	for i := 0; i < limit; i += EventSize {
		if count >= BatchSize {
			break
		}
		
		e := &events[count]
		
		// Offset 16 = 8 bytes Sec + 8 bytes Usec.
		// Saltamos el timestamp para ahorrar CPU si no se usa.
		// e.TimeSec = int64(binary.LittleEndian.Uint64(rawBuf[i : i+8]))
		// e.TimeUsec = int64(binary.LittleEndian.Uint64(rawBuf[i+8 : i+16]))
		
		e.Type = binary.LittleEndian.Uint16(rawBuf[i+16 : i+18])
		e.Code = binary.LittleEndian.Uint16(rawBuf[i+18 : i+20])
		e.Value = int32(binary.LittleEndian.Uint32(rawBuf[i+20 : i+24]))

		count++
	}
	
	return count, nil
}
