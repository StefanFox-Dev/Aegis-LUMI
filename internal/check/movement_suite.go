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
	NoClipA     = "NoClip_A"
	JesusA      = "Jesus_A"
	FastLadderA = "FastLadder_A"
	StepA       = "Step_A"
	NoSlowA     = "NoSlow_A"
	ElytraA     = "Elytra_A"
	BlinkA      = "Blink_A"
)

type noClip struct {
	violations
	last     [3]float32
	tick, at uint64
	buffer   int
	lastFlag uint64
}

func newNoClip(s config.Check) *noClip { return &noClip{violations: newViolations(NoClipA, s)} }
func (c *noClip) Name() string          { return NoClipA }
func (c *noClip) Serverbound(p *player.Player, pk packet.Packet, at uint64) {
	in, ok := pk.(*packet.PlayerAuthInput)
	if !ok {
		return
	}
	pos := [3]float32(in.Position)
	pos[1] -= p.State.Offset
	if c.tick == 0 || in.Tick <= c.tick || in.Tick-c.tick > 3 || !p.Ready(at) {
		c.last, c.tick, c.at, c.buffer = pos, in.Tick, at, 0
		return
	}
	dist := math.Hypot(float64(pos[0]-c.last[0]), float64(pos[2]-c.last[2]))
	dy := math.Abs(float64(pos[1] - c.last[1]))
	c.last, c.tick, c.at = pos, in.Tick, at

	if p.State.Flags&wire.StateInsideSolid == 0 || p.State.Flags&wire.StateSpecial != 0 || (dist < 0.04 && dy < 0.04) {
		c.buffer = max(0, c.buffer-1)
		c.pass(0.02)
		return
	}
	c.buffer = min(20, c.buffer+1)
	if c.buffer < 4 || at-c.lastFlag < 500 {
		return
	}
	c.lastFlag = at
	c.fail(p, 1, fmt.Sprintf("dist=%.3f dy=%.3f", dist, dy))
}

type jesus struct {
	violations
	last     [3]float32
	tick, at uint64
	streak   int
	lastFlag uint64
}

func newJesus(s config.Check) *jesus { return &jesus{violations: newViolations(JesusA, s)} }
func (c *jesus) Name() string        { return JesusA }
func (c *jesus) Serverbound(p *player.Player, pk packet.Packet, at uint64) {
	in, ok := pk.(*packet.PlayerAuthInput)
	if !ok {
		return
	}
	pos := [3]float32(in.Position)
	pos[1] -= p.State.Offset
	if c.tick == 0 || in.Tick != c.tick+1 || at < p.GraceUntil ||
		p.State.Flags&(wire.StateFlying|wire.StateLagging|wire.StateDead|wire.StateFrozen|wire.StateGliding|wire.StateRiding|wire.StateSpinAttack) != 0 ||
		!p.View.Synced(at) {
		c.last, c.tick, c.at, c.streak = pos, in.Tick, at, 0
		return
	}
	horiz := math.Hypot(float64(pos[0]-c.last[0]), float64(pos[2]-c.last[2]))
	dy := float64(pos[1] - c.last[1])
	c.last, c.tick, c.at = pos, in.Tick, at

	inLiquid := p.State.Flags&wire.StateInLiquid != 0
	swimming := p.ControlExt&wire.ControlExtSwimming != 0
	ground := p.State.Flags&wire.StateGround != 0
	if !inLiquid || ground || swimming || horiz < 0.14 || math.Abs(dy) > 0.02 {
		c.streak = max(0, c.streak-2)
		c.pass(0.01)
		return
	}
	c.streak = min(30, c.streak+1)
	if c.streak < 12 || at-c.lastFlag < 500 {
		return
	}
	c.lastFlag = at
	c.fail(p, 1, fmt.Sprintf("horiz=%.3f dy=%.4f streak=%d", horiz, dy, c.streak))
}

type fastLadder struct {
	violations
	lastY    float32
	tick     uint64
	buffer   int
	lastFlag uint64
}

