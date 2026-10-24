package aegis.lumi.listener;

import aegis.lumi.config.AegisConfig;
import aegis.lumi.network.AegisConnection;
import aegis.lumi.network.FrameWriter;
import aegis.lumi.session.PlayerSession;
import aegis.lumi.session.SessionRegistry;
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

    public PlayerSessionListener(AegisConfig config, AegisConnection connection, SessionRegistry sessions) {
        this.config = config;
        this.connection = connection;
        this.sessions = sessions;
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
        String xuid = chain != null ? chain.getXUID() : "";
        String version = chain != null ? chain.getGameVersion() : "";
        int deviceOS = chain != null ? chain.getDeviceOS() : 0;
        int inputMode = chain != null ? chain.getCurrentInputMode() : 0;

        byte[] frame = FrameWriter.join(
                player.getId(),
                player.getName(),
                xuid,
                player.protocol,
                version,
                deviceOS,
                inputMode
        );
        connection.send(frame);
    }
}
