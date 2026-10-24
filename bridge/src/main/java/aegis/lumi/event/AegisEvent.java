package aegis.lumi.event;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.Event;

public abstract class AegisEvent extends Event implements Cancellable {

    private final Player player;

    protected AegisEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }
}
