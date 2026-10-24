package aegis.lumi.event;

import aegis.lumi.model.Action;
import aegis.lumi.model.Detection;
import cn.nukkit.Player;

public class AegisPunishEvent extends AegisEvent {

    private final Detection detection;
    private Action action;

    public AegisPunishEvent(Player player, Detection detection, Action action) {
        super(player);
        this.detection = detection;
        this.action = action;
    }

    public Detection getDetection() {
        return detection;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }
}
