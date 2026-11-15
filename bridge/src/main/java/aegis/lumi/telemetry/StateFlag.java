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
    public static final int IN_LIQUID = 512;
    public static final int ON_LADDER = 1024;
    public static final int ON_ICE = 2048;
    public static final int USING_ITEM = 4096;
    public static final int GLIDING = 8192;
    public static final int RIDING = 16384;
    public static final int SPIN_ATTACK = 32768;
    public static final int INSIDE_SOLID = 65536;
}
