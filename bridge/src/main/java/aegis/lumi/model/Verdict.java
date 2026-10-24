package aegis.lumi.model;

public record Verdict(
    long session,
    Detection detection,
    boolean isPunish
) {}
