package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"

	"github.com/EraseMC/Aegis/internal/wire"
)

type Check struct {
	Enabled bool    `json:"enabled"`
	Max     float64 `json:"max"`
	Action  string  `json:"action"`
}

func (c Check) Punishment() wire.Action {
	switch strings.ToLower(c.Action) {
	case "kick":
		return wire.ActionKick
	case "ban":
		return wire.ActionBan
	default:
		return wire.ActionNone
	}
}

type Config struct {
	Listen string           `json:"listen"`
	Debug  bool             `json:"debug"`
	Checks map[string]Check `json:"checks"`
}

func Default() Config {
	return Config{
		Listen: "127.0.0.1:19140",
		Checks: map[string]Check{
			"Timer_A":       {Enabled: true, Max: 10, Action: "kick"},
			"Autoclicker_A": {Enabled: true, Max: 10, Action: "kick"},
			"BadPacket_A":   {Enabled: true, Max: 1, Action: "kick"},
			"Reach_A":       {Enabled: true, Max: 10, Action: "kick"},
			"KillAura_A":    {Enabled: true, Max: 10, Action: "kick"},
			"Speed_A":       {Enabled: true, Max: 10, Action: "kick"},
			"Fly_A":         {Enabled: true, Max: 10, Action: "kick"},
			"KillAura_B":    {Enabled: true, Max: 10, Action: "none"},
			"AutoCrit_A":    {Enabled: true, Max: 10, Action: "none"},
			"Velocity_A":    {Enabled: true, Max: 10, Action: "none"},
			"NoJumpDelay_A": {Enabled: true, Max: 10, Action: "none"},
			"NoClip_A":      {Enabled: true, Max: 10, Action: "none"},
			"Jesus_A":       {Enabled: true, Max: 10, Action: "none"},
			"FastLadder_A":  {Enabled: true, Max: 10, Action: "none"},
			"Step_A":        {Enabled: true, Max: 10, Action: "none"},
			"NoSlow_A":      {Enabled: true, Max: 10, Action: "none"},
			"Elytra_A":      {Enabled: true, Max: 10, Action: "none"},
			"Blink_A":       {Enabled: true, Max: 10, Action: "none"},
			"LoginSpoof_A":  {Enabled: true, Max: 6, Action: "none"},
			"InputSpoof_A":  {Enabled: true, Max: 10, Action: "none"},
			"FastThrow_A":   {Enabled: true, Max: 6, Action: "kick"},
			"AutoRefill_A":  {Enabled: true, Max: 8, Action: "none"},
			"Macro_A":       {Enabled: true, Max: 8, Action: "none"},
			"MLSpoof_A":     {Enabled: true, Max: 8, Action: "none"},
		},
	}
}

func Load(path string) (Config, error) {
	cfg := Default()
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return cfg, nil
	}
	if err != nil {
		return cfg, err
	}
	if err := json.Unmarshal(data, &cfg); err != nil {
		return cfg, fmt.Errorf("parse %s: %w", path, err)
	}
	if cfg.Checks == nil {
		cfg.Checks = make(map[string]Check)
	}
	for name, check := range Default().Checks {
		if _, ok := cfg.Checks[name]; !ok {
			cfg.Checks[name] = check
		}
	}
	return cfg, nil
}

func (c Config) Check(name string) Check {
	return c.Checks[name]
}
