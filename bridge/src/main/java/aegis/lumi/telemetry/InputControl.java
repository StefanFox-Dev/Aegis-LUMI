package aegis.lumi.telemetry;

public final class InputControl {

    private InputControl() {}

    public static final int JUMP = 1;
    public static final int JUMP_START = 2;
    public static final int SPRINT = 4;
    public static final int HORIZONTAL_COLLISION = 8;
    public static final int VERTICAL_COLLISION = 16;
    public static final int JUMP_PRESSED = 32;
    public static final int SNEAK = 64;
    public static final int KNOWN = 128;
}
