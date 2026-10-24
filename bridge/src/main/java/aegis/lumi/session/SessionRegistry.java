package aegis.lumi.session;

import cn.nukkit.Player;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRegistry {

    private final Map<Long, PlayerSession> sessions = new ConcurrentHashMap<>();

    public PlayerSession register(Player player, boolean defaultAlerts) {
        PlayerSession session = new PlayerSession(player, defaultAlerts);
        sessions.put(player.getId(), session);
        return session;
    }

    public PlayerSession remove(long id) {
        PlayerSession removed = sessions.remove(id);
        if (removed != null) {
            removed.getView().clear();
        }
        return removed;
    }

    public PlayerSession get(long id) {
        return sessions.get(id);
    }

    public Collection<PlayerSession> all() {
        return sessions.values();
    }

    public int size() {
        return sessions.size();
    }

    public void clear() {
        for (PlayerSession session : sessions.values()) {
            session.getView().clear();
        }
        sessions.clear();
    }
}
