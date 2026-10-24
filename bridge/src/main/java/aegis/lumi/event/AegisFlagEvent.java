package aegis.lumi.event;

import aegis.lumi.model.Detection;
import cn.nukkit.Player;

public class AegisFlagEvent extends AegisEvent {

    private final Detection detection;

    public AegisFlagEvent(Player player, Detection detection) {
        super(player);
        this.detection = detection;
    }

    public Detection getDetection() {
        return detection;
    }
}
