package wire

const (
	ControlJump uint8 = 1 << iota
	ControlJumpStart
	ControlSprint
	ControlHorizontalCollision
	ControlVerticalCollision
	ControlJumpPressed
	ControlSneak
	ControlKnown
)

const (
	ControlExtUsingItem uint16 = 1 << iota
	ControlExtGliding
	ControlExtSwimming
	ControlExtSpinAttack
	ControlExtUp
	ControlExtDown
	ControlExtLeft
	ControlExtRight
)

type Input struct {
	Session, Time, Tick uint64
	Position            [3]float32
	Pitch, Yaw, HeadYaw float32
	Mode                uint32
	Missed              bool
	Control             uint8
	HasExtended         bool
	ControlExt          uint16
	Delta               [3]float32
	AnalogMove          [2]float32
	RawMove             [2]float32
	InteractRot         [2]float32
	CameraOrient        [3]float32
	PlayMode            uint32
	InteractionModel    uint32
}

func DecodeInput(body []byte) (Input, error) {
	r := NewReader(body)
	m := Input{Session: r.U64(), Time: r.U64(), Tick: r.U64()}
	for i := range m.Position {
		m.Position[i] = r.F32()
	}
	m.Pitch, m.Yaw, m.HeadYaw = r.F32(), r.F32(), r.F32()
	m.Mode = r.U32()
	m.Missed = r.U8() != 0
	if r.off < len(r.buf) {
		m.Control = r.U8()
	}
	if r.err == nil && r.off < len(r.buf) {
		m.HasExtended = true
		m.ControlExt = r.U16()
		for i := range m.Delta {
			m.Delta[i] = r.F32()
		}
		m.AnalogMove[0], m.AnalogMove[1] = r.F32(), r.F32()
		m.RawMove[0], m.RawMove[1] = r.F32(), r.F32()
		m.InteractRot[0], m.InteractRot[1] = r.F32(), r.F32()
		for i := range m.CameraOrient {
			m.CameraOrient[i] = r.F32()
		}
		m.PlayMode = r.U32()
		m.InteractionModel = r.U32()
	}
	return m, r.Err()
}

func (m Input) Encode() []byte {
	return frame(TypeInput, func(w *Writer) {
		w.U64(m.Session)
		w.U64(m.Time)
		w.U64(m.Tick)
		for _, v := range m.Position {
			w.F32(v)
		}
		w.F32(m.Pitch)
		w.F32(m.Yaw)
		w.F32(m.HeadYaw)
		w.U32(m.Mode)
		if m.Missed {
			w.U8(1)
		} else {
			w.U8(0)
		}
		if m.Control != 0 || m.HasExtended {
			w.U8(m.Control)
		}
		if m.HasExtended {
			w.U16(m.ControlExt)
			for _, v := range m.Delta {
				w.F32(v)
			}
			w.F32(m.AnalogMove[0])
			w.F32(m.AnalogMove[1])
			w.F32(m.RawMove[0])
			w.F32(m.RawMove[1])
			w.F32(m.InteractRot[0])
			w.F32(m.InteractRot[1])
			for _, v := range m.CameraOrient {
				w.F32(v)
			}
			w.U32(m.PlayMode)
			w.U32(m.InteractionModel)
		}
	})
}

type Attack struct{ Session, Time, Target uint64 }

func DecodeAttack(body []byte) (Attack, error) {
	r := NewReader(body)
	m := Attack{Session: r.U64(), Time: r.U64(), Target: r.U64()}
	return m, r.Err()
}
func (m Attack) Encode() []byte {
	return frame(TypeAttack, func(w *Writer) { w.U64(m.Session); w.U64(m.Time); w.U64(m.Target) })
}

func DecodeSwing(body []byte) (Packet, error) {
	r := NewReader(body)
	m := Packet{Session: r.U64(), Time: r.U64()}
	return m, r.Err()
}
