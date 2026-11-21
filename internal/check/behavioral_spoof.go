package check

import (
	"fmt"
	"math"

	"github.com/EraseMC/Aegis/internal/config"
	"github.com/EraseMC/Aegis/internal/player"
	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
)

const (
	InputSpoofA = "InputSpoof_A"
)

// InputSpoof_A detects behavioral mismatches between claimed InputMode and actual movement/interaction vectors.
type inputSpoof struct {
	violations
	touchDiscreteTicks int
	mouseAnalogTicks   int
	lastMode           uint32
	modeSwitches       []uint64
	lastFlag           uint64
}

func newInputSpoof(s config.Check) *inputSpoof {
	return &inputSpoof{violations: newViolations(InputSpoofA, s)}
}
func (c *inputSpoof) Name() string { return InputSpoofA }
func (c *inputSpoof) Serverbound(p *player.Player, pk packet.Packet, at uint64) {
	in, ok := pk.(*packet.PlayerAuthInput)
	if !ok || !p.HasExtendedInput || !p.Ready(at) {
		return
	}

	if c.lastMode != 0 && in.InputMode != c.lastMode {
		c.modeSwitches = append(c.modeSwitches, at)
		kept := c.modeSwitches[:0]
		for _, t := range c.modeSwitches {
			if at-t <= 3000 {
				kept = append(kept, t)
			}
		}
		c.modeSwitches = kept
		if len(c.modeSwitches) >= 4 && at-c.lastFlag >= 1000 {
			c.lastFlag = at
			c.fail(p, 1, fmt.Sprintf("rapid_mode_switch count=%d", len(c.modeSwitches)))
		}
	}
	c.lastMode = in.InputMode

	rx, ry := float64(p.RawMove[0]), float64(p.RawMove[1])
	mag := math.Hypot(rx, ry)
	if mag < 0.05 {
		return
	}

	discrete := isDiscreteWASD(rx, ry)
	if in.InputMode == player.InputModeTouch {
		if discrete {
			c.touchDiscreteTicks++
		} else {
			c.touchDiscreteTicks = max(0, c.touchDiscreteTicks-2)
		}
		if c.touchDiscreteTicks >= 70 && at-c.lastFlag >= 1500 {
			c.lastFlag = at
			c.touchDiscreteTicks = 0
			c.fail(p, 1, fmt.Sprintf("wasd_on_touch interaction_model=%d", p.InteractionModel))
		}
	} else if in.InputMode == player.InputModeMouse {
		if !discrete && mag > 0.15 {
			c.mouseAnalogTicks++
		} else {
			c.mouseAnalogTicks = max(0, c.mouseAnalogTicks-2)
		}
		if c.mouseAnalogTicks >= 45 && at-c.lastFlag >= 1500 {
			c.lastFlag = at
			c.mouseAnalogTicks = 0
			c.fail(p, 1, fmt.Sprintf("analog_on_mouse raw=(%.3f,%.3f)", rx, ry))
		}
	}
}

func isDiscreteWASD(x, y float64) bool {
	ax, ay := math.Abs(x), math.Abs(y)
	isUnit := func(v float64) bool {
		return v < 0.02 || math.Abs(v-1.0) < 0.02 || math.Abs(v-0.70710678) < 0.025
	}
	return isUnit(ax) && isUnit(ay)
}
