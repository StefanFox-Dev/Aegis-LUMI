package aegis.lumi.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerView {

    private final Map<Long, float[]> visible = new ConcurrentHashMap<>();
    private final Map<Object, byte[]> pending = new ConcurrentHashMap<>();

    public Map<Long, float[]> getVisible() {
        return visible;
    }

    public Map<Object, byte[]> getPending() {
        return pending;
    }

    public void clear() {
        visible.clear();
        pending.clear();
    }
}