func newFastLadder(s config.Check) *fastLadder {
	return &fastLadder{violations: newViolations(FastLadderA, s)}
}
func (c *fastLadder) Name() string { return FastLadderA }
func (c *fastLadder) Serverbound(p *player.Player, pk packet.Packet, at uint64) {
	in, ok := pk.(*packet.PlayerAuthInput)
	if !ok {
		return
	}
	y := in.Position[1] - p.State.Offset
	if c.tick == 0 || in.Tick != c.tick+1 || at < p.GraceUntil ||
		p.State.Flags&(wire.StateFlying|wire.StateLagging|wire.StateDead|wire.StateFrozen|wire.StateGliding|wire.StateRiding|wire.StateSpinAttack) != 0 ||
		!p.View.Synced(at) {
		c.lastY, c.tick, c.buffer = y, in.Tick, 0
		return
	}
	dy := float64(y - c.lastY)
	c.lastY, c.tick = y, in.Tick

	if p.State.Flags&wire.StateOnLadder == 0 || p.State.Flags&wire.StateInLiquid != 0 || dy <= 0.145 {
		c.buffer = max(0, c.buffer-1)
		c.pass(0.01)
		return
	}
	c.buffer = min(20, c.buffer+1)
	if c.buffer < 5 || at-c.lastFlag < 500 {
		return
	}
	c.lastFlag = at
	c.fail(p, 1, fmt.Sprintf("dy=%.3f limit=0.145", dy))
}

type step struct {
	violations
	lastY    float32
	tick     uint64
	ground   bool
	buffer   int
	lastFlag uint64
}

func newStep(s config.Check) *step { return &step{violations: newViolations(StepA, s)} }
func (c *step) Name() string      { return StepA }
func (c *step) Serverbound(p *player.Player, pk packet.Packet, at uint64) {
	in, ok := pk.(*packet.PlayerAuthInput)
	if !ok {
		return
	}
	y := in.Position[1] - p.State.Offset
	ground := p.Grounded([3]float32(in.Position))
	if c.tick == 0 || in.Tick != c.tick+1 || !p.Ready(at) ||
		p.State.Flags&(wire.StateGliding|wire.StateRiding|wire.StateSpinAttack|wire.StateInLiquid|wire.StateOnLadder) != 0 {
		c.lastY, c.tick, c.ground, c.buffer = y, in.Tick, ground, 0
		return
	}
	dy := float64(y - c.lastY)
	wasGround := c.ground
	c.lastY, c.tick, c.ground = y, in.Tick, ground

	maxStep := max(0.65, float64(p.State.Jump)+0.15)
	if !wasGround || dy <= maxStep {
		c.buffer = max(0, c.buffer-1)
		c.pass(0.02)
		return
	}
	c.buffer = min(10, c.buffer+1)
	if c.buffer < 3 || at-c.lastFlag < 500 {
		return
	}
	c.lastFlag = at
	c.fail(p, 1, fmt.Sprintf("dy=%.3f max_step=%.3f", dy, maxStep))
}

type noSlow struct {
	violations
	last     [3]float32
	tick     uint64
	buffer   int
	lastFlag uint64
}

func newNoSlow(s config.Check) *noSlow { return &noSlow{violations: newViolations(NoSlowA, s)} }
func (c *noSlow) Name() string          { return NoSlowA }
func (c *noSlow) Serverbound(p *player.Player, pk packet.Packet, at uint64) {
	in, ok := pk.(*packet.PlayerAuthInput)
	if !ok {
		return
	}
	pos := [3]float32(in.Position)
	if c.tick == 0 || in.Tick != c.tick+1 || !p.Ready(at) ||
		p.State.Flags&(wire.StateOnIce|wire.StateInLiquid|wire.StateGliding|wire.StateRiding|wire.StateSpinAttack) != 0 {
		c.last, c.tick, c.buffer = pos, in.Tick, 0
		return
	}
	horiz := math.Hypot(float64(pos[0]-c.last[0]), float64(pos[2]-c.last[2]))
	c.last, c.tick = pos, in.Tick

	using := (p.State.Flags&wire.StateUsingItem != 0) && (p.ControlExt&wire.ControlExtUsingItem != 0)
	limit := max(0.145, float64(p.State.Speed)*1.25)
	if !using || horiz <= limit {
		c.buffer = max(0, c.buffer-1)
		c.pass(0.01)
		return
	}
	c.buffer = min(20, c.buffer+1)
	if c.buffer < 7 || at-c.lastFlag < 500 {
		return
	}
	c.lastFlag = at
	c.fail(p, 1, fmt.Sprintf("horiz=%.3f limit=%.3f", horiz, limit))
}

