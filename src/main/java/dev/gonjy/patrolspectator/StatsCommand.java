package dev.gonjy.patrolspectator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

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

        sender.sendMessage(Component.text("========== [ あなたの統計情報 ] ==========", NamedTextColor.GOLD));
        sender.sendMessage(statLine("現在のランク: ", rank));
        sender.sendMessage(statLine("累計プレイ時間: ", formatTime(totalMs)));
        sender.sendMessage(statLine("今日のプレイ時間: ", formatTime(todayMs)));
        sender.sendMessage(statLine("イベントポイント: ", points + " pt"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(LEGACY_SECTION.deserialize(engagementSystem.getRankProgressMessage(player)));
        sender.sendMessage(Component.text("========================================", NamedTextColor.GOLD));

        return true;
    }

    private static Component statLine(String label, String value) {
        return Component.text(label, NamedTextColor.YELLOW)
                .append(Component.text(value, NamedTextColor.WHITE));
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
