package config

import (
	"os"
	"github.com/pelletier/go-toml/v2"
)

type Config struct {
	Devices []Device `toml:"device"`
}

type Device struct {
	Name    string            `toml:"name"`
	MatchID string            `toml:"match_id"`
	Buttons map[string]Action `toml:"buttons"`
	Axes    map[string]Axis   `toml:"axes"`     // EV_ABS (Joysticks)
	RelAxes map[string]RelAxis `toml:"rel_axes"` // EV_REL (Ratón/Scroll)
}

type Action struct {
	Command string `toml:"exec"`
	Mode    string `toml:"mode"`
}

type Axis struct {
	Deadzone float64 `toml:"deadzone"`
	OnLow    string  `toml:"on_low"`
	OnHigh   string  `toml:"on_high"`
}

// Nueva struct para Scroll/Ratón
type RelAxis struct {
	Threshold int    `toml:"threshold"` // Sensibilidad (1 para Scroll, 50+ para Ratón)
	OnPos     string `toml:"on_pos"`    // Valor positivo (Scroll Arriba / Derecha)
	OnNeg     string `toml:"on_neg"`    // Valor negativo (Scroll Abajo / Izquierda)
}

func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil { return nil, err }
	var cfg Config
	err = toml.Unmarshal(data, &cfg)
	return &cfg, err
}
