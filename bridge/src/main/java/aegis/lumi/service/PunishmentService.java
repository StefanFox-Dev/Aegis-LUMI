package aegis.lumi.service;

import aegis.lumi.config.AegisConfig;
import aegis.lumi.model.Action;
import aegis.lumi.model.Detection;
import cn.nukkit.Player;

public final class PunishmentService {

    private final AegisConfig config;

    public PunishmentService(AegisConfig config) {
        this.config = config;
    }

    public void execute(Player player, Detection detection, Action action) {
        if (player == null || detection == null || action == null || action == Action.NONE) {
            return;
        }

        if (!config.isPunishmentsEnabled()) {
            return;
        }

        if (action == Action.KICK || action == Action.BAN) {
            player.kick("§8[§cAegis§8] §cUnfair Advantage: " + detection.name());
        }
    }
}
