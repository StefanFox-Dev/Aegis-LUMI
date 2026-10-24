package aegis.lumi.network;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class FrameWriter {

    public static final int VERSION = 2;

    private FrameWriter() {}

    public static byte[] hello(int shieldId, String server) {
        byte[] serverBytes = toBytes(server);
        ByteBuffer buf = allocate(2 + 4 + 2 + serverBytes.length);
        buf.putShort((short) VERSION);
        buf.putInt(shieldId);
        putString(buf, serverBytes);
        return wrap(FrameType.HELLO, buf.array());
    }

    public static byte[] join(long session, String name, String xuid, int protocol, String version, int deviceOS, int inputMode) {
        byte[] nameBytes = toBytes(name);
        byte[] xuidBytes = toBytes(xuid);
        byte[] versionBytes = toBytes(version);

        int bodyLength = 8 + (2 + nameBytes.length) + (2 + xuidBytes.length) + 4 + (2 + versionBytes.length) + 4 + 4;
        ByteBuffer buf = allocate(bodyLength);

        buf.putLong(session);
        putString(buf, nameBytes);
        putString(buf, xuidBytes);
        buf.putInt(protocol);
        putString(buf, versionBytes);
        buf.putInt(deviceOS);
        buf.putInt(inputMode);

        return wrap(FrameType.JOIN, buf.array());
    }

    public static byte[] quit(long session) {
        ByteBuffer buf = allocate(8);
        buf.putLong(session);
        return wrap(FrameType.QUIT, buf.array());
    }

    public static byte[] tick(long tick, long time) {
        ByteBuffer buf = allocate(16);
        buf.putLong(tick);
        buf.putLong(time);
        return wrap(FrameType.TICK, buf.array());
    }

    public static byte[] input(long session, long time, long tick,
                              float x, float y, float z,
                              float pitch, float yaw, float headYaw,
                              int mode, boolean missed, int control) {
        ByteBuffer buf = allocate(8 + 8 + 8 + 4 * 6 + 4 + 1 + 1);
        buf.putLong(session);
        buf.putLong(time);
        buf.putLong(tick);
        buf.putFloat(x);
        buf.putFloat(y);
        buf.putFloat(z);
        buf.putFloat(pitch);
        buf.putFloat(yaw);
        buf.putFloat(headYaw);
        buf.putInt(mode);
        buf.put((byte) (missed ? 1 : 0));
        buf.put((byte) (control & 0xFF));
        return wrap(FrameType.INPUT, buf.array());
    }

    public static byte[] attack(long session, long time, long target) {
        ByteBuffer buf = allocate(24);
        buf.putLong(session);
        buf.putLong(time);
        buf.putLong(target);
        return wrap(FrameType.ATTACK, buf.array());
    }

    public static byte[] swing(long session, long time) {
        ByteBuffer buf = allocate(16);
        buf.putLong(session);
        buf.putLong(time);
        return wrap(FrameType.SWING, buf.array());
    }

    public static byte[] animation(long session, long time) {
        ByteBuffer buf = allocate(16);
        buf.putLong(session);
        buf.putLong(time);
        return wrap(FrameType.ANIMATION, buf.array());
    }

    public static byte[] observation(long session, long time, long target, int kind, float[] box) {
        ByteBuffer buf = allocate(8 + 8 + 8 + 1 + 4 * 6);
        buf.putLong(session);
        buf.putLong(time);
        buf.putLong(target);
        buf.put((byte) (kind & 0xFF));
        if (box != null && box.length >= 6) {
            for (int i = 0; i < 6; i++) {
                buf.putFloat(box[i]);
            }
        } else {
            for (int i = 0; i < 6; i++) {
                buf.putFloat(0.0f);
            }
        }
        return wrap(FrameType.OBSERVATION, buf.array());
    }

    public static byte[] state(long session, long time, int flags,
                              float speed, float jump, float offset,
                              float x, float y, float z) {
        ByteBuffer buf = allocate(8 + 8 + 4 + 4 * 6);
        buf.putLong(session);
        buf.putLong(time);
        buf.putInt(flags);
        buf.putFloat(speed);
        buf.putFloat(jump);
        buf.putFloat(offset);
        buf.putFloat(x);
        buf.putFloat(y);
        buf.putFloat(z);
        return wrap(FrameType.STATE, buf.array());
    }

    private static ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] toBytes(String s) {
        return s != null ? s.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    private static void putString(ByteBuffer buf, byte[] bytes) {
        int length = Math.min(bytes.length, 0xFFFF);
        buf.putShort((short) length);
        buf.put(bytes, 0, length);
    }

    private static byte[] wrap(byte type, byte[] body) {
        int frameLen = body.length + 1;
        byte[] frame = new byte[4 + 1 + body.length];
        frame[0] = (byte) (frameLen & 0xFF);
        frame[1] = (byte) ((frameLen >> 8) & 0xFF);
        frame[2] = (byte) ((frameLen >> 16) & 0xFF);
        frame[3] = (byte) ((frameLen >> 24) & 0xFF);
        frame[4] = type;
        System.arraycopy(body, 0, frame, 5, body.length);
        return frame;
    }
}