type elytra struct {
	violations
	last     [3]float32
	tick     uint64
	buffer   int
	lastFlag uint64
}

func newElytra(s config.Check) *elytra { return &elytra{violations: newViolations(ElytraA, s)} }
func (c *elytra) Name() string          { return ElytraA }
func (c *elytra) Serverbound(p *player.Player, pk packet.Packet, at uint64) {
	in, ok := pk.(*packet.PlayerAuthInput)
	if !ok {
		return
	}
	pos := [3]float32(in.Position)
	if c.tick == 0 || in.Tick != c.tick+1 || at < p.GraceUntil ||
		p.State.Flags&(wire.StateFlying|wire.StateLagging|wire.StateDead|wire.StateFrozen|wire.StateSpinAttack) != 0 ||
		!p.View.Synced(at) {
		c.last, c.tick, c.buffer = pos, in.Tick, 0
		return
	}
	horiz := math.Hypot(float64(pos[0]-c.last[0]), float64(pos[2]-c.last[2]))
	dy := float64(pos[1] - c.last[1])
	c.last, c.tick = pos, in.Tick

	gliding := p.State.Flags&wire.StateGliding != 0 || p.ControlExt&wire.ControlExtGliding != 0
	if !gliding || (horiz <= 2.6 && dy <= 1.2) {
		c.buffer = max(0, c.buffer-1)
		c.pass(0.01)
		return
	}
	c.buffer = min(20, c.buffer+1)
	if c.buffer < 6 || at-c.lastFlag < 500 {
		return
	}
	c.lastFlag = at
	c.fail(p, 1, fmt.Sprintf("horiz=%.3f dy=%.3f", horiz, dy))
}

type blink struct {
	violations
	last        [3]float32
	tick, at    uint64
	silenceAt   uint64
	burstCount  int
	burstStart  uint64
	buffer      int
	lastFlag    uint64
}

func newBlink(s config.Check) *blink { return &blink{violations: newViolations(BlinkA, s)} }
func (c *blink) Name() string        { return BlinkA }
func (c *blink) Serverbound(p *player.Player, pk packet.Packet, at uint64) {
	in, ok := pk.(*packet.PlayerAuthInput)
	if !ok {
		return
	}
	pos := [3]float32(in.Position)
	if c.tick == 0 || !p.Ready(at) {
		c.last, c.tick, c.at = pos, in.Tick, at
		c.burstCount, c.buffer = 0, 0
		return
	}
	if at-c.at > 750 {
		c.silenceAt = at
		c.burstCount = 1
		c.burstStart = at
	} else if c.silenceAt > 0 && at-c.burstStart <= 50 {
		c.burstCount++
	} else {
		c.silenceAt = 0
		c.burstCount = 0
	}

	dist := math.Sqrt(math.Pow(float64(pos[0]-c.last[0]), 2) + math.Pow(float64(pos[1]-c.last[1]), 2) + math.Pow(float64(pos[2]-c.last[2]), 2))
	dt := in.Tick - c.tick
	c.last, c.tick, c.at = pos, in.Tick, at

	if (dt == 1 && dist > 4.5 && p.State.Flags&(wire.StateGliding|wire.StateSpinAttack|wire.StateRiding) == 0) || c.burstCount >= 14 {
		c.buffer = min(10, c.buffer+1)
		if c.buffer >= 4 && at-c.lastFlag >= 1000 {
			c.lastFlag = at
			c.fail(p, 1, fmt.Sprintf("dist=%.3f dt=%d burst=%d", dist, dt, c.burstCount))
		}
		return
	}
	c.buffer = max(0, c.buffer-1)
	c.pass(0.01)
}
