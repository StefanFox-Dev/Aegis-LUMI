package aegis.lumi.model;

public enum Action {
    NONE((byte) 0),
    KICK((byte) 1),
    BAN((byte) 2);

    private final byte id;

    Action(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }

    public static Action fromId(int id) {
        return switch (id) {
            case 1 -> KICK;
            case 2 -> BAN;
            default -> NONE;
        };
    }
}
