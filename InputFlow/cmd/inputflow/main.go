package main

import (
	"flag"
	"fmt"
	"inputflow/internal/config"
	"inputflow/internal/engine"
	"inputflow/internal/scanner"
	"log"
	"sync"
)

func main() {
	scanMode := flag.Bool("scan", false, "Modo Escáner")
	devID := flag.String("dev", "", "ID del dispositivo (para scan o force start)")
	cfgPath := flag.String("config", "config.toml", "Ruta de configuración")
	flag.Parse()

	// MODO SCANNER
	if *scanMode {
		if *devID == "" {
			scanner.ListDevices()
			return
		}
		scanner.Start(*devID)
		return
	}

	// MODO DAEMON (PRODUCCIÓN)
	fmt.Println("👻 InputFlow Daemon v1.0 Starting...")
	
	cfg, err := config.Load(*cfgPath)
	if err != nil {
		log.Fatalf("❌ Error cargando config: %v", err)
	}

	var wg sync.WaitGroup

	// Si el usuario pasa -dev, forzamos solo ese dispositivo (Debug mode)
	if *devID != "" {
		wg.Add(1)
		go func() {
			defer wg.Done()
			// Hack temporal: inyectar el ID si no está en config para testear
			engine.Start(cfg, *devID)
		}()
	} else {
		// Modo Normal: Arrancar todos los dispositivos definidos en TOML
		for _, dev := range cfg.Devices {
			wg.Add(1)
			go func(d config.Device) {
				defer wg.Done()
				engine.Start(cfg, d.MatchID)
			}(dev)
		}
	}

	if len(cfg.Devices) == 0 && *devID == "" {
		log.Println("⚠️  No hay dispositivos en config.toml ni argumento -dev")
	}

	wg.Wait() // Mantener vivo el programa
}
