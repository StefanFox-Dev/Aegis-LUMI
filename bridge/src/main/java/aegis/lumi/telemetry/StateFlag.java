package aegis.lumi.telemetry;

public final class StateFlag {

    private StateFlag() {}

    public static final int FLYING = 1;
    public static final int SPECIAL = 2;
    public static final int GROUND = 4;
    public static final int LAGGING = 8;
    public static final int DEAD = 16;
    public static final int FROZEN = 32;
    public static final int CEILING = 64;
    public static final int PHYSICS = 128;
    public static final int OBSTRUCTED = 256;
}
