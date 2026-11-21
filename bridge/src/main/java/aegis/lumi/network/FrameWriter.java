package aegis.lumi.network;

import java.nio.charset.StandardCharsets;

public final class FrameWriter {

    public static final int VERSION = 3;

    private FrameWriter() {}

    public static byte[] hello(int shieldId, String server) {
        byte[] serverBytes = toBytes(server);
        int serverLen = Math.min(serverBytes.length, 0xFFFF);
        int bodyLen = 2 + 4 + 2 + serverLen;
        byte[] out = allocFrame(FrameType.HELLO, bodyLen);
        int p = 5;
        p = putShortLE(out, p, VERSION);
        p = putIntLE(out, p, shieldId);
        putString(out, p, serverBytes, serverLen);
        return out;
    }

    public static byte[] join(long session, String name, String xuid, int protocol, String version, int deviceOS, int inputMode,
                              int defaultInputMode, int uiProfile, int guiScale, String deviceModel, String deviceId,
                              long clientRandomId, String titleId, String languageCode, String serverAddress,
                              boolean xboxAuthed, String platformOnlineId, String platformOfflineId, String playFabId, int authType) {
        byte[] nameBytes = toBytes(name);
        byte[] xuidBytes = toBytes(xuid);
        byte[] versionBytes = toBytes(version);
        byte[] modelBytes = toBytes(deviceModel);
        byte[] deviceIdBytes = toBytes(deviceId);
        byte[] titleIdBytes = toBytes(titleId);
        byte[] langBytes = toBytes(languageCode);
        byte[] addrBytes = toBytes(serverAddress);
        byte[] pOnlineBytes = toBytes(platformOnlineId);
        byte[] pOfflineBytes = toBytes(platformOfflineId);
        byte[] playFabBytes = toBytes(playFabId);

        int nameLen = Math.min(nameBytes.length, 0xFFFF);
        int xuidLen = Math.min(xuidBytes.length, 0xFFFF);
        int verLen = Math.min(versionBytes.length, 0xFFFF);
        int modelLen = Math.min(modelBytes.length, 0xFFFF);
        int devIdLen = Math.min(deviceIdBytes.length, 0xFFFF);
        int titleLen = Math.min(titleIdBytes.length, 0xFFFF);
        int langLen = Math.min(langBytes.length, 0xFFFF);
        int addrLen = Math.min(addrBytes.length, 0xFFFF);
        int pOnLen = Math.min(pOnlineBytes.length, 0xFFFF);
        int pOffLen = Math.min(pOfflineBytes.length, 0xFFFF);
        int fabLen = Math.min(playFabBytes.length, 0xFFFF);

        int bodyLength = 8 + (2 + nameLen) + (2 + xuidLen) + 4 + (2 + verLen) + 4 + 4
                + 4 + 4 + 4
                + (2 + modelLen) + (2 + devIdLen)
                + 8
                + (2 + titleLen) + (2 + langLen) + (2 + addrLen)
                + 1
                + (2 + pOnLen) + (2 + pOffLen) + (2 + fabLen)
                + 4;

        byte[] out = allocFrame(FrameType.JOIN, bodyLength);
        int p = 5;
        p = putLongLE(out, p, session);
        p = putString(out, p, nameBytes, nameLen);
        p = putString(out, p, xuidBytes, xuidLen);
        p = putIntLE(out, p, protocol);
        p = putString(out, p, versionBytes, verLen);
        p = putIntLE(out, p, deviceOS);
        p = putIntLE(out, p, inputMode);
        p = putIntLE(out, p, defaultInputMode);
        p = putIntLE(out, p, uiProfile);
        p = putIntLE(out, p, guiScale);
        p = putString(out, p, modelBytes, modelLen);
        p = putString(out, p, deviceIdBytes, devIdLen);
        p = putLongLE(out, p, clientRandomId);
        p = putString(out, p, titleIdBytes, titleLen);
        p = putString(out, p, langBytes, langLen);
        p = putString(out, p, addrBytes, addrLen);
        out[p++] = (byte) (xboxAuthed ? 1 : 0);
        p = putString(out, p, pOnlineBytes, pOnLen);
        p = putString(out, p, pOfflineBytes, pOffLen);
        p = putString(out, p, playFabBytes, fabLen);
        putIntLE(out, p, authType);
        return out;
    }

