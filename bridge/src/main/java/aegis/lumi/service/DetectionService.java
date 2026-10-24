package aegis.lumi.service;

import aegis.lumi.event.AegisDetectionEvent;
import aegis.lumi.event.AegisFlagEvent;
import aegis.lumi.event.AegisPunishEvent;
import aegis.lumi.event.bus.AegisEventBus;
import aegis.lumi.model.Action;
import aegis.lumi.model.Detection;
import aegis.lumi.model.Verdict;
import aegis.lumi.session.PlayerSession;
import aegis.lumi.session.SessionRegistry;
import cn.nukkit.Player;

public final class DetectionService {

    private final SessionRegistry sessions;
    private final AlertService alertService;
    private final PunishmentService punishmentService;
    private final AegisEventBus eventBus;

    public DetectionService(SessionRegistry sessions,
                            AlertService alertService,
                            PunishmentService punishmentService,
                            AegisEventBus eventBus) {
        this.sessions = sessions;
        this.alertService = alertService;
        this.punishmentService = punishmentService;
        this.eventBus = eventBus;
    }

    public void handleVerdict(Verdict verdict) {
        if (verdict == null) {
            return;
        }

        PlayerSession session = sessions.get(verdict.session());
        if (session == null) {
            return;
        }

        Player player = session.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }

        if (player.hasPermission("aegis.bypass")) {
            return;
        }

        Detection detection = verdict.detection();
        if (detection == null) {
            return;
        }

        if (!verdict.isPunish()) {
            // Flag event
            AegisDetectionEvent detectionEvent = new AegisDetectionEvent(player, detection, false);
            if (!eventBus.post(detectionEvent)) {
                return;
            }

            AegisFlagEvent flagEvent = new AegisFlagEvent(player, detection);
            if (!eventBus.post(flagEvent)) {
                return;
            }

            alertService.handleAlert(player, detection);
        } else {
            // Punish event
            AegisDetectionEvent detectionEvent = new AegisDetectionEvent(player, detection, true);
            if (!eventBus.post(detectionEvent)) {
                return;
            }

            AegisPunishEvent punishEvent = new AegisPunishEvent(player, detection, detection.action());
            if (!eventBus.post(punishEvent)) {
                return;
            }

            Action resolvedAction = punishEvent.getAction();
            alertService.handlePunishmentAlert(player, detection, resolvedAction);
            punishmentService.execute(player, detection, resolvedAction);
        }
    }
}
