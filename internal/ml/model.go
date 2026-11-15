package ml

import (
	"encoding/json"
	"math"
	"os"
)

// Features represents a normalized behavioral window (~40 moving/combat ticks)
// extracted without blocking or allocations on the hot path.
type Features struct {
	DiscreteMoveRatio        float64 // [0..1] fraction of moving ticks snapping to 8-way WASD angles
	AnalogVariance           float64 // variance of raw move magnitude (near 0 for digital WASD, >0 for touch/pad)
	YawStepConsistency       float64 // [0..1] GCD/quantized mouse step consistency
	PitchMicroRatio          float64 // [0..1] fraction of fine pitch adjustments < 0.15 deg
	AimSnapRatio             float64 // [0..1] sudden >25 deg yaw snap followed by <0.5 deg lock
	CrosshairOffTargetRatio  float64 // [0..1] hits where crosshair ray missed target AABB
	ZeroDelayUseRatio        float64 // [0..1] item uses within 0-1 ticks of attack or slot switch
	SprintJumpSyncRatio      float64 // [0..1] synchronized sprint+jump starts with minimal interval
	HeadBodyDesync           float64 // normalized |Yaw - HeadYaw| / 180
	DeltaPosError            float64 // mean |client_delta - pos_delta|
	ClaimedTouch             float64 // 1.0 if InputMode == Touch, else 0.0
	ClaimedMouse             float64 // 1.0 if InputMode == Mouse, else 0.0
	LoginSpoofPrior          float64 // [0..1] prior anomaly score from LoginSpoof_A
}

// TreeNode is a compact decision stump / small binary tree node for GBDT inference.
type TreeNode struct {
	FeatureIdx int     `json:"f"`
	Threshold  float64 `json:"t"`
	Left       int     `json:"l"`
	Right      int     `json:"r"`
	Value      float64 `json:"v"`
}

// Model combines a calibrated logistic scorer with additive GBDT trees.
type Model struct {
	Bias    float64     `json:"bias"`
	Weights [13]float64 `json:"weights"`
	Trees   [][]TreeNode `json:"trees"`
}

// DefaultModel returns a calibrated zero-harm behavioral spoof model.
// High thresholds and multi-feature corroboration prevent false positives on legit players.
func DefaultModel() *Model {
	return &Model{
		Bias: -3.25,
		Weights: [13]float64{
			0.95,  // 0: DiscreteMoveRatio
			-0.40, // 1: AnalogVariance
			0.45,  // 2: YawStepConsistency
			0.35,  // 3: PitchMicroRatio
			1.60,  // 4: AimSnapRatio
			-0.50, // 5: CrosshairOffTargetRatio
			1.45,  // 6: ZeroDelayUseRatio
			0.85,  // 7: SprintJumpSyncRatio
			0.55,  // 8: HeadBodyDesync
			1.10,  // 9: DeltaPosError
			0.15,  // 10: ClaimedTouch
			0.05,  // 11: ClaimedMouse
			2.10,  // 12: LoginSpoofPrior
		},
		Trees: [][]TreeNode{
			// Tree 1: Keyboard/Mouse spoofing Touch (discrete WASD + crosshair aim while claiming Touch)
			{
				{FeatureIdx: 10, Threshold: 0.5, Left: 1, Right: 2},   // ClaimedTouch > 0.5?
				{FeatureIdx: -1, Value: -0.35},                        // not touch
				{FeatureIdx: 0, Threshold: 0.88, Left: 3, Right: 4},   // DiscreteMoveRatio > 0.88?
				{FeatureIdx: -1, Value: -0.25},                        // analog-like touch
				{FeatureIdx: 1, Threshold: 0.008, Left: 5, Right: 6},  // AnalogVariance < 0.008?
				{FeatureIdx: -1, Value: 2.45},                         // strong WASD-on-Touch signature
				{FeatureIdx: -1, Value: 0.15},
			},
			// Tree 2: Touch/Gamepad spoofing Mouse (continuous non-45 analog movement while claiming Mouse)
			{
				{FeatureIdx: 11, Threshold: 0.5, Left: 1, Right: 2},   // ClaimedMouse > 0.5?
				{FeatureIdx: -1, Value: -0.20},                        // not mouse
				{FeatureIdx: 1, Threshold: 0.025, Left: 3, Right: 4},  // AnalogVariance > 0.025?
				{FeatureIdx: -1, Value: -0.25},
				{FeatureIdx: 0, Threshold: 0.35, Left: 5, Right: 6},   // DiscreteMoveRatio < 0.35?
				{FeatureIdx: -1, Value: 2.35},                         // strong Analog-on-Mouse signature
				{FeatureIdx: -1, Value: 0.10},
			},
			// Tree 3: Combat/Macro behavioral anomaly (zero-delay item use + aim snap + login prior)
			{
				{FeatureIdx: 6, Threshold: 0.78, Left: 1, Right: 2},   // ZeroDelayUseRatio > 0.78?
				{FeatureIdx: -1, Value: -0.20},
				{FeatureIdx: 4, Threshold: 0.18, Left: 3, Right: 4},   // AimSnapRatio > 0.18?
				{FeatureIdx: -1, Value: 0.45},
				{FeatureIdx: -1, Value: 1.55},
			},
		},
	}
}