    public static byte[] quit(long session) {
        byte[] out = allocFrame(FrameType.QUIT, 8);
        putLongLE(out, 5, session);
        return out;
    }

    public static byte[] input(long session, long time, long tick,
                              float x, float y, float z,
                              float pitch, float yaw, float headYaw,
                              int mode, boolean missed, int control,
                              int controlExt,
                              float dx, float dy, float dz,
                              float analogX, float analogY,
                              float rawX, float rawY,
                              float interactX, float interactY,
                              float camX, float camY, float camZ,
                              int playMode, int interactionModel) {
        byte[] out = allocFrame(FrameType.INPUT, 112);
        int p = 5;
        p = putLongLE(out, p, session);
        p = putLongLE(out, p, time);
        p = putLongLE(out, p, tick);
        p = putFloatLE(out, p, x);
        p = putFloatLE(out, p, y);
        p = putFloatLE(out, p, z);
        p = putFloatLE(out, p, pitch);
        p = putFloatLE(out, p, yaw);
        p = putFloatLE(out, p, headYaw);
        p = putIntLE(out, p, mode);
        out[p++] = (byte) (missed ? 1 : 0);
        out[p++] = (byte) (control & 0xFF);
        p = putShortLE(out, p, controlExt & 0xFFFF);
        p = putFloatLE(out, p, dx);
        p = putFloatLE(out, p, dy);
        p = putFloatLE(out, p, dz);
        p = putFloatLE(out, p, analogX);
        p = putFloatLE(out, p, analogY);
        p = putFloatLE(out, p, rawX);
        p = putFloatLE(out, p, rawY);
        p = putFloatLE(out, p, interactX);
        p = putFloatLE(out, p, interactY);
        p = putFloatLE(out, p, camX);
        p = putFloatLE(out, p, camY);
        p = putFloatLE(out, p, camZ);
        p = putIntLE(out, p, playMode);
        putIntLE(out, p, interactionModel);
        return out;
    }

    public static byte[] useItem(long session, long time, long tick, int actionType, int slot, int itemId, int flags) {
        byte[] out = allocFrame(FrameType.USE_ITEM, 34);
        int p = 5;
        p = putLongLE(out, p, session);
        p = putLongLE(out, p, time);
        p = putLongLE(out, p, tick);
        out[p++] = (byte) (actionType & 0xFF);
        p = putIntLE(out, p, slot);
        p = putIntLE(out, p, itemId);
        out[p] = (byte) (flags & 0xFF);
        return out;
    }

    public static byte[] slotChange(long session, long time, long tick, int fromSlot, int toSlot) {
        byte[] out = allocFrame(FrameType.SLOT_CHANGE, 32);
        int p = 5;
        p = putLongLE(out, p, session);
        p = putLongLE(out, p, time);
        p = putLongLE(out, p, tick);
        p = putIntLE(out, p, fromSlot);
        putIntLE(out, p, toSlot);
        return out;
    }

    public static byte[] inventoryAction(long session, long time, long tick, int windowId, int sourceSlot, int destSlot, int kind) {
        byte[] out = allocFrame(FrameType.INVENTORY_ACTION, 37);
        int p = 5;
        p = putLongLE(out, p, session);
        p = putLongLE(out, p, time);
        p = putLongLE(out, p, tick);
        p = putIntLE(out, p, windowId);
        p = putIntLE(out, p, sourceSlot);
        p = putIntLE(out, p, destSlot);
        out[p] = (byte) (kind & 0xFF);
        return out;
    }

    public static byte[] attack(long session, long time, long target) {
        byte[] out = allocFrame(FrameType.ATTACK, 24);
        int p = 5;
        p = putLongLE(out, p, session);
        p = putLongLE(out, p, time);
        putLongLE(out, p, target);
        return out;
    }

    public static byte[] swing(long session, long time) {
        byte[] out = allocFrame(FrameType.SWING, 16);
        int p = 5;
        p = putLongLE(out, p, session);
        putLongLE(out, p, time);
        return out;
    }

