package aegis.lumi.event;

import aegis.lumi.model.Detection;
import cn.nukkit.Player;

public class AegisDetectionEvent extends AegisEvent {

    private final Detection detection;
    private final boolean punishment;

    public AegisDetectionEvent(Player player, Detection detection, boolean punishment) {
        super(player);
        this.detection = detection;
        this.punishment = punishment;
    }

    public Detection getDetection() {
        return detection;
    }

    public boolean isPunishment() {
        return punishment;
    }
}