// LoadModel loads optional custom weights from JSON, falling back to DefaultModel.
func LoadModel(path string) (*Model, error) {
	if path == "" {
		return DefaultModel(), nil
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return DefaultModel(), err
	}
	var m Model
	if err := json.Unmarshal(raw, &m); err != nil {
		return DefaultModel(), err
	}
	return &m, nil
}

func (f Features) asArray() [13]float64 {
	return [13]float64{
		f.DiscreteMoveRatio,
		f.AnalogVariance,
		f.YawStepConsistency,
		f.PitchMicroRatio,
		f.AimSnapRatio,
		f.CrosshairOffTargetRatio,
		f.ZeroDelayUseRatio,
		f.SprintJumpSyncRatio,
		f.HeadBodyDesync,
		f.DeltaPosError,
		f.ClaimedTouch,
		f.ClaimedMouse,
		f.LoginSpoofPrior,
	}
}

// Predict returns calibrated spoof probability in [0, 1] and the primary behavioral driver.
func (m *Model) Predict(f Features) (float64, string) {
	if m == nil {
		m = DefaultModel()
	}
	x := f.asArray()
	logit := m.Bias
	for i := range x {
		logit += m.Weights[i] * x[i]
	}
	for _, tree := range m.Trees {
		logit += evalTree(tree, x)
	}
	prob := 1.0 / (1.0 + math.Exp(-logit))
	return prob, dominantReason(f)
}

func evalTree(nodes []TreeNode, x [13]float64) float64 {
	if len(nodes) == 0 {
		return 0
	}
	idx := 0
	for steps := 0; steps < len(nodes); steps++ {
		n := nodes[idx]
		if n.FeatureIdx < 0 || n.FeatureIdx >= len(x) {
			return n.Value
		}
		if x[n.FeatureIdx] <= n.Threshold {
			idx = n.Left
		} else {
			idx = n.Right
		}
		if idx < 0 || idx >= len(nodes) {
			return n.Value
		}
	}
	return 0
}

func dominantReason(f Features) string {
	switch {
	case f.ClaimedTouch > 0.5 && f.DiscreteMoveRatio > 0.85 && f.AnalogVariance < 0.01:
		return "wasd_on_touch"
	case f.ClaimedMouse > 0.5 && f.AnalogVariance > 0.025 && f.DiscreteMoveRatio < 0.35:
		return "analog_on_mouse"
	case f.LoginSpoofPrior >= 0.5 && f.AimSnapRatio > 0.15:
		return "login_prior+aim_snap"
	case f.ZeroDelayUseRatio > 0.78:
		return "zero_delay_use"
	case f.AimSnapRatio > 0.25:
		return "aim_snap"
	case f.DeltaPosError > 0.2:
		return "delta_desync"
	default:
		return "behavioral_anomaly"
	}
}
