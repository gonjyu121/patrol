package dev.gonjy.patrolspectator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /patrol コマンドの処理を担当するクラス。
 */
public class PatrolCommand implements CommandExecutor, TabCompleter {

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
            sender.sendMessage("§a/patrol rank                 - ランキング表示");
            if (sender.isOp()) {
                sender.sendMessage("§a/patrol reset_survival       - 連続生存時間リセット(OP)");
                sender.sendMessage("§a/patrol backup               - ランキングのDiscordバックアップ(OP)");
            }
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
            case "reset_survival": {
                if (!sender.isOp()) {
                    sender.sendMessage("§cPermission denied.");
                    return true;
                }
                plugin.getStatsStorage().resetAllContinuousSurvivalTime();
                sender.sendMessage("§a[Patrol] All continuous survival times have been reset.");
                break;
            }
            case "backup": {
                if (!sender.isOp()) {
                    sender.sendMessage("§cPermission denied.");
                    return true;
                }
                sender.sendMessage("§a[Patrol] Stats backup triggered. Check Discord shortly.");
                plugin.backupStats();
                break;
            }
            case "reload": {
                if (!sender.isOp()) {
                    sender.sendMessage("§cPermission denied.");
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage("§a[Patrol] Configuration and legacy stats reloaded.");
                break;
            }
            default:
                sender.sendMessage("Unknown subcommand. /patrol help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>(Arrays.asList("start", "stop", "status", "rank"));
            if (sender.isOp()) {
                sub.add("reset_survival");
                sub.add("backup");
                sub.add("reload");
            }
            List<String> ret = new ArrayList<>();
            for (String s : sub) {
                if (s.startsWith(args[0].toLowerCase()))
                    ret.add(s);
            }
            return ret;
        }
        return Collections.emptyList();
    }
}
