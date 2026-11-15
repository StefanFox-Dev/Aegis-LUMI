package player

import (
	"github.com/EraseMC/Aegis/internal/wire"
	"github.com/sandertv/gophertunnel/minecraft/protocol"
	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
)

const (
	InputModeMouse = packet.InputModeMouse
	InputModeTouch = packet.InputModeTouch
)

type Reporter interface {
	Flag(p *Player, check string, violations, max float64, data string)
	Punish(p *Player, check string, violations float64, action wire.Action, data string)
}

type Check interface {
	Name() string
	Serverbound(p *Player, pk packet.Packet, at uint64)
}

type ActionCheck interface {
	Check
	OnJoin(p *Player)
	OnAction(p *Player, action any, at uint64)
}

type Player struct {
	Session   uint64
	Name      string
	XUID      string
	Protocol  uint32
	Version   string
	DeviceOS  int32
	InputMode uint32

	JoinInfo wire.Join

	ClientTick                    uint64
	Punished                      bool
	View                          View
	State                         wire.State
	Position                      [3]float32
	PreviousPosition              [3]float32
	Directions                    [4][3]float32
	DirectionCount, DirectionNext int
	Yaw, Pitch, HeadYaw           float32
	PreviousYaw, PreviousPitch    float32
	HasExtendedInput              bool
	ControlExt                    uint16
	Delta                         [3]float32
	AnalogMove                    [2]float32
	RawMove                       [2]float32
	InteractRot                   [2]float32
	CameraOrient                  [3]float32
	PlayMode                      uint32
	InteractionModel              uint32

	GraceUntil                    uint64
	CombatGraceUntil              uint64
	LastInput                     uint64
	ControlsKnown                 bool
	LastSwing                     uint64
	AnimationSeen                 bool
	LastAttackTick                uint64
	LastAttackAt                  uint64
	LastSlotChangeTick            uint64
	LastSlotChangeAt              uint64
	LastDamageAt                  uint64
	LoginSpoofScore               float64

	checks   []Check
	reporter Reporter
}

func New(join wire.Join, reporter Reporter, checks []Check) *Player {
	p := &Player{
		Session:   join.Session,
		Name:      join.Name,
		XUID:      join.XUID,
		Protocol:  join.Protocol,
		Version:   join.Version,
		DeviceOS:  join.DeviceOS,
		InputMode: join.InputMode,
		JoinInfo:  join,
		checks:    checks,
		View:      NewView(),
		reporter:  reporter,
	}
	for _, c := range checks {
		if ac, ok := c.(ActionCheck); ok {
			ac.OnJoin(p)
		}
	}
	return p
}

func (p *Player) Touch() bool {
	return p.InputMode == InputModeTouch
}

func (p *Player) Serverbound(pk packet.Packet, at uint64) {
	if ack, ok := pk.(*packet.NetworkStackLatency); ok {
		p.View.Ack(ack.Timestamp, at)
		return
	}
	if input, ok := pk.(*packet.PlayerAuthInput); ok {
		p.InputMode = input.InputMode
		p.PreviousPosition = p.Position
		p.Position = [3]float32(input.Position)
		p.PreviousYaw = p.Yaw
		p.PreviousPitch = p.Pitch
		p.Yaw = input.Yaw
		p.Pitch = input.Pitch
		p.HeadYaw = input.HeadYaw
		p.Directions[p.DirectionNext] = Direction(input.Yaw, input.Pitch)
		p.DirectionNext = (p.DirectionNext + 1) % len(p.Directions)
		p.DirectionCount = min(p.DirectionCount+1, len(p.Directions))
		if Input(input.InputData, packet.InputFlagMissedSwing) {
			p.LastSwing = at
		}
	}
	if tx, ok := pk.(*packet.InventoryTransaction); ok {
		if hit, ok := tx.TransactionData.(*protocol.UseItemOnEntityTransactionData); ok && hit.ActionType == protocol.UseItemOnEntityActionAttack {
			p.LastAttackTick = p.ClientTick
			p.LastAttackAt = at
		}
	}
	if swing, ok := pk.(*packet.LevelSoundEvent); ok && swing.SoundType == packet.SoundEventAttackNoDamage {
		p.LastSwing = at
	}
	if animation, ok := pk.(*packet.Animate); ok && animation.ActionType == packet.AnimateActionSwingArm {
		p.LastSwing = at
		p.AnimationSeen = true
	}
	for _, c := range p.checks {
		c.Serverbound(p, pk, at)
	}
	if input, ok := pk.(*packet.PlayerAuthInput); ok {
		p.ClientTick = input.Tick
		p.LastInput = at
	}
}

func (p *Player) Grounded(position [3]float32) bool {
	feet := position[1] - p.State.Offset
	return p.State.Flags&wire.StateGround != 0 && feet-p.State.Position[1] < 0.035 && feet-p.State.Position[1] > -0.035
}

func (p *Player) Action(action any, at uint64) {
	switch a := action.(type) {
	case wire.SlotChange:
		p.LastSlotChangeTick = a.Tick
		p.LastSlotChangeAt = at
	}
	for _, c := range p.checks {
		if ac, ok := c.(ActionCheck); ok {
			ac.OnAction(p, action, at)
		}
	}
}

func (p *Player) Observe(u wire.Observation) {
	if u.Target == p.Session && u.Kind == wire.Teleport {
		p.View = NewView()
		p.CombatGraceUntil = max(p.CombatGraceUntil, u.Time+2000)
	}
	if u.Target == p.Session && u.Kind == wire.Velocity {
		p.LastDamageAt = u.Time
	}
	if u.Target == p.Session && (u.Kind == wire.Teleport || u.Kind == wire.Velocity) {
		p.GraceUntil = max(p.GraceUntil, u.Time+2000)
	}
	if u.Kind == wire.BlockChange {
		p.GraceUntil = max(p.GraceUntil, u.Time+500)
		return
	}
	p.View.Observe(u)
}

func (p *Player) Ready(at uint64) bool {
	return p.State.Time > 0 && at >= p.State.Time && at-p.State.Time <= 750 && at >= p.GraceUntil &&
		p.State.Flags&(wire.StateFlying|wire.StateSpecial|wire.StateLagging|wire.StateDead|wire.StateFrozen) == 0 &&
		p.View.Synced(at)
}

func (p *Player) CombatReady(at uint64) bool {
	return p.State.Time > 0 && at >= p.State.Time && at-p.State.Time <= 750 && at >= p.CombatGraceUntil &&
		p.State.Flags&(wire.StateFlying|wire.StateLagging|wire.StateDead|wire.StateFrozen) == 0 && p.View.Synced(at)
}

func (p *Player) Flag(check string, violations, max float64, data string) {
	p.reporter.Flag(p, check, violations, max, data)
}

func (p *Player) Punish(check string, violations float64, action wire.Action, data string) {
	if p.Punished {
		return
	}
	p.Punished = action != wire.ActionNone
	p.reporter.Punish(p, check, violations, action, data)
}

func Input(flags protocol.InputFlags, flag int) bool {
	return flag < flags.Len() && flags.Load(flag)
}
