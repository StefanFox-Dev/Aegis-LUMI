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

    public static final int EXT_USING_ITEM = 1;
    public static final int EXT_GLIDING = 2;
    public static final int EXT_SWIMMING = 4;
    public static final int EXT_SPIN_ATTACK = 8;
    public static final int EXT_UP = 16;
    public static final int EXT_DOWN = 32;
    public static final int EXT_LEFT = 64;
    public static final int EXT_RIGHT = 128;
}
