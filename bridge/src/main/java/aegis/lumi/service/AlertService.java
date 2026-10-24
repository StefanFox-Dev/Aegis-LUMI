package aegis.lumi.service;

import aegis.lumi.config.AegisConfig;
import aegis.lumi.model.Action;
import aegis.lumi.model.Detection;
import aegis.lumi.session.PlayerSession;
import aegis.lumi.session.SessionRegistry;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.utils.Logger;

public final class AlertService {

    private final AegisConfig config;
    private final Logger logger;
    private final SessionRegistry sessions;

    public AlertService(AegisConfig config, Logger logger, SessionRegistry sessions) {
        this.config = config;
        this.logger = logger;
        this.sessions = sessions;
    }

    public void handleAlert(Player target, Detection detection) {
        if (target == null || detection == null) {
            return;
        }

        PlayerSession targetSession = sessions.get(target.getId());
        long now = System.currentTimeMillis();

        if (targetSession != null) {
            long last = targetSession.getLastAlertTime(detection.name());
            if (now - last < config.getAlertCooldownMs()) {
                return;
            }
            targetSession.setLastAlertTime(detection.name(), now);
        }

        String dataSuffix = (detection.data() != null && !detection.data().isEmpty())
                ? " §8| §7" + detection.data()
                : "";

        String message = String.format(
                "§8[§cAegis§8] §c%s §7failed §f%s §8(x%.1f%s§8)",
                target.getName(),
                detection.name(),
                detection.violations(),
                dataSuffix
        );

        broadcast(message);

        if (config.isLogAlerts()) {
            logger.info(message);
        }
    }

    public void handlePunishmentAlert(Player target, Detection detection, Action action) {
        if (target == null || detection == null || action == null || action == Action.NONE) {
            return;
        }

        String actionVerb = action == Action.BAN ? "banned" : "kicked";
        String message = String.format(
                "§8[§cAegis§8] §c%s §7was §4%s §7for §f%s §8(x%.1f)",
                target.getName(),
                actionVerb,
                detection.name(),
                detection.violations()
        );

        broadcast(message);

        if (config.isLogAlerts()) {
            logger.info(message);
        }
    }

    private void broadcast(String message) {
        for (PlayerSession session : sessions.all()) {
            Player p = session.getPlayer();
            if (p != null && p.isOnline() && session.isAlertsEnabled() && p.hasPermission("aegis.alerts")) {
                p.sendMessage(message);
            }
        }
    }
}
