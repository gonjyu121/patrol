package dev.gonjy.patrolspectator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /patrol コマンドの処理を担当するクラス。
 */
public class PatrolCommand implements CommandExecutor {

    private final PatrolSpectatorPlugin plugin;
    private final PatrolManager patrolManager;
    private final RankingDisplaySystem rankingDisplaySystem;

    public PatrolCommand(PatrolSpectatorPlugin plugin, PatrolManager patrolManager,
            RankingDisplaySystem rankingDisplaySystem) {
        this.plugin = plugin;
        this.patrolManager = patrolManager;
        this.rankingDisplaySystem = rankingDisplaySystem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"patrol".equalsIgnoreCase(command.getName()))
            return false;

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§a/patrol start [dwellSeconds] - 観光巡りをスタート");
            sender.sendMessage("§a/patrol stop                 - 停止");
            sender.sendMessage("§a/patrol status               - 状態表示");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                Player p = (Player) sender;

                int dwell = plugin.getTourConf().dwellSeconds;
                if (args.length >= 2) {
                    try {
                        dwell = Math.max(3, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }

                patrolManager.startPatrol(p, dwell);
                sender.sendMessage("§a[Patrol] start (dwell=" + dwell + "s)");
                break;
            }
            case "stop": {
                patrolManager.stopPatrol();
                sender.sendMessage("§e[Patrol] stop");
                break;
            }
            case "status": {
                String running = patrolManager.isRunning() ? "RUNNING" : "IDLE";
                sender.sendMessage("§b[Patrol] status=" + running + ", locations=" + patrolManager.getLocationCount());
                break;
            }
            case "rank": {
                if (patrolManager.isRunning()) {
                    rankingDisplaySystem.displayRankings();
                    sender.sendMessage("§a[Patrol] Ranking display triggered manually.");
                } else {
                    sender.sendMessage("§c[Patrol] Patrol is not running. Start patrol first.");
                }
                break;
            }
            default:
                sender.sendMessage("Unknown subcommand. /patrol help");
        }
        return true;
    }
}
