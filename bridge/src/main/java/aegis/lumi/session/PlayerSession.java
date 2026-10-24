package aegis.lumi.session;

import cn.nukkit.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSession {

    private final long id;
    private final Player player;
    private final PlayerView view = new PlayerView();
    private final Map<String, Long> lastAlertTimes = new ConcurrentHashMap<>();

    private volatile boolean alertsEnabled;

    public PlayerSession(Player player, boolean defaultAlerts) {
        this.id = player.getId();
        this.player = player;
        this.alertsEnabled = defaultAlerts;
    }

    public long getId() {
        return id;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerView getView() {
        return view;
    }

    public boolean isAlertsEnabled() {
        return alertsEnabled;
    }

    public void setAlertsEnabled(boolean alertsEnabled) {
        this.alertsEnabled = alertsEnabled;
    }

    public long getLastAlertTime(String check) {
        return lastAlertTimes.getOrDefault(check, 0L);
    }

    public void setLastAlertTime(String check, long time) {
        lastAlertTimes.put(check, time);
    }

    public int getPing() {
        return player.getPing();
    }
}
