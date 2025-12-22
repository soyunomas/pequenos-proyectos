package engine

import (
	"inputflow/internal/config"
	"inputflow/internal/watcher"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Configuración del Motor
const (
	ReconnectDelay = 3 * time.Second
	WorkerCount    = 4   // Número de hilos paralelos para ejecutar comandos
	CommandQueue   = 100 // Buffer de comandos (Backpressure)
	HoldThreshold  = 600 * time.Millisecond // Tiempo para considerar "Hold"
)

// Start inicia el ciclo de vida del dispositivo con gestión de errores y reconexión.
func Start(globalCfg *config.Config, deviceID string) {
	// 1. Identificar configuración
	devCfg := findConfig(globalCfg, deviceID)
	
	// Si no hay config, logueamos pero intentamos arrancar (modo monitor ciego)
	devName := deviceID
	if devCfg != nil {
		devName = devCfg.Name
	}

	log.Printf("🔌 Inicializando controlador para: %s", devName)

	// 2. Worker Pool (Patrón: Fan-Out)
	// Canal con buffer para evitar bloqueos en el hilo principal (Input Reader)
	cmdChan := make(chan string, CommandQueue)
	for i := 0; i < WorkerCount; i++ {
		go commandWorker(i, cmdChan)
	}

	// 3. Bucle de Supervisión (Hot-Plug)
	for {
		err := runEventLoop(deviceID, devCfg, cmdChan)
		if err != nil {
			log.Printf("⚠️  Dispositivo %s desconectado/error: %v", devName, err)
			log.Printf("♻️  Reintentando en %v...", ReconnectDelay)
			time.Sleep(ReconnectDelay)
		}
	}
}

// runEventLoop maneja la lectura cruda y la máquina de estados.
func runEventLoop(deviceID string, cfg *config.Device, cmdChan chan<- string) error {
	path := resolvePath(deviceID)
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()

	log.Printf("🚀 Engine Ready: %s (Leída directa de Kernel)", filepath.Base(path))

	// Optimizaciones de Memoria (Mapas uint16 para acceso rápido)
	btnActions := make(map[uint16]config.Action)
	axisConf := make(map[uint16]config.Axis)
	relConf := make(map[uint16]config.RelAxis)

	if cfg != nil {
		for k, v := range cfg.Buttons { c, _ := strconv.Atoi(k); btnActions[uint16(c)] = v }
		for k, v := range cfg.Axes { c, _ := strconv.Atoi(k); axisConf[uint16(c)] = v }
		for k, v := range cfg.RelAxes { c, _ := strconv.Atoi(k); relConf[uint16(c)] = v }
	}

	// Estado en Memoria (Stateful Logic)
	// Guardamos el estado anterior de los ejes para detectar cambios de zona
	axisState := make(map[uint16]int) // -1 (Low), 0 (Dead), 1 (High)
	// Guardamos timers para botones (Hold logic)
	holdTimers := make(map[uint16]*time.Timer)
	var timerLock sync.Mutex

	// Buffer Estático (Zero-Allocation)
	var events [watcher.BatchSize]watcher.InputEvent
	rawBuf := make([]byte, watcher.BatchSize*watcher.EventSize)

	for {
		n, err := watcher.ReadBatch(f, &events, rawBuf)
		if err != nil { return err }

		for i := 0; i < n; i++ {
			e := &events[i]
			if e.Type == watcher.EV_SYN { continue }

			// ---------------------------------------------------------
			// 1. BOTONES (Press, Release, Hold)
			// ---------------------------------------------------------
			if e.Type == watcher.EV_KEY {
				action, exists := btnActions[e.Code]
				if !exists { continue }

				if e.Value == 1 { // PRESS
					// Disparar evento "press" inmediato
					if action.Mode == "press" || action.Mode == "" {
						select {
						case cmdChan <- action.Command:
						default: // Drop si cola llena (Backpressure)
						}
					}

					// Iniciar Timer para "Hold"
					if action.Mode == "hold" {
						timerLock.Lock()
						// Cancelar timer anterior si existía (rebote raro)
						if t, ok := holdTimers[e.Code]; ok { t.Stop() }
						
						// Crear nuevo timer
						holdTimers[e.Code] = time.AfterFunc(HoldThreshold, func() {
							select {
							case cmdChan <- action.Command:
							default:
							}
							log.Printf("⏳ HOLD Ejecutado: Botón %d", e.Code)
						})
						timerLock.Unlock()
					}

				} else if e.Value == 0 { // RELEASE
					// Cancelar Hold si se soltó antes de tiempo
					if action.Mode == "hold" {
						timerLock.Lock()
						if t, ok := holdTimers[e.Code]; ok {
							t.Stop()
							delete(holdTimers, e.Code)
						}
						timerLock.Unlock()
					}

					// Ejecutar acción Release
					if action.Mode == "release" {
						select {
						case cmdChan <- action.Command:
						default:
						}
					}
				}
			}

			// ---------------------------------------------------------
			// 2. JOYSTICKS / CRUCETAS (EV_ABS)
			// ---------------------------------------------------------
			if e.Type == watcher.EV_ABS {
				conf, exists := axisConf[e.Code]
				if !exists { continue }

				// Normalización y Deadzone
				// Los ejes suelen ir de -32767 a 32767.
				// Deadzone 0.5 significa ignorar rango [-16383, 16383]
				limit := int32(32767.0 * conf.Deadzone)
				
				currentState := 0
				if e.Value > limit {
					currentState = 1 // High
				} else if e.Value < -limit {
					currentState = -1 // Low
				}

				// Edge Detection: Solo disparar si cambiamos de zona
				lastState := axisState[e.Code]
				if currentState != lastState {
					axisState[e.Code] = currentState
					
					if currentState == 1 && conf.OnHigh != "" {
						select { case cmdChan <- conf.OnHigh:; default: }
					}
					if currentState == -1 && conf.OnLow != "" {
						select { case cmdChan <- conf.OnLow:; default: }
					}
				}
			}

			// ---------------------------------------------------------
			// 3. RATÓN / SCROLL (EV_REL)
			// ---------------------------------------------------------
			if e.Type == watcher.EV_REL {
				conf, exists := relConf[e.Code]
				if !exists { continue }

				threshold := int32(conf.Threshold)
				if threshold == 0 { threshold = 1 }

				// Acumulador simple (en sistemas complejos usaríamos float delta)
				if e.Value >= threshold && conf.OnPos != "" {
					select { case cmdChan <- conf.OnPos:; default: }
				} else if e.Value <= -threshold && conf.OnNeg != "" {
					select { case cmdChan <- conf.OnNeg:; default: }
				}
			}
		}
	}
}

// commandWorker consume comandos de la cola y los ejecuta.
func commandWorker(id int, cmds <-chan string) {
	for cmdStr := range cmds {
		if cmdStr == "" { continue }
		
		// Parsing básico "exec: comando args..."
		// Si no tiene prefijo exec:, lo tratamos como shell directo por flexibilidad
		cleanCmd := strings.TrimPrefix(cmdStr, "exec: ")
		
		// Optimización: Usar sh -c permite tuberías y expansiones, 
		// aunque es un poco más lento que exec directo. 
		// Para máxima velocidad: separar args y usar exec.Command(bin, args...)
		cmd := exec.Command("sh", "-c", cleanCmd)
		
		// Fire and forget
		err := cmd.Run()
		if err != nil {
			log.Printf("[Worker %d] ❌ Error exec '%s': %v", id, cleanCmd, err)
		}
	}
}

// Helpers
func resolvePath(id string) string {
	if strings.HasPrefix(id, "/") { return id }
	return "/dev/input/by-id/" + id
}

func findConfig(cfg *config.Config, id string) *config.Device {
	for _, d := range cfg.Devices {
		if d.MatchID == id || strings.Contains(id, d.MatchID) {
			// Return copy
			val := d
			return &val
		}
	}
	return nil
}
