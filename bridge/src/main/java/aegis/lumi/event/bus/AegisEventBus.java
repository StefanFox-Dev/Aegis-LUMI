package aegis.lumi.event.bus;

import aegis.lumi.event.AegisEvent;
import cn.nukkit.utils.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class AegisEventBus {

    private final Logger logger;
    private final Map<Class<?>, List<AegisSubscription<?>>> subscriptions = new ConcurrentHashMap<>();

    public AegisEventBus(Logger logger) {
        this.logger = logger;
    }

    public <T> void subscribe(Class<T> eventClass, Consumer<T> consumer) {
        subscribe(eventClass, 0, consumer);
    }

    public synchronized <T> void subscribe(Class<T> eventClass, int priority, Consumer<T> consumer) {
        List<AegisSubscription<?>> list = subscriptions.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>());
        list.add(new AegisSubscription<>(eventClass, priority, consumer));
        list.sort(null);
    }

    @SuppressWarnings("unchecked")
    public boolean post(AegisEvent event) {
        if (event == null) {
            return false;
        }

        // 1. Dispatch to Lumi Server EventBus first
        try {
            event.call();
        } catch (Exception e) {
            logger.error("[Aegis-LUMI] Error dispatching to Lumi EventBus: " + e.getMessage(), e);
        }

        if (event.isCancelled()) {
            return false;
        }

        // 2. Dispatch to internal Aegis lambda subscriptions
        Class<?> clazz = event.getClass();
        for (Map.Entry<Class<?>, List<AegisSubscription<?>>> entry : subscriptions.entrySet()) {
            if (entry.getKey().isAssignableFrom(clazz)) {
                for (AegisSubscription<?> sub : entry.getValue()) {
                    try {
                        ((Consumer<Object>) sub.consumer()).accept(event);
                    } catch (Exception e) {
                        logger.error("[Aegis-LUMI] Error in internal event listener: " + e.getMessage(), e);
                    }
                }
            }
        }

        return !event.isCancelled();
    }

    public void clear() {
        subscriptions.clear();
    }
}
