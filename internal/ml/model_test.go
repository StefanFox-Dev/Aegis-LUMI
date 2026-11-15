package ml

import "testing"

func TestLegitPlayersStayLowProbability(t *testing.T) {
	m := DefaultModel()

	legitMouse := Features{
		DiscreteMoveRatio: 0.96,
		AnalogVariance:    0.0,
		PitchMicroRatio:   0.40,
		ClaimedMouse:      1.0,
	}
	if p, _ := m.Predict(legitMouse); p >= 0.5 {
		t.Fatalf("legit mouse player scored too high: %.3f", p)
	}

	legitTouch := Features{
		DiscreteMoveRatio:       0.20,
		AnalogVariance:          0.08,
		CrosshairOffTargetRatio: 0.45,
		ClaimedTouch:            1.0,
	}
	if p, _ := m.Predict(legitTouch); p >= 0.5 {
		t.Fatalf("legit touch player scored too high: %.3f", p)
	}
}

func TestSpoofedBehaviorScoresHigh(t *testing.T) {
	m := DefaultModel()

	wasdOnTouch := Features{
		DiscreteMoveRatio: 0.98,
		AnalogVariance:    0.001,
		YawStepConsistency: 0.80,
		ClaimedTouch:      1.0,
		LoginSpoofPrior:   0.8,
	}
	p, reason := m.Predict(wasdOnTouch)
	if p < 0.85 {
		t.Fatalf("expected high spoof prob for WASD-on-Touch, got %.3f (%s)", p, reason)
	}
}
