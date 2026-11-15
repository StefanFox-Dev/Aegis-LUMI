package check

import (
	"testing"

	"github.com/EraseMC/Aegis/internal/config"
	"github.com/EraseMC/Aegis/internal/player"
	"github.com/EraseMC/Aegis/internal/wire"
	"github.com/sandertv/gophertunnel/minecraft/protocol"
	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
)

func TestLoginSpoofDetection(t *testing.T) {
	r := &recorder{flags: map[string]int{}, punished: map[string]wire.Action{}}
	// Windows TitleID (896928775) claiming Android (1) with iPhone model
	p := player.New(wire.Join{
		Session:     1,
		Name:        "Spoofer",
		DeviceOS:    1,
		InputMode:   player.InputModeTouch,
		DeviceModel: "iPhone15,2",
		DeviceId:    "00000000-0000-0000-0000-000000000000",
		TitleId:     "896928775",
		XboxAuthed:  true,
		HasExtended: true,
	}, r, Build(config.Default()))

	p.Serverbound(&packet.PlayerAuthInput{Tick: 1, InputData: protocol.NewInputFlags(packet.InputFlagCount)}, 1000)
	if r.flags[LoginSpoofA] == 0 {
		t.Fatal("expected LoginSpoof_A flag for TitleID/DeviceModel/DeviceOS mismatch")
	}
	if len(r.punished) > 0 {
		t.Fatalf("zero-harm default should not punish on LoginSpoof_A: %+v", r.punished)
	}
}

func TestFastThrowDetection(t *testing.T) {
	p, r := newPlayer(player.InputModeMouse)
	for tick := uint64(1); tick <= 12; tick++ {
		p.Action(wire.UseItem{
			Session: 1,
			Time:    1000 + tick*50,
			Tick:    tick,
			ItemId:  373,
			Flags:   wire.UseItemFlagProjectile,
		}, 1000+tick*50)
	}
	if r.flags[FastThrowA] == 0 {
		t.Fatal("expected FastThrow_A flag for 12 projectiles in 12 ticks")
	}
}

func TestInputSpoofWASDOnTouch(t *testing.T) {
	p, r := newPlayer(player.InputModeTouch)
	p.HasExtendedInput = true
	p.RawMove = [2]float32{0, 1}
	for tick := uint64(1); tick <= 80; tick++ {
		at := 10000 + tick*50
		ready(p, at, true)
		p.Serverbound(&packet.PlayerAuthInput{
			Tick:      tick,
			Position:  [3]float32{0, 1.621, float32(tick) * 0.1},
			InputMode: player.InputModeTouch,
			InputData: protocol.NewInputFlags(packet.InputFlagCount),
		}, at)
	}
	if r.flags[InputSpoofA] == 0 {
		t.Fatal("expected InputSpoof_A flag for exact WASD movement on Touch")
	}
}

func TestMLSpoofLegitAndCheater(t *testing.T) {
	// Legit touch player with analog movement must NOT be flagged
	p, r := newPlayer(player.InputModeTouch)
	p.HasExtendedInput = true
	for tick := uint64(1); tick <= 160; tick++ {
		at := 10000 + tick*50
		ready(p, at, true)
		p.RawMove = [2]float32{0.31 + float32(tick%5)*0.09, 0.72 - float32(tick%3)*0.07}
		p.Serverbound(&packet.PlayerAuthInput{
			Tick:      tick,
			Position:  [3]float32{0, 1.621, float32(tick) * 0.1},
			InputMode: player.InputModeTouch,
			InputData: protocol.NewInputFlags(packet.InputFlagCount),
		}, at)
	}
	if r.flags[MLSpoofA] != 0 {
		t.Fatalf("legit touch player flagged by MLSpoof_A: %d", r.flags[MLSpoofA])
	}
}
