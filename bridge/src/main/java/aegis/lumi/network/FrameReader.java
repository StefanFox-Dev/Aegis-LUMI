package aegis.lumi.network;

import aegis.lumi.model.Action;
import aegis.lumi.model.Detection;
import aegis.lumi.model.Verdict;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class FrameReader {

    private FrameReader() {}

    public static int readWelcome(byte[] body) {
        if (body.length < 2) {
            return -1;
        }
        ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        return buf.getShort() & 0xFFFF;
    }

    public static Verdict readFlag(byte[] body) {
        ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.remaining() < 8) {
            return null;
        }

        long session = buf.getLong();
        String check = readString(buf);
        if (buf.remaining() < 8) {
            return null;
        }
        float violations = buf.getFloat();
        float max = buf.getFloat();
        String data = readString(buf);

        Detection detection = new Detection(check, violations, max, Action.NONE, data);
        return new Verdict(session, detection, false);
    }

    public static Verdict readPunish(byte[] body) {
        ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.remaining() < 8) {
            return null;
        }

        long session = buf.getLong();
        String check = readString(buf);
        if (buf.remaining() < 5) {
            return null;
        }
        float violations = buf.getFloat();
        int actionId = buf.get() & 0xFF;
        String data = readString(buf);

        Detection detection = new Detection(check, violations, 0.0f, Action.fromId(actionId), data);
        return new Verdict(session, detection, true);
    }

    private static String readString(ByteBuffer buf) {
        if (buf.remaining() < 2) {
            return "";
        }
        int length = buf.getShort() & 0xFFFF;
        if (buf.remaining() < length) {
            return "";
        }
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
