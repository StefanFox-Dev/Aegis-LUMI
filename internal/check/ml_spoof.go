package check

import (
	"fmt"
	"math"

	"github.com/EraseMC/Aegis/internal/config"
	"github.com/EraseMC/Aegis/internal/ml"
	"github.com/EraseMC/Aegis/internal/player"
	"github.com/EraseMC/Aegis/internal/wire"
	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
)

const MLSpoofA = "MLSpoof_A"

const mlWindowSize = 40

type mlSpoof struct {
	violations
	model             *ml.Model
	ticks             int
	movingTicks       int
	discreteMoveTicks int
	rawMags           [mlWindowSize]float64
	rawMagCount       int
	prevDYaw          float64
	snapEvents        int
	microPitch        int
	yawSteps          int
	zeroDelayUses     int
	totalUses         int
	sprintJumpSyncs   int
	headDesyncSum     float64
	deltaErrSum       float64
	prevPos           [3]float32
	hasPrev           bool
	highConfStreak    int
	lastFlag          uint64
}

func newMLSpoof(s config.Check) *mlSpoof {
	return &mlSpoof{
		violations: newViolations(MLSpoofA, s),
		model:      ml.DefaultModel(),
	}
}

func (m *mlSpoof) Name() string            { return MLSpoofA }
func (m *mlSpoof) OnJoin(_ *player.Player) {}

func (m *mlSpoof) OnAction(p *player.Player, action any, _ uint64) {
	if u, ok := action.(wire.UseItem); ok {
		m.totalUses++
		if (p.LastAttackTick > 0 && u.Tick-p.LastAttackTick <= 1) || (p.LastSlotChangeTick > 0 && u.Tick-p.LastSlotChangeTick <= 1) {
			m.zeroDelayUses++
		}
	}
}

func (m *mlSpoof) Serverbound(p *player.Player, pk packet.Packet, at uint64) {
	in, ok := pk.(*packet.PlayerAuthInput)
	if !ok || !p.HasExtendedInput || !p.Ready(at) || p.View.RTT > 300 {
		m.hasPrev = false
		return
	}

	if !m.hasPrev {
		m.prevPos = [3]float32(in.Position)
		m.hasPrev = true
		return
	}

	// Movement & analog features
	rx, ry := float64(p.RawMove[0]), float64(p.RawMove[1])
	mag := math.Hypot(rx, ry)
	if mag > 0.05 {
		m.movingTicks++
		if isDiscreteWASD(rx, ry) {
			m.discreteMoveTicks++
		}
		if m.rawMagCount < mlWindowSize {
			m.rawMags[m.rawMagCount] = mag
			m.rawMagCount++
		}
	}

	// Rotation & aim snap features
	dYaw := math.Abs(wrapAngleDeg(float64(in.Yaw - p.PreviousYaw)))
	dPitch := math.Abs(float64(in.Pitch - p.PreviousPitch))
	if dPitch > 0 && dPitch < 0.15 {
		m.microPitch++
	}
	if dYaw > 0.2 && dYaw < 15 {
		m.yawSteps++
	}
	if m.prevDYaw > 25 && dYaw < 0.5 {
		m.snapEvents++
	}
	m.prevDYaw = dYaw

	// Sprint + jump sync
	if player.Input(in.InputData, packet.InputFlagStartJumping) && player.Input(in.InputData, packet.InputFlagSprinting) {
		m.sprintJumpSyncs++
	}

	// Head-body yaw desync
	headDesync := math.Abs(wrapAngleDeg(float64(in.Yaw - in.HeadYaw)))
	m.headDesyncSum += headDesync / 180.0

	// Delta prediction consistency
	dx := float64(in.Position[0] - m.prevPos[0])
	dy := float64(in.Position[1] - m.prevPos[1])
	dz := float64(in.Position[2] - m.prevPos[2])
	deltaErr := math.Sqrt(math.Pow(dx-float64(p.Delta[0]), 2) + math.Pow(dy-float64(p.Delta[1]), 2) + math.Pow(dz-float64(p.Delta[2]), 2))
	m.deltaErrSum += deltaErr
	m.prevPos = [3]float32(in.Position)

	m.ticks++
	if m.ticks < mlWindowSize {
		return
	}

	f := m.buildFeatures(p)
	m.resetWindow()

	prob, reason := m.model.Predict(f)
	if prob >= 0.88 {
		m.highConfStreak++
		if m.highConfStreak >= 3 && at-m.lastFlag >= 2000 {
			m.lastFlag = at
			m.fail(p, 1, fmt.Sprintf("prob=%.3f streak=%d reason=%s", prob, m.highConfStreak, reason))
		}
	} else {
		m.highConfStreak = max(0, m.highConfStreak-1)
		m.pass(0.05)
	}
}

func (m *mlSpoof) buildFeatures(p *player.Player) ml.Features {
	var discreteRatio, analogVar float64
	if m.movingTicks > 0 {
		discreteRatio = float64(m.discreteMoveTicks) / float64(m.movingTicks)
	}
	if m.rawMagCount > 1 {
		var sum, mean float64
		for i := 0; i < m.rawMagCount; i++ {
			sum += m.rawMags[i]
		}
		mean = sum / float64(m.rawMagCount)
		for i := 0; i < m.rawMagCount; i++ {
			d := m.rawMags[i] - mean
			analogVar += d * d
		}
		analogVar /= float64(m.rawMagCount)
	}
	var zeroUseRatio float64
	if m.totalUses > 0 {
		zeroUseRatio = float64(m.zeroDelayUses) / float64(m.totalUses)
	}
	var claimedTouch, claimedMouse float64
	if p.InputMode == player.InputModeTouch {
		claimedTouch = 1
	} else if p.InputMode == player.InputModeMouse {
		claimedMouse = 1
	}
	return ml.Features{
		DiscreteMoveRatio:   discreteRatio,
		AnalogVariance:      analogVar,
		YawStepConsistency:  min(1.0, float64(m.yawSteps)/float64(mlWindowSize)),
		PitchMicroRatio:     min(1.0, float64(m.microPitch)/float64(mlWindowSize)),
		AimSnapRatio:        min(1.0, float64(m.snapEvents)/5.0),
		ZeroDelayUseRatio:   zeroUseRatio,
		SprintJumpSyncRatio: min(1.0, float64(m.sprintJumpSyncs)/6.0),
		HeadBodyDesync:      min(1.0, m.headDesyncSum/float64(mlWindowSize)),
		DeltaPosError:       min(1.0, m.deltaErrSum/float64(mlWindowSize)),
		ClaimedTouch:        claimedTouch,
		ClaimedMouse:        claimedMouse,
		LoginSpoofPrior:     p.LoginSpoofScore,
	}
}

func (m *mlSpoof) resetWindow() {
	m.ticks = 0
	m.movingTicks = 0
	m.discreteMoveTicks = 0
	m.rawMagCount = 0
	m.snapEvents = 0
	m.microPitch = 0
	m.yawSteps = 0
	m.zeroDelayUses = 0
	m.totalUses = 0
	m.sprintJumpSyncs = 0
	m.headDesyncSum = 0
	m.deltaErrSum = 0
}

func wrapAngleDeg(a float64) float64 {
	for a > 180 {
		a -= 360
	}
	for a < -180 {
		a += 360
	}
	return a
}
