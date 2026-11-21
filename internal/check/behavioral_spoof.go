package check

import (
	"fmt"
	"math"

	"github.com/EraseMC/Aegis/internal/config"
	"github.com/EraseMC/Aegis/internal/player"
	"github.com/EraseMC/Aegis/internal/wire"
	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
)

const (
	InputSpoofA = "InputSpoof_A"
	FastThrowA  = "FastThrow_A"
	AutoRefillA = "AutoRefill_A"
	MacroA      = "Macro_A"
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

// FastThrow_A detects rapid projectile throwing ("machine gun" potion/pearl/snowball).
type fastThrow struct {
	violations
	throws   []uint64
	lastFlag uint64
}

func newFastThrow(s config.Check) *fastThrow {
	return &fastThrow{violations: newViolations(FastThrowA, s)}
}
func (c *fastThrow) Name() string                                          { return FastThrowA }
func (c *fastThrow) OnJoin(_ *player.Player)                               {}
func (c *fastThrow) Serverbound(_ *player.Player, _ packet.Packet, _ uint64) {}
func (c *fastThrow) OnAction(p *player.Player, action any, at uint64) {
	u, ok := action.(wire.UseItem)
	if !ok || u.Flags&wire.UseItemFlagProjectile == 0 {
		return
	}
	c.throws = append(c.throws, u.Tick)
	kept := c.throws[:0]
	for _, t := range c.throws {
		if u.Tick >= t && u.Tick-t <= 20 {
			kept = append(kept, t)
		}
	}
	c.throws = kept
	if len(c.throws) >= 9 && at-c.lastFlag >= 500 {
		c.lastFlag = at
		c.fail(p, 1, fmt.Sprintf("throws_per_20t=%d item=%d", len(c.throws), u.ItemId))
	} else if len(c.throws) <= 4 {
		c.pass(0.02)
	}
}

// AutoRefill_A detects instant hotbar refilling from inventory during combat.
type autoRefill struct {
	violations
	hits     int
	lastFlag uint64
}

func newAutoRefill(s config.Check) *autoRefill {
	return &autoRefill{violations: newViolations(AutoRefillA, s)}
}
func (c *autoRefill) Name() string                                          { return AutoRefillA }
func (c *autoRefill) OnJoin(_ *player.Player)                               {}
func (c *autoRefill) Serverbound(_ *player.Player, _ packet.Packet, _ uint64) {}
func (c *autoRefill) OnAction(p *player.Player, action any, at uint64) {
	inv, ok := action.(wire.InventoryAction)
	if !ok || inv.DestSlot < 0 || inv.DestSlot > 8 || inv.SourceSlot < 9 {
		return
	}
	inCombat := (p.LastAttackAt > 0 && at-p.LastAttackAt < 750) || (p.LastDamageAt > 0 && at-p.LastDamageAt < 750)
	if !inCombat {
		c.hits = max(0, c.hits-1)
		return
	}
	c.hits++
	if c.hits >= 4 && at-c.lastFlag >= 1500 {
		c.lastFlag = at
		c.hits = 0
		c.fail(p, 1, fmt.Sprintf("combat_refill src=%d dst=%d", inv.SourceSlot, inv.DestSlot))
	}
}

// Macro_A detects rapid 1-2 tick slot->use->return cycles (AutoPot / AutoPearl macros).
type macroCheck struct {
	violations
	fromSlot   int32
	toSlot     int32
	switchedAt uint64
	used       bool
	cycles     int
	lastFlag   uint64
}

func newMacroCheck(s config.Check) *macroCheck {
	return &macroCheck{violations: newViolations(MacroA, s)}
}
func (c *macroCheck) Name() string                                          { return MacroA }
func (c *macroCheck) OnJoin(_ *player.Player)                               {}
func (c *macroCheck) Serverbound(_ *player.Player, _ packet.Packet, _ uint64) {}
func (c *macroCheck) OnAction(p *player.Player, action any, at uint64) {
	switch a := action.(type) {
	case wire.SlotChange:
		if c.used && a.ToSlot == c.fromSlot && a.Tick >= c.switchedAt && a.Tick-c.switchedAt <= 3 {
			c.cycles++
			c.used = false
			if c.cycles >= 4 && at-c.lastFlag >= 1500 {
				c.lastFlag = at
				c.cycles = 0
				c.fail(p, 1, fmt.Sprintf("auto_switch_use_return slot=%d dt=%d", c.toSlot, a.Tick-c.switchedAt))
			}
		} else {
			c.fromSlot = a.FromSlot
			c.toSlot = a.ToSlot
			c.switchedAt = a.Tick
			c.used = false
			if a.Tick-c.switchedAt > 10 {
				c.cycles = max(0, c.cycles-1)
			}
		}
	case wire.UseItem:
		if c.switchedAt > 0 && a.Tick >= c.switchedAt && a.Tick-c.switchedAt <= 2 {
			c.used = true
		}
	}
}
