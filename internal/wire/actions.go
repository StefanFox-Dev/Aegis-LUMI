package wire

const (
	UseItemFlagProjectile uint8 = 1 << 0
	UseItemFlagConsumable uint8 = 1 << 1
)

type UseItem struct {
	Session    uint64
	Time       uint64
	Tick       uint64
	ActionType uint8
	Slot       int32
	ItemId     int32
	Flags      uint8
}

func (m UseItem) Encode() []byte {
	return frame(TypeUseItem, func(w *Writer) {
		w.U64(m.Session)
		w.U64(m.Time)
		w.U64(m.Tick)
		w.U8(m.ActionType)
		w.U32(uint32(m.Slot))
		w.U32(uint32(m.ItemId))
		w.U8(m.Flags)
	})
}

func DecodeUseItem(body []byte) (UseItem, error) {
	r := NewReader(body)
	m := UseItem{
		Session:    r.U64(),
		Time:       r.U64(),
		Tick:       r.U64(),
		ActionType: r.U8(),
		Slot:       int32(r.U32()),
		ItemId:     int32(r.U32()),
		Flags:      r.U8(),
	}
	return m, r.Err()
}

type SlotChange struct {
	Session  uint64
	Time     uint64
	Tick     uint64
	FromSlot int32
	ToSlot   int32
}

func (m SlotChange) Encode() []byte {
	return frame(TypeSlotChange, func(w *Writer) {
		w.U64(m.Session)
		w.U64(m.Time)
		w.U64(m.Tick)
		w.U32(uint32(m.FromSlot))
		w.U32(uint32(m.ToSlot))
	})
}

func DecodeSlotChange(body []byte) (SlotChange, error) {
	r := NewReader(body)
	m := SlotChange{
		Session:  r.U64(),
		Time:     r.U64(),
		Tick:     r.U64(),
		FromSlot: int32(r.U32()),
		ToSlot:   int32(r.U32()),
	}
	return m, r.Err()
}

type InventoryAction struct {
	Session    uint64
	Time       uint64
	Tick       uint64
	WindowId   int32
	SourceSlot int32
	DestSlot   int32
	Kind       uint8
}

func (m InventoryAction) Encode() []byte {
	return frame(TypeInventoryAction, func(w *Writer) {
		w.U64(m.Session)
		w.U64(m.Time)
		w.U64(m.Tick)
		w.U32(uint32(m.WindowId))
		w.U32(uint32(m.SourceSlot))
		w.U32(uint32(m.DestSlot))
		w.U8(m.Kind)
	})
}

func DecodeInventoryAction(body []byte) (InventoryAction, error) {
	r := NewReader(body)
	m := InventoryAction{
		Session:    r.U64(),
		Time:       r.U64(),
		Tick:       r.U64(),
		WindowId:   int32(r.U32()),
		SourceSlot: int32(r.U32()),
		DestSlot:   int32(r.U32()),
		Kind:       r.U8(),
	}
	return m, r.Err()
}
