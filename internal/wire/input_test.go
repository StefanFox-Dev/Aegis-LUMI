package wire

import (
	"bytes"
	"testing"
)

func TestInputControlsAndLegacyFrame(t *testing.T) {
	for _, control := range []uint8{0, ControlKnown | ControlJump | ControlJumpStart | ControlSprint | ControlJumpPressed, ControlKnown | ControlHorizontalCollision | ControlVerticalCollision | ControlSneak} {
		want := Input{Session: 7, Time: 1000, Tick: 42, Position: [3]float32{1, 2, 3}, Pitch: 4, Yaw: 5, HeadYaw: 6, Mode: 1, Missed: true, Control: control}
		_, body, err := ReadFrame(bytes.NewBuffer(want.Encode()), nil)
		if err != nil {
			t.Fatal(err)
		}
		got, err := DecodeInput(body)
		if err != nil || got != want {
			t.Fatalf("%+v %v", got, err)
		}
		if _, err := DecodeInput(append(body, 0, 0)); err == nil {
			t.Fatal("accepted unexpected fields")
		}
	}

	ext := Input{
		Session:          7,
		Time:             2000,
		Tick:             43,
		Position:         [3]float32{1, 2, 3},
		Pitch:            4,
		Yaw:              5,
		HeadYaw:          6,
		Mode:             1,
		Missed:           false,
		Control:          ControlKnown | ControlSprint,
		HasExtended:      true,
		ControlExt:       ControlExtUsingItem | ControlExtUp,
		Delta:            [3]float32{0.1, -0.08, 0.2},
		AnalogMove:       [2]float32{0, 1},
		RawMove:          [2]float32{0, 1},
		InteractRot:      [2]float32{4, 5},
		CameraOrient:     [3]float32{0, 0, 1},
		PlayMode:         1,
		InteractionModel: 1,
	}
	_, body, err := ReadFrame(bytes.NewBuffer(ext.Encode()), nil)
	if err != nil {
		t.Fatal(err)
	}
	got, err := DecodeInput(body)
	if err != nil || got != ext {
		t.Fatalf("extended input mismatch: %+v vs %+v (%v)", got, ext, err)
	}
}
