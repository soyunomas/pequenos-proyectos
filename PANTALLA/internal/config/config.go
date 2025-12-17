// ------ internal/config/config.go ----
package config

import (
	"encoding/json"
	"os"
)

const (
	EdgeRight = "right"
	EdgeLeft  = "left"
)

type ConfigServer struct {
	ClientEdge string `json:"client_edge"`
	Port       string `json:"port"`
	AuthToken  string `json:"auth_token"` // NUEVO
}

type ConfigClient struct {
	ServerIP  string `json:"server_ip"`
	Name      string `json:"name"`
	AuthToken string `json:"auth_token"` // NUEVO
}

type AppConfig struct {
	Server ConfigServer `json:"server"`
	Client ConfigClient `json:"client"`
}

func LoadConfig(filename string) (*AppConfig, error) {
	cfg := &AppConfig{
		Server: ConfigServer{
			ClientEdge: EdgeRight,
			Port:       ":8080",
			AuthToken:  "secret123", // Valor por defecto
		},
		Client: ConfigClient{
			ServerIP:  "127.0.0.1:8080",
			Name:      "ClientPC",
			AuthToken: "secret123", // Valor por defecto
		},
	}

	if _, err := os.Stat(filename); os.IsNotExist(err) {
		return cfg, SaveConfig(filename, cfg)
	}

	data, err := os.ReadFile(filename)
	if err != nil {
		return nil, err
	}

	if err := json.Unmarshal(data, cfg); err != nil {
		return nil, err
	}

	return cfg, nil
}

func SaveConfig(filename string, cfg *AppConfig) error {
	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filename, data, 0644)
}
