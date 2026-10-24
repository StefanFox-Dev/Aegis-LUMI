package aegis.lumi.network;

import aegis.lumi.config.AegisConfig;
import aegis.lumi.model.Verdict;
import cn.nukkit.item.ItemID;
import cn.nukkit.utils.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AegisConnection {

    public enum State {
        DISCONNECTED,
        CONNECTING,
        HANDSHAKING,
        CONNECTED
    }

    private final AegisConfig config;
    private final Logger logger;
    private final Runnable onConnectedCallback;

    private volatile State state = State.DISCONNECTED;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private Socket socket;
    private BufferedInputStream in;
    private BufferedOutputStream out;

    private Thread readerThread;
    private Thread writerThread;

    private final BlockingQueue<byte[]> sendQueue = new ArrayBlockingQueue<>(4096);
    private final ConcurrentLinkedQueue<Verdict> inboundVerdicts = new ConcurrentLinkedQueue<>();

    private long nextRetryTick = 0;
    private long lastWarningTime = 0;

    public AegisConnection(AegisConfig config, Logger logger, Runnable onConnectedCallback) {
        this.config = config;
        this.logger = logger;
        this.onConnectedCallback = onConnectedCallback;
    }

    public State getState() {
        return state;
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public void start() {
        scheduleReconnect(0);
    }

    public void tick(long currentTick) {
        if (state == State.DISCONNECTED && currentTick >= nextRetryTick) {
            scheduleReconnect(currentTick + config.getRetryIntervalTicks());
        }
    }

    public Verdict pollVerdict() {
        return inboundVerdicts.poll();
    }

    public boolean send(byte[] frame) {
        if (state != State.CONNECTED && state != State.HANDSHAKING) {
            return false;
        }

        if (!sendQueue.offer(frame)) {
            long now = System.currentTimeMillis();
            if (now - lastWarningTime > 5000L) {
                lastWarningTime = now;
                logger.warning("[Aegis-LUMI] Send queue is full, dropping telemetry frame.");
            }
            return false;
        }

        return true;
    }

    public synchronized void scheduleReconnect(long nextTick) {
        this.nextRetryTick = nextTick;
        if (!running.get() || reconnecting.getAndSet(true)) {
            return;
        }

        Thread connectThread = new Thread(this::attemptConnection, "Aegis-ConnectWorker");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void attemptConnection() {
        try {
            cleanupSocket();
            this.state = State.CONNECTING;

            Socket sock = new Socket();
            sock.setTcpNoDelay(true);
            sock.setSoTimeout(0);
            sock.connect(new InetSocketAddress(config.getHost(), config.getPort()), 4000);

            this.socket = sock;
            this.in = new BufferedInputStream(sock.getInputStream(), 16384);
            this.out = new BufferedOutputStream(sock.getOutputStream(), 16384);

            this.state = State.HANDSHAKING;

            // Start I/O threads
            this.readerThread = new Thread(this::readLoop, "Aegis-ReaderThread");
            this.readerThread.setDaemon(true);
            this.readerThread.start();

            this.writerThread = new Thread(this::writeLoop, "Aegis-WriterThread");
            this.writerThread.setDaemon(true);
            this.writerThread.start();

            // Send Hello
            byte[] helloFrame = FrameWriter.hello(ItemID.SHIELD, "Lumi-Server");
            sendQueue.offer(helloFrame);

        } catch (Exception e) {
            logger.debug("[Aegis-LUMI] Connection attempt failed: " + e.getMessage());
            disconnect();
        } finally {
            reconnecting.set(false);
        }
    }

    private void readLoop() {
        byte[] headerBuf = new byte[5];
        ByteBuffer headWrap = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN);

        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                readFully(headerBuf, 0, 5);

                headWrap.position(0);
                long frameLen = headWrap.getInt() & 0xFFFFFFFFL;
                byte frameType = headerBuf[4];

                if (frameLen < 1 || frameLen > 10_000_000) {
                    throw new IOException("Invalid frame length: " + frameLen);
                }

                int bodyLen = (int) (frameLen - 1);
                byte[] body = new byte[bodyLen];
                if (bodyLen > 0) {
                    readFully(body, 0, bodyLen);
                }

                handleIncomingFrame(frameType, body);
            }
        } catch (EOFException ignored) {
            // Socket closed cleanly or remotely
        } catch (Exception e) {
            if (running.get()) {
                logger.debug("[Aegis-LUMI] Reader error: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private void readFully(byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int count = in.read(b, off + n, len - n);
            if (count < 0) {
                throw new EOFException("End of stream reached");
            }
            n += count;
        }
    }

    private void writeLoop() {
        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                byte[] frame = sendQueue.take();
                out.write(frame);

                // Drain and batch flush any remaining queued items
                while (!sendQueue.isEmpty()) {
                    byte[] extra = sendQueue.poll();
                    if (extra != null) {
                        out.write(extra);
                    }
                }
                out.flush();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running.get()) {
                logger.debug("[Aegis-LUMI] Writer error: " + e.getMessage());
            }
            disconnect();
        }
    }

    private void handleIncomingFrame(byte type, byte[] body) {
        switch (type) {
            case FrameType.WELCOME -> {
                int version = FrameReader.readWelcome(body);
                if (version == FrameWriter.VERSION) {
                    this.state = State.CONNECTED;
                    logger.info("§aConnected to Aegis sidecar daemon at " + config.getHost() + ":" + config.getPort() + " (wire v" + version + ")");
                    if (onConnectedCallback != null) {
                        onConnectedCallback.run();
                    }
                } else {
                    logger.error("[Aegis-LUMI] Incompatible sidecar wire version: " + version + " (expected " + FrameWriter.VERSION + ")");
                    disconnect();
                }
            }
            case FrameType.FLAG -> {
                Verdict flag = FrameReader.readFlag(body);
                if (flag != null) {
                    inboundVerdicts.offer(flag);
                }
            }
            case FrameType.PUNISH -> {
                Verdict punish = FrameReader.readPunish(body);
                if (punish != null) {
                    inboundVerdicts.offer(punish);
                }
            }
            default -> logger.debug("[Aegis-LUMI] Received unknown frame type: " + (type & 0xFF));
        }
    }

    public synchronized void disconnect() {
        if (state == State.DISCONNECTED) {
            return;
        }
        state = State.DISCONNECTED;
        cleanupSocket();
    }

    private synchronized void cleanupSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {}

        socket = null;
        in = null;
        out = null;

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }

        if (writerThread != null) {
            writerThread.interrupt();
            writerThread = null;
        }

        sendQueue.clear();
    }

    public void close() {
        running.set(false);
        disconnect();
        inboundVerdicts.clear();
    }
}
