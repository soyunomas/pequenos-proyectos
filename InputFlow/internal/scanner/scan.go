package scanner

import (
	"fmt"
	"inputflow/internal/watcher"
	"os"
	"path/filepath"
	"strings"
	"time"
)

func ListDevices() {
	files, _ := filepath.Glob("/dev/input/by-id/*")
	fmt.Println("🔍 Dispositivos Detectados:")
	for _, f := range files {
		if strings.Contains(f, "-event-") {
			fmt.Printf("   👉 %s\n", filepath.Base(f))
		}
	}
}

func Start(deviceID string) {
	path := deviceID
	if !strings.HasPrefix(path, "/") {
		path = "/dev/input/by-id/" + deviceID
	}

	f, err := os.Open(path)
	if err != nil {
		fmt.Printf("❌ Error: %v\n", err)
		return
	}
	defer f.Close()

	fmt.Printf("✅ MONITORIZANDO: %s\n", filepath.Base(path))
	fmt.Println("👉 Presiona TECLAS o mueve la RUEDA DEL RATÓN para ver códigos.")
	fmt.Println("---------------------------------------------------------")

	// MEMORIA ESTÁTICA (Stack Allocation - Ultra Fast)
	var events [watcher.BatchSize]watcher.InputEvent
	// Buffer de bytes crudos reutilizable
	rawBuf := make([]byte, watcher.BatchSize*watcher.EventSize)

	for {
		// Pasamos puntero al array (&events)
		n, err := watcher.ReadBatch(f, &events, rawBuf)
		if err != nil {
			time.Sleep(1 * time.Second)
			continue
		}

		// Iteramos solo hasta 'n' (eventos leídos)
		for i := 0; i < n; i++ {
			e := &events[i] // Puntero para evitar copia del struct

			if e.Type == watcher.EV_SYN { 
				continue 
			}

			// --- BOTONES (EV_KEY) ---
			if e.Type == watcher.EV_KEY {
				state := "Release"
				if e.Value == 1 {
					state = "Press"
				} else if e.Value == 2 {
					state = "Hold"
				}
				fmt.Printf("[BOTÓN] Code %d (%s)\n", e.Code, state)
			}

			// --- RELATIVE (SCROLL/MOUSE) (EV_REL) ---
			if e.Type == watcher.EV_REL {
				if e.Value == 0 {
					continue
				}

				name := fmt.Sprintf("REL_AXIS_%d", e.Code)
				if e.Code == 8 {
					name = "SCROLL VERTICAL (Rueda)"
				}
				if e.Code == 6 {
					name = "SCROLL HORIZONTAL"
				}
				if e.Code == 0 {
					name = "RATON X"
				}
				if e.Code == 1 {
					name = "RATON Y"
				}

				fmt.Printf("[%s] Code %d | Value: %d\n", name, e.Code, e.Value)

				// Sugerencia TOML inteligente para Scroll Vertical
				if e.Code == 8 { 
					fmt.Printf("   📝 TOML Sugerido (Volumen):\n")
					fmt.Printf("   [device.rel_axes.%d]\n", e.Code)
					fmt.Printf("   threshold = 1\n")
					fmt.Printf("   on_pos = \"exec: pactl set-sink-volume @DEFAULT_SINK@ +5%%\" # Arriba\n")
					fmt.Printf("   on_neg = \"exec: pactl set-sink-volume @DEFAULT_SINK@ -5%%\" # Abajo\n")
					fmt.Println("   ---------------------------------------")
				}
			}
			
			// --- ABSOLUTE (JOYSTICKS) (EV_ABS) ---
			if e.Type == watcher.EV_ABS {
				// Filtro simple de ruido para la visualización
				if e.Value > 500 || e.Value < -500 {
					fmt.Printf("[JOYSTICK] Axis %d | Value: %d\n", e.Code, e.Value)
				}
			}
		}
	}
}
