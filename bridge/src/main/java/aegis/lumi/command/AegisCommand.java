package aegis.lumi.command;

import aegis.lumi.AegisLumiPlugin;
import aegis.lumi.config.AegisConfig;
import aegis.lumi.network.AegisConnection;
import aegis.lumi.session.PlayerSession;
import aegis.lumi.session.SessionRegistry;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandExecutor;
import cn.nukkit.command.CommandSender;

public final class AegisCommand extends Command implements CommandExecutor {

    private final AegisLumiPlugin plugin;
    private final AegisConfig config;
    private final AegisConnection connection;
    private final SessionRegistry sessions;

    public AegisCommand(AegisLumiPlugin plugin,
                        AegisConfig config,
                        AegisConnection connection,
                        SessionRegistry sessions) {
        super("aegis", "Aegis Anticheat management and status command", "/aegis [status|alerts [on|off]|reload]");
        setPermission("aegis.command");
        this.plugin = plugin;
        this.config = config;
        this.connection = connection;
        this.sessions = sessions;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> {
                String connState = switch (connection.getState()) {
                    case CONNECTED -> "§aCONNECTED";
                    case HANDSHAKING -> "§eHANDSHAKING";
                    case CONNECTING -> "§6CONNECTING";
                    case DISCONNECTED -> "§cDISCONNECTED";
                };

                sender.sendMessage("§8---- §cAegis-LUMI Status §8----");
                sender.sendMessage("§7Sidecar Host: §f" + config.getHost() + ":" + config.getPort());
                sender.sendMessage("§7Sidecar Status: " + connState);
                sender.sendMessage("§7Tracked Sessions: §a" + sessions.size());
                sender.sendMessage("§7Server TPS: §a" + String.format("%.2f", Server.getInstance().getTicksPerSecond()));
                sender.sendMessage("§7Punishments: " + (config.isPunishmentsEnabled() ? "§aENABLED" : "§eDISABLED (Flag only)"));
                sender.sendMessage("§8-----------------------------");
            }
            case "alerts" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cThis subcommand can only be executed by a player.");
                    return true;
                }

                if (!player.hasPermission("aegis.alerts")) {
                    player.sendMessage("§cYou don't have permission to receive Aegis alerts.");
                    return true;
                }

                PlayerSession session = sessions.get(player.getId());
                if (session == null) {
                    player.sendMessage("§cFailed to find your active session.");
                    return true;
                }

                boolean newState;
                if (args.length >= 2) {
                    if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("true")) {
                        newState = true;
                    } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("disable") || args[1].equalsIgnoreCase("false")) {
                        newState = false;
                    } else {
                        player.sendMessage("§cUsage: /aegis alerts [on|off]");
                        return true;
                    }
                } else {
                    newState = !session.isAlertsEnabled();
                }

                session.setAlertsEnabled(newState);
                player.sendMessage("§8[§cAegis§8] §7Alerts are now " + (newState ? "§aENABLED" : "§cDISABLED") + "§7.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                config.load(plugin.getConfig());
                sender.sendMessage("§8[§cAegis§8] §aConfiguration reloaded successfully.");
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8---- §cAegis-LUMI Help §8----");
        sender.sendMessage("§e/aegis status §7- View bridge and sidecar connection health");
        sender.sendMessage("§e/aegis alerts [on|off] §7- Toggle anticheat detection notifications");
        sender.sendMessage("§e/aegis reload §7- Reload configuration from disk");
        sender.sendMessage("§8---------------------------");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }
}
