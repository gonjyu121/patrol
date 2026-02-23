package dev.gonjy.patrolspectator;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand implements CommandExecutor {

    private final PatrolSpectatorPlugin plugin;
    private final EngagementSystem engagementSystem;

    public StatsCommand(PatrolSpectatorPlugin plugin, EngagementSystem engagementSystem) {
        this.plugin = plugin;
        this.engagementSystem = engagementSystem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        Player player = (Player) sender;
        PlayerStatsStorage stats = plugin.getStatsStorage();

        long totalMs = stats.getTotalPlayTimeMillis(player.getUniqueId());
        long todayMs = stats.getTodayPlayTimeMillis(player.getUniqueId());
        String rank = stats.getPlayerRank(player.getUniqueId());
        int points = stats.getEventPoints(player.getUniqueId());

        sender.sendMessage(ChatColor.GOLD + "========== [ あなたの統計情報 ] ==========");
        sender.sendMessage(ChatColor.YELLOW + "現在のランク: " + ChatColor.WHITE + rank);
        sender.sendMessage(ChatColor.YELLOW + "累計プレイ時間: " + ChatColor.WHITE + formatTime(totalMs));
        sender.sendMessage(ChatColor.YELLOW + "今日のプレイ時間: " + ChatColor.WHITE + formatTime(todayMs));
        sender.sendMessage(ChatColor.YELLOW + "イベントポイント: " + ChatColor.WHITE + points + " pt");
        sender.sendMessage("");
        sender.sendMessage(engagementSystem.getRankProgressMessage(player));
        sender.sendMessage(ChatColor.GOLD + "========================================");

        return true;
    }

    private String formatTime(long ms) {
        long minutes = ms / (1000 * 60);
        long hours = minutes / 60;
        if (hours > 0) {
            return hours + "時間" + (minutes % 60) + "分";
        } else {
            return minutes + "分";
        }
    }
}
