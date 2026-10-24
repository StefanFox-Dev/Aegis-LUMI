package aegis.lumi.telemetry;

public final class TickBudget {

    private final long maxNanos;
    private final int maxOperations;

    private long lastTick = -1;
    private long accumulatedNanos = 0;
    private int operations = 0;

    public TickBudget(long maxNanos, int maxOperations) {
        this.maxNanos = maxNanos;
        this.maxOperations = maxOperations;
    }

    public boolean canClaim(long currentTick) {
        if (currentTick != lastTick) {
            lastTick = currentTick;
            accumulatedNanos = 0;
            operations = 0;
        }

        if (maxOperations > 0 && ++operations > maxOperations) {
            return false;
        }

        return maxNanos <= 0 || accumulatedNanos <= maxNanos;
    }

    public void record(long durationNanos) {
        if (durationNanos > 0) {
            accumulatedNanos += durationNanos;
        }
    }

    public long getAccumulatedNanos() {
        return accumulatedNanos;
    }

    public int getOperations() {
        return operations;
    }
}
