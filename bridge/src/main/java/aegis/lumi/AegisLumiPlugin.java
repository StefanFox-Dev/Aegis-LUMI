package aegis.lumi;

import aegis.lumi.command.AegisCommand;
import aegis.lumi.config.AegisConfig;
import aegis.lumi.event.bus.AegisEventBus;
import aegis.lumi.listener.PlayerSessionListener;
import aegis.lumi.model.Verdict;
import aegis.lumi.network.AegisConnection;
import aegis.lumi.network.FrameWriter;
import aegis.lumi.service.AlertService;
import aegis.lumi.service.DetectionService;
import aegis.lumi.service.PunishmentService;
import aegis.lumi.session.PlayerSession;
import aegis.lumi.session.SessionRegistry;
import aegis.lumi.telemetry.PacketObserver;
import aegis.lumi.telemetry.WorldSampler;
import cn.nukkit.Player;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.TaskHandler;

public final class AegisLumiPlugin extends PluginBase {

    private static AegisLumiPlugin instance;

    private AegisConfig aegisConfig;
    private SessionRegistry sessionRegistry;
    private AegisEventBus eventBus;
    private AlertService alertService;
    private PunishmentService punishmentService;
    private DetectionService detectionService;
    private AegisConnection connection;
    private WorldSampler worldSampler;
    private PlayerSessionListener sessionListener;
    private PacketObserver packetObserver;
    private TaskHandler tickTaskHandler;

    public static AegisLumiPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.aegisConfig = new AegisConfig();
        this.aegisConfig.load(getConfig());

        this.sessionRegistry = new SessionRegistry();
        this.eventBus = new AegisEventBus(getLogger());
        this.alertService = new AlertService(aegisConfig, getLogger(), sessionRegistry);
        this.punishmentService = new PunishmentService(aegisConfig);
        this.detectionService = new DetectionService(sessionRegistry, alertService, punishmentService, eventBus);

        this.connection = new AegisConnection(aegisConfig, getLogger(), this::onConnected);

        this.worldSampler = new WorldSampler(aegisConfig, connection, sessionRegistry);
        this.sessionListener = new PlayerSessionListener(aegisConfig, connection, sessionRegistry);
        this.packetObserver = new PacketObserver(aegisConfig, connection, sessionRegistry);

        // Register event listeners directly to Lumi EventBus
        getServer().getEventBus().subscribe(sessionListener);
        getServer().getEventBus().subscribe(packetObserver);

        // Register command
        getServer().getCommandMap().register("aegis", new AegisCommand(this, aegisConfig, connection, sessionRegistry));

        // Register any already online players
        for (Player player : getServer().getOnlinePlayers().values()) {
            sessionRegistry.register(player, aegisConfig.isAlertsEnabledByDefault());
        }

        // Connect to sidecar
        connection.start();

        // Main thread tick task (runs every tick)
        this.tickTaskHandler = getServer().getScheduler().scheduleRepeatingTask(this, this::onTick, 1);

        getLogger().info("§aAegis-LUMI anticheat bridge enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (sessionListener != null) {
            try {
                getServer().getEventBus().unsubscribe(sessionListener);
            } catch (Exception ignored) {}
        }

        if (packetObserver != null) {
            try {
                getServer().getEventBus().unsubscribe(packetObserver);
            } catch (Exception ignored) {}
        }

        if (tickTaskHandler != null) {
            tickTaskHandler.cancel();
            tickTaskHandler = null;
        }

        if (connection != null) {
            connection.close();
            connection = null;
        }

        if (sessionRegistry != null) {
            sessionRegistry.clear();
        }

        if (eventBus != null) {
            eventBus.clear();
        }

        getLogger().info("§cAegis-LUMI anticheat bridge disabled.");
    }

    private void onConnected() {
        // Resynchronize online players
        for (PlayerSession session : sessionRegistry.all()) {
            sessionListener.sendJoin(session);
        }
    }

    private void onTick() {
        long currentTick = getServer().getTick();
        long now = System.currentTimeMillis();

        // Tick connection liveness / reconnect
        connection.tick(currentTick);

        if (connection.isConnected()) {
            // Send tick synchronization frame
            connection.send(FrameWriter.tick(currentTick, now));

            // Sample world collisions and state
            worldSampler.tick(currentTick);
        }

        // Drain incoming anticheat verdicts on the main thread
        Verdict verdict;
        int maxVerdictsPerTick = 128;
        while ((verdict = connection.pollVerdict()) != null && --maxVerdictsPerTick >= 0) {
            detectionService.handleVerdict(verdict);
        }
    }

    public AegisConfig getAegisConfig() {
        return aegisConfig;
    }

    public SessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }

    public AegisEventBus getEventBus() {
        return eventBus;
    }

    public AegisConnection getConnection() {
        return connection;
    }

    public DetectionService getDetectionService() {
        return detectionService;
    }
}
