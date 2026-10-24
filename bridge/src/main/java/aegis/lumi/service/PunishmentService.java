package aegis.lumi.service;

import aegis.lumi.config.AegisConfig;
import aegis.lumi.model.Action;
import aegis.lumi.model.Detection;
import cn.nukkit.Player;
import cn.nukkit.Server;

import java.util.Date;

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

        if (action == Action.KICK) {
            player.kick("§8[§cAegis§8] §cUnfair Advantage: " + detection.name());
        } else if (action == Action.BAN) {
            long duration = config.getBanDurationSeconds();
            Date expiry = duration > 0 ? new Date(System.currentTimeMillis() + duration * 1000L) : null;
            Server.getInstance().getNameBans().addBan(player.getName(), "Aegis: " + detection.name(), expiry, "Aegis");
            player.kick("§8[§cAegis§8] §cBanned: " + detection.name());
        }
    }
}
