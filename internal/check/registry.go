package check

import (
	"github.com/EraseMC/Aegis/internal/config"
	"github.com/EraseMC/Aegis/internal/player"
)

type factory func(settings config.Check) player.Check

var factories = map[string]factory{
	TimerA:       func(s config.Check) player.Check { return newTimer(s) },
	AutoclickerA: func(s config.Check) player.Check { return newAutoclicker(s) },
	BadPacketA:   func(s config.Check) player.Check { return newBadPacket(s) },
	ReachA:       func(s config.Check) player.Check { return newCombat(ReachA, s, false) },
	KillAuraA:    func(s config.Check) player.Check { return newCombat(KillAuraA, s, true) },
	SpeedA:       func(s config.Check) player.Check { return newMovement(SpeedA, s, false) },
	FlyA:         func(s config.Check) player.Check { return newMovement(FlyA, s, true) },
	KillAuraB:   func(s config.Check) player.Check { return newAura(s) },
	AutoCritA:   func(s config.Check) player.Check { return newJumpCheck(AutoCritA, s, true) },
	VelocityA:   func(s config.Check) player.Check { return newVelocity(s) },
	NoClipA:     func(s config.Check) player.Check { return newNoClip(s) },
	JesusA:      func(s config.Check) player.Check { return newJesus(s) },
	FastLadderA: func(s config.Check) player.Check { return newFastLadder(s) },
	StepA:       func(s config.Check) player.Check { return newStep(s) },
	NoSlowA:     func(s config.Check) player.Check { return newNoSlow(s) },
	ElytraA:     func(s config.Check) player.Check { return newElytra(s) },
	BlinkA:      func(s config.Check) player.Check { return newBlink(s) },
	LoginSpoofA: func(s config.Check) player.Check { return newLoginSpoof(s) },
	InputSpoofA: func(s config.Check) player.Check { return newInputSpoof(s) },
	MLSpoofA:    func(s config.Check) player.Check { return newMLSpoof(s) },
}

func Build(cfg config.Config) []player.Check {
	checks := make([]player.Check, 0, len(factories))
	for _, name := range []string{
		TimerA, AutoclickerA, BadPacketA, ReachA, KillAuraA, SpeedA, FlyA, KillAuraB,
		AutoCritA, VelocityA, NoClipA, JesusA, FastLadderA, StepA, NoSlowA,
		ElytraA, BlinkA, LoginSpoofA, InputSpoofA, MLSpoofA,
	} {
		settings := cfg.Check(name)
		if settings.Enabled {
			checks = append(checks, factories[name](settings))
		}
	}
	return checks
}
