package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;

/**
 * ランキング表示システム
 * <p>
 * 5分間隔で以下の4種類のランキングを表示します:
 * <ul>
 * <li>累計生存時間ランキング</li>
 * <li>PK数ランキング</li>
 * <li>エンダードラゴン討伐数ランキング</li>
 * <li>イベントポイントランキング</li>
 * </ul>
 */
@SuppressWarnings("deprecation") // Using legacy ChatColor, broadcastMessage, and sendTitle for compatibility
public class RankingDisplaySystem {

    private final JavaPlugin plugin;
    private final PlayerStatsStorage statsStorage;

    private BukkitTask rankingTask;
    private static final long RANKING_INTERVAL = 300L; // 5分 = 300秒

    public RankingDisplaySystem(JavaPlugin plugin, PlayerStatsStorage statsStorage) {
        this.plugin = plugin;
        this.statsStorage = statsStorage;
    }

    /**
     * ランキング表示を開始します。
     */
    public void startRankingDisplay() {
        if (rankingTask != null) {
            rankingTask.cancel();
        }

        rankingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::displayRankings,
                RANKING_INTERVAL * 20L, RANKING_INTERVAL * 20L);
        plugin.getLogger().info("ランキング表示を開始しました（" + RANKING_INTERVAL + "秒間隔）");
    }

    /**
     * ランキング表示を停止します。
     */
    public void stopRankingDisplay() {
        if (rankingTask != null) {
            rankingTask.cancel();
            rankingTask = null;
            plugin.getLogger().info("ランキング表示を停止しました");
        }
    }

    /**
     * 4種類のランキングを順番に表示します。
     */
    private UUID excludedPlayerUuid;

    /**
     * ランキングから除外するプレイヤーを設定します。
     * 
     * @param uuid 除外するプレイヤーのUUID（nullの場合は除外なし）
     */
    public void setExcludedPlayer(UUID uuid) {
        this.excludedPlayerUuid = uuid;
    }

    /**
     * 5種類のランキングを順番に表示します。
     */
    public void displayRankings() {
        // Title表示でランキング開始を通知
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.GOLD + "🏆 ランキング発表 🏆",
                    ChatColor.YELLOW + "5秒後に詳細を表示します",
                    10, 60, 20);
        }

        // 5秒後に詳細ランキングを表示
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            displayTotalPlayTimeRanking(); // 1. 累計プレイ時間

            // 2秒後に連続生存時間ランキング
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                displayContinuousSurvivalTimeRanking(); // 2. 連続生存時間

                // 2秒後にPK数ランキング
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    displayKillCountRanking(); // 3. PK数

                    // 2秒後にエンダードラゴン討伐数ランキング
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        displayEnderDragonKillRanking(); // 4. エンドラ討伐数

                        // 2秒後にイベントポイントランキング
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            displayEventPointsRanking(); // 5. イベントポイント
                        }, 40L);
                    }, 40L);
                }, 40L);
            }, 40L);
        }, 100L);
    }

    /**
     * 累計プレイ時間ランキングを表示します。
     */
    private void displayTotalPlayTimeRanking() {
        List<Map.Entry<UUID, Long>> ranking = getTotalSurvivalTimeRanking();

        // Title表示
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.GOLD + "🏆 累計プレイ時間ランキング",
                    ChatColor.YELLOW + "サーバーで遊んでくれた時間の合計です",
                    10, 40, 10);
        }

        // チャット表示
        Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "🏆 累計プレイ時間ランキング 🏆");
        Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  サーバーで遊んでくれた時間の合計です");
        Bukkit.getServer().broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Long> entry = ranking.get(i);
                String playerName = statsStorage.getPlayerName(entry.getKey());
                long totalMinutes = entry.getValue() / (1000 * 60);
                long totalHours = totalMinutes / 60;
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                String timeDisplay;
                if (totalHours > 0) {
                    timeDisplay = totalHours + "時間" + (totalMinutes % 60) + "分";
                } else {
                    timeDisplay = totalMinutes + "分";
                }

                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " "
                        + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.GOLD + timeDisplay);
            }
        } else {
            Bukkit.getServer()
                    .broadcastMessage(ChatColor.GRAY + "  📊 まだ記録保持者がいません。あなたの挑戦を待っています！");
        }
    }

    /**
     * 連続生存時間ランキングを表示します。
     */
    private void displayContinuousSurvivalTimeRanking() {
        List<Map.Entry<UUID, Long>> ranking = getContinuousSurvivalTimeRanking();

        // Title表示
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.RED + "🔥 連続生存時間ランキング 🔥",
                    ChatColor.YELLOW + "死なずにプレイできているランキングです。",
                    10, 40, 10);
        }

        // チャット表示
        Bukkit.getServer().broadcastMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.getServer().broadcastMessage(ChatColor.RED + "🔥 連続生存時間ランキング 🔥");
        Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  死なずにプレイできているランキングです");
        Bukkit.getServer().broadcastMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Long> entry = ranking.get(i);
                String playerName = statsStorage.getPlayerName(entry.getKey());
                long totalMinutes = entry.getValue() / (1000 * 60);
                long totalHours = totalMinutes / 60;
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                String timeDisplay;
                if (totalHours > 0) {
                    timeDisplay = totalHours + "時間" + (totalMinutes % 60) + "分";
                } else {
                    timeDisplay = totalMinutes + "分";
                }

                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " "
                        + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.RED + timeDisplay);
            }
        } else {
            Bukkit.getServer()
                    .broadcastMessage(ChatColor.GRAY + "  🔥 まだ生存者はいません。生き残れ！");
        }
    }

    /**
     * PK数ランキングを表示します。
     */
    private void displayKillCountRanking() {
        List<Map.Entry<UUID, Integer>> ranking = getKillCountRanking();

        // Title表示
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.RED + "⚔️ PK数ランキング ⚔️",
                    "",
                    10, 40, 10);
        }

        // チャット表示
        Bukkit.getServer().broadcastMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.getServer().broadcastMessage(ChatColor.RED + "⚔️ PK数ランキング ⚔️");
        Bukkit.getServer().broadcastMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Integer> entry = ranking.get(i);
                String playerName = statsStorage.getPlayerName(entry.getKey());
                int kills = entry.getValue();
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " "
                        + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.RED + kills + "キル");
            }
        } else {
            Bukkit.getServer()
                    .broadcastMessage(ChatColor.GRAY + "  ⚔️ まだPK王はいません。最初の王者になるのは誰だ！？");
        }
    }

    /**
     * エンダードラゴン討伐数ランキングを表示します。
     */
    private void displayEnderDragonKillRanking() {
        List<Map.Entry<UUID, Integer>> ranking = getEnderDragonKillRanking();

        // Title表示
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.LIGHT_PURPLE + "🐉 エンドラ討伐数ランキング 🐉",
                    "",
                    10, 40, 10);
        }

        // チャット表示
        Bukkit.getServer()
                .broadcastMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.getServer().broadcastMessage(ChatColor.LIGHT_PURPLE + "🐉 エンダードラゴン討伐数ランキング 🐉");
        Bukkit.getServer()
                .broadcastMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Integer> entry = ranking.get(i);
                String playerName = statsStorage.getPlayerName(entry.getKey());
                int dragonKills = entry.getValue();
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " "
                        + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.LIGHT_PURPLE
                        + dragonKills + "討伐");
            }
        } else {
            Bukkit.getServer().broadcastMessage(
                    ChatColor.GRAY + "  🐉 まだドラゴンスレイヤーはいません。伝説を作るのはあなたです！");
        }
    }

    /**
     * イベントポイントランキングを表示します。
     */
    private void displayEventPointsRanking() {
        List<Map.Entry<UUID, Integer>> ranking = getEventPointsRanking();

        // Title表示
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.AQUA + "🎮 イベントポイントランキング 🎮",
                    "",
                    10, 40, 10);
        }

        // チャット表示
        Bukkit.getServer().broadcastMessage(ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.getServer().broadcastMessage(ChatColor.AQUA + "🎮 イベントポイントランキング 🎮");
        Bukkit.getServer().broadcastMessage(ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Integer> entry = ranking.get(i);
                String playerName = statsStorage.getPlayerName(entry.getKey());
                int eventPoints = entry.getValue();
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " "
                        + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.AQUA + eventPoints
                        + "ポイント");
            }
        } else {
            Bukkit.getServer()
                    .broadcastMessage(ChatColor.GRAY + "  🎮 まだイベント勝者はいません。次のイベントで勝利を掴め！");
        }
    }

    /**
     * 累計生存時間ランキングを取得します。
     * 
     * @return 累計生存時間ランキング（上位から順）
     */
    private List<Map.Entry<UUID, Long>> getTotalSurvivalTimeRanking() {
        List<Map.Entry<UUID, Long>> ranking = new ArrayList<>();

        for (UUID playerId : statsStorage.getAllPlayerIds()) {
            // 除外プレイヤーはスキップ
            if (playerId.equals(excludedPlayerUuid)) {
                continue;
            }

            long totalTime = statsStorage.getTotalPlayTimeMillis(playerId);

            // 累計生存時間が1分以上ある場合のみ
            if (totalTime > 60000) {
                ranking.add(new SimpleEntry<>(playerId, totalTime));
            }
        }

        // 累計生存時間の長い順にソート
        ranking.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return ranking;
    }

    /**
     * 連続生存時間ランキングを取得します。
     * 
     * @return 連続生存時間ランキング（上位から順）
     */
    private List<Map.Entry<UUID, Long>> getContinuousSurvivalTimeRanking() {
        List<Map.Entry<UUID, Long>> ranking = new ArrayList<>();

        for (UUID playerId : statsStorage.getAllPlayerIds()) {
            // 除外プレイヤーはスキップ
            if (playerId.equals(excludedPlayerUuid)) {
                continue;
            }

            long continuousTime = statsStorage.getContinuousSurvivalTimeMillis(playerId);

            // 連続生存時間が1分以上ある場合のみ
            if (continuousTime > 60000) {
                ranking.add(new SimpleEntry<>(playerId, continuousTime));
            }
        }

        // 連続生存時間の長い順にソート
        ranking.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return ranking;
    }

    /**
     * PK数ランキングを取得します。
     * 
     * @return PK数ランキング（上位から順）
     */
    private List<Map.Entry<UUID, Integer>> getKillCountRanking() {
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>();

        for (UUID playerId : statsStorage.getAllPlayerIds()) {
            // 除外プレイヤーはスキップ
            if (playerId.equals(excludedPlayerUuid)) {
                continue;
            }

            int kills = statsStorage.getPlayerKills(playerId);

            // キル数が1以上ある場合のみ
            if (kills > 0) {
                ranking.add(new SimpleEntry<>(playerId, kills));
            }
        }

        // キル数の多い順にソート
        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return ranking;
    }

    /**
     * エンダードラゴン討伐数ランキングを取得します。
     * 
     * @return エンダードラゴン討伐数ランキング（上位から順）
     */
    private List<Map.Entry<UUID, Integer>> getEnderDragonKillRanking() {
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>();

        for (UUID playerId : statsStorage.getAllPlayerIds()) {
            // 除外プレイヤーはスキップ
            if (playerId.equals(excludedPlayerUuid)) {
                continue;
            }

            int dragonKills = statsStorage.getEnderDragonKills(playerId);

            // エンダードラゴン討伐数が1以上ある場合のみ
            if (dragonKills > 0) {
                ranking.add(new SimpleEntry<>(playerId, dragonKills));
            }
        }

        // 討伐数の多い順にソート
        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return ranking;
    }

    /**
     * イベントポイントランキングを取得します。
     * 
     * @return イベントポイントランキング（上位から順）
     */
    private List<Map.Entry<UUID, Integer>> getEventPointsRanking() {
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>();

        for (UUID playerId : statsStorage.getAllPlayerIds()) {
            // 除外プレイヤーはスキップ
            if (playerId.equals(excludedPlayerUuid)) {
                continue;
            }

            int eventPoints = statsStorage.getEventPoints(playerId);

            // イベントポイントが1以上ある場合のみ
            if (eventPoints > 0) {
                ranking.add(new SimpleEntry<>(playerId, eventPoints));
            }
        }

        // イベントポイントの多い順にソート
        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return ranking;
    }
}
