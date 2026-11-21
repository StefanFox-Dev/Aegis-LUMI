package aegis.lumi.listener;

import aegis.lumi.config.AegisConfig;
import aegis.lumi.network.AegisConnection;
import aegis.lumi.network.FrameWriter;
import aegis.lumi.session.PlayerSession;
import aegis.lumi.session.SessionRegistry;
import aegis.lumi.telemetry.PacketObserver;
import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.utils.LoginChainData;

public final class PlayerSessionListener implements Listener {

    private final AegisConfig config;
    private final AegisConnection connection;
    private final SessionRegistry sessions;
    private final PacketObserver packetObserver;

    public PlayerSessionListener(AegisConfig config, AegisConnection connection, SessionRegistry sessions, PacketObserver packetObserver) {
        this.config = config;
        this.connection = connection;
        this.sessions = sessions;
        this.packetObserver = packetObserver;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        PlayerSession session = sessions.register(player, config.isAlertsEnabledByDefault());

        if (connection.isConnected()) {
            sendJoin(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        sessions.remove(player.getId());
        if (packetObserver != null) {
            packetObserver.removeSession(player.getId());
        }

        if (connection.isConnected()) {
            connection.send(FrameWriter.quit(player.getId()));
        }
    }

    public void sendJoin(PlayerSession session) {
        Player player = session.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }

        LoginChainData chain = player.getLoginChainData();
        String xuid = chain != null && chain.getXUID() != null ? chain.getXUID() : "";
        String version = chain != null && chain.getGameVersion() != null ? chain.getGameVersion() : "";
        int deviceOS = chain != null ? chain.getDeviceOS() : 0;
        int inputMode = chain != null ? chain.getCurrentInputMode() : 0;
        int defaultInputMode = chain != null ? chain.getDefaultInputMode() : 0;
        int uiProfile = chain != null ? chain.getUIProfile() : 0;
        int guiScale = chain != null ? chain.getGuiScale() : 0;
        String deviceModel = chain != null && chain.getDeviceModel() != null ? chain.getDeviceModel() : "";
        String deviceId = chain != null && chain.getDeviceId() != null ? chain.getDeviceId() : "";
        long clientRandomId = chain != null ? chain.getClientId() : 0L;
        String titleId = chain != null && chain.getTitleId() != null ? chain.getTitleId() : "";
        String languageCode = chain != null && chain.getLanguageCode() != null ? chain.getLanguageCode() : "";
        String serverAddress = chain != null && chain.getServerAddress() != null ? chain.getServerAddress() : "";
        boolean xboxAuthed = chain != null && chain.isXboxAuthed();

        String platformOnlineId = "";
        String platformOfflineId = "";
        String playFabId = "";
        if (chain != null && chain.getRawData() != null) {
            var raw = chain.getRawData();
            if (raw.has("PlatformOnlineId")) platformOnlineId = raw.get("PlatformOnlineId").getAsString();
            if (raw.has("PlatformOfflineId")) platformOfflineId = raw.get("PlatformOfflineId").getAsString();
            if (raw.has("PlayFabId")) playFabId = raw.get("PlayFabId").getAsString();
        }

        byte[] frame = FrameWriter.join(
                player.getId(),
                player.getName(),
                xuid,
                player.protocol,
                version,
                deviceOS,
                inputMode,
                defaultInputMode,
                uiProfile,
                guiScale,
                deviceModel,
                deviceId,
                clientRandomId,
                titleId,
                languageCode,
                serverAddress,
                xboxAuthed,
                platformOnlineId,
                platformOfflineId,
                playFabId,
                0
        );
        connection.send(frame);
    }
}
