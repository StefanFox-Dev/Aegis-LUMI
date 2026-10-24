package aegis.lumi.model;

public record Detection(
    String name,
    float violations,
    float maxThreshold,
    Action action,
    String data
) {
    public boolean exceedsThreshold() {
        return maxThreshold > 0 && violations >= maxThreshold;
    }
}
