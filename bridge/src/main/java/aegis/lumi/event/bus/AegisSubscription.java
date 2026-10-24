package aegis.lumi.event.bus;

import java.util.function.Consumer;

public record AegisSubscription<T>(
    Class<T> eventClass,
    int priority,
    Consumer<T> consumer
) implements Comparable<AegisSubscription<?>> {

    @Override
    public int compareTo(AegisSubscription<?> o) {
        return Integer.compare(o.priority, this.priority); // descending priority
    }
}
