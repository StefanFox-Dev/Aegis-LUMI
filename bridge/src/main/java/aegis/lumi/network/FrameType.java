package aegis.lumi.network;

public final class FrameType {

    private FrameType() {}

    public static final byte HELLO = 0x01;
    public static final byte JOIN = 0x02;
    public static final byte QUIT = 0x03;
    public static final byte SERVERBOUND = 0x04;
    public static final byte CLIENTBOUND = 0x05;
    public static final byte TICK = 0x06;
    public static final byte OBSERVATION = 0x07;
    public static final byte STATE = 0x08;
    public static final byte INPUT = 0x09;
    public static final byte ATTACK = 0x0A;
    public static final byte SWING = 0x0B;
    public static final byte ANIMATION = 0x0C;

    public static final byte WELCOME = (byte) 0x81;
    public static final byte FLAG = (byte) 0x82;
    public static final byte PUNISH = (byte) 0x83;
}