    public static byte[] animation(long session, long time) {
        byte[] out = allocFrame(FrameType.ANIMATION, 16);
        int p = 5;
        p = putLongLE(out, p, session);
        putLongLE(out, p, time);
        return out;
    }

    public static byte[] observation(long session, long time, long target, int kind, float[] box) {
        if (box != null && box.length >= 6) {
            return observation(session, time, target, kind, box[0], box[1], box[2], box[3], box[4], box[5]);
        }
        return observation(session, time, target, kind, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    public static byte[] observation(long session, long time, long target, int kind,
                                     float b0, float b1, float b2, float b3, float b4, float b5) {
        byte[] out = allocFrame(FrameType.OBSERVATION, 49);
        int p = 5;
        p = putLongLE(out, p, session);
        p = putLongLE(out, p, time);
        p = putLongLE(out, p, target);
        out[p++] = (byte) (kind & 0xFF);
        p = putFloatLE(out, p, b0);
        p = putFloatLE(out, p, b1);
        p = putFloatLE(out, p, b2);
        p = putFloatLE(out, p, b3);
        p = putFloatLE(out, p, b4);
        putFloatLE(out, p, b5);
        return out;
    }

    public static byte[] state(long session, long time, int flags,
                               float speed, float jump, float offset,
                               float x, float y, float z) {
        byte[] out = allocFrame(FrameType.STATE, 44);
        int p = 5;
        p = putLongLE(out, p, session);
        p = putLongLE(out, p, time);
        p = putIntLE(out, p, flags);
        p = putFloatLE(out, p, speed);
        p = putFloatLE(out, p, jump);
        p = putFloatLE(out, p, offset);
        p = putFloatLE(out, p, x);
        p = putFloatLE(out, p, y);
        putFloatLE(out, p, z);
        return out;
    }

    private static byte[] allocFrame(byte type, int bodyLen) {
        int frameLen = bodyLen + 1;
        byte[] out = new byte[5 + bodyLen];
        out[0] = (byte) (frameLen & 0xFF);
        out[1] = (byte) ((frameLen >> 8) & 0xFF);
        out[2] = (byte) ((frameLen >> 16) & 0xFF);
        out[3] = (byte) ((frameLen >> 24) & 0xFF);
        out[4] = type;
        return out;
    }

    private static byte[] toBytes(String s) {
        return s != null && !s.isEmpty() ? s.getBytes(StandardCharsets.UTF_8) : EMPTY_BYTES;
    }

    private static final byte[] EMPTY_BYTES = new byte[0];

    private static int putString(byte[] out, int p, byte[] bytes, int len) {
        p = putShortLE(out, p, len);
        if (len > 0) {
            System.arraycopy(bytes, 0, out, p, len);
            p += len;
        }
        return p;
    }

    private static int putShortLE(byte[] out, int p, int v) {
        out[p] = (byte) (v & 0xFF);
        out[p + 1] = (byte) ((v >>> 8) & 0xFF);
        return p + 2;
    }

    private static int putIntLE(byte[] out, int p, int v) {
        out[p] = (byte) (v & 0xFF);
        out[p + 1] = (byte) ((v >>> 8) & 0xFF);
        out[p + 2] = (byte) ((v >>> 16) & 0xFF);
        out[p + 3] = (byte) ((v >>> 24) & 0xFF);
        return p + 4;
    }

    private static int putLongLE(byte[] out, int p, long v) {
        out[p] = (byte) (v & 0xFF);
        out[p + 1] = (byte) ((v >>> 8) & 0xFF);
        out[p + 2] = (byte) ((v >>> 16) & 0xFF);
        out[p + 3] = (byte) ((v >>> 24) & 0xFF);
        out[p + 4] = (byte) ((v >>> 32) & 0xFF);
        out[p + 5] = (byte) ((v >>> 40) & 0xFF);
        out[p + 6] = (byte) ((v >>> 48) & 0xFF);
        out[p + 7] = (byte) ((v >>> 56) & 0xFF);
        return p + 8;
    }

    private static int putFloatLE(byte[] out, int p, float f) {
        return putIntLE(out, p, Float.floatToRawIntBits(f));
    }
}
