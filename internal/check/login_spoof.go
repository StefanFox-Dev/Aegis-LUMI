package check

import (
	"fmt"
	"regexp"
	"strings"

	"github.com/EraseMC/Aegis/internal/config"
	"github.com/EraseMC/Aegis/internal/player"
	"github.com/sandertv/gophertunnel/minecraft/protocol/packet"
)

const LoginSpoofA = "LoginSpoof_A"

// Official Bedrock TitleIDs mapped to expected DeviceOS values.
var titleIDToOS = map[string]int32{
	"1739947436": 1,  // Android
	"1810924247": 2,  // iOS
	"896928775":  7,  // Windows 10/11
	"2047319603": 11, // PlayStation
	"2044456598": 12, // Nintendo Switch
	"1828326430": 13, // Xbox
}

var hex32Regex = regexp.MustCompile(`^[0-9a-fA-F]{32}$`)
var uuidRegex = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

type loginSpoof struct {
	violations
	evaluated bool
	pending   string
	score     float64
}

func newLoginSpoof(s config.Check) *loginSpoof {
	return &loginSpoof{violations: newViolations(LoginSpoofA, s)}
}

func (l *loginSpoof) Name() string { return LoginSpoofA }

func (l *loginSpoof) OnJoin(p *player.Player) {
	j := p.JoinInfo
	if !j.HasExtended {
		return
	}

	var reasons []string
	score := 0.0

	// 1. Signed Xbox TitleID vs claimed DeviceOS (strongest indicator of Horion/Toolbox/Proxy OS spoofer)
	if j.XboxAuthed && j.TitleId != "" {
		if expectedOS, known := titleIDToOS[j.TitleId]; known && expectedOS != j.DeviceOS {
			reasons = append(reasons, fmt.Sprintf("title_os_mismatch(title=%s,expected=%d,got=%d)", j.TitleId, expectedOS, j.DeviceOS))
			score += 0.75
		}
	}

	// 2. DeviceModel vs DeviceOS consistency
	modelLower := strings.ToLower(strings.TrimSpace(j.DeviceModel))
	switch j.DeviceOS {
	case 1: // Android
		if modelLower == "" {
			reasons = append(reasons, "empty_android_model")
			score += 0.35
		} else if strings.HasPrefix(modelLower, "iphone") || strings.HasPrefix(modelLower, "ipad") {
			reasons = append(reasons, "ios_model_on_android("+j.DeviceModel+")")
			score += 0.65
		}
		if j.ClientRandomId == 0 {
			reasons = append(reasons, "zero_client_random_id")
			score += 0.30
		}
	case 2: // iOS
		if modelLower != "" && !strings.HasPrefix(modelLower, "iphone") && !strings.HasPrefix(modelLower, "ipad") && !strings.HasPrefix(modelLower, "ipod") && !strings.HasPrefix(modelLower, "apple") {
			reasons = append(reasons, "non_ios_model("+j.DeviceModel+")")
			score += 0.60
		}
		if j.ClientRandomId == 0 {
			reasons = append(reasons, "zero_client_random_id")
			score += 0.30
		}
	case 7: // Windows 10/11
		if strings.HasPrefix(modelLower, "iphone") || strings.HasPrefix(modelLower, "ipad") || strings.HasPrefix(modelLower, "sm-") || strings.HasPrefix(modelLower, "redmi") || strings.HasPrefix(modelLower, "poco") {
			reasons = append(reasons, "mobile_model_on_win("+j.DeviceModel+")")
			score += 0.65
		}
	}

	// 3. DeviceId format sanity check
	devID := strings.TrimSpace(j.DeviceId)
	if devID == "" || devID == "00000000-0000-0000-0000-000000000000" {
		reasons = append(reasons, "empty_or_zero_device_id")
		score += 0.40
	} else if j.DeviceOS == 1 && !hex32Regex.MatchString(devID) && !uuidRegex.MatchString(devID) {
		reasons = append(reasons, "malformed_android_device_id")
		score += 0.30
	}

	// 4. UIProfile / DefaultInputMode cross-check (soft signal only)
	if j.DeviceOS == 7 && j.UIProfile == 1 && j.DefaultInputMode == 2 {
		reasons = append(reasons, "win_pocket_touch_default")
		score += 0.20
	}
	if (j.DeviceOS == 1 || j.DeviceOS == 2) && j.DefaultInputMode == 1 && j.InputMode == 2 {
		reasons = append(reasons, "mobile_mouse_default_touch_current")
		score += 0.25
	}

	if score > 1.0 {
		score = 1.0
	}
	p.LoginSpoofScore = score
	l.score = score
	if len(reasons) > 0 && score >= 0.45 {
		l.pending = strings.Join(reasons, "+")
	}
}

func (l *loginSpoof) OnAction(_ *player.Player, _ any, _ uint64) {}

func (l *loginSpoof) Serverbound(p *player.Player, pk packet.Packet, _ uint64) {
	if l.evaluated || l.pending == "" {
		return
	}
	if _, ok := pk.(*packet.PlayerAuthInput); !ok {
		return
	}
	l.evaluated = true
	l.fail(p, l.score*2.0, fmt.Sprintf("score=%.2f %s", l.score, l.pending))
}
