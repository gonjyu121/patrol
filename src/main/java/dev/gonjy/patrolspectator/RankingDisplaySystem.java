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
     * ランキング集計データを保持する内部クラス
     */
    private static class RankingData {
        List<Map.Entry<UUID, Long>> totalPlayTime;
        List<Map.Entry<UUID, Long>> todayPlayTime;
        List<Map.Entry<UUID, Long>> continuousSurvival;
        List<Map.Entry<UUID, Integer>> kills;
        List<Map.Entry<UUID, Integer>> dragonKills;
        List<Map.Entry<UUID, Integer>> eventPoints;
        List<Map.Entry<UUID, Integer>> dungeonLevels;
    }

    public void displayRankings() {
        // 通知
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.GOLD + "🏆 ランキング発表 🏆",
                    ChatColor.YELLOW + "集計中...",
                    10, 40, 10);
        }

        // 非同期でランキングを集計（重い処理をメインスレッドから逃がす）
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RankingData data = new RankingData();
            data.totalPlayTime = getTotalSurvivalTimeRanking();
            data.todayPlayTime = getTodayPlayTimeRanking();
            data.continuousSurvival = getContinuousSurvivalTimeRanking();
            data.kills = getKillCountRanking();
            data.dragonKills = getEnderDragonKillRanking();
            data.eventPoints = getEventPointsRanking();

            if (plugin instanceof PatrolSpectatorPlugin) {
                dev.gonjy.patrolspectator.dungeon.DungeonStatsStorage ds = ((PatrolSpectatorPlugin) plugin)
                        .getDungeonStatsStorage();
                if (ds != null) {
                    List<Map.Entry<UUID, Integer>> dungeonRanking = new ArrayList<>(ds.getAllPlayerLevels().entrySet());
                    dungeonRanking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                    data.dungeonLevels = dungeonRanking;
                }
            }

            // メメインスレッドに戻して順次表示
            Bukkit.getScheduler().runTask(plugin, () -> {
                displayAllRankingsSequentially(data);
            });
        });
    }

    private void displayAllRankingsSequentially(RankingData data) {
        displayTotalPlayTimeRanking(data.totalPlayTime);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            displayTodayPlayTimeRanking(data.todayPlayTime);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                displayContinuousSurvivalTimeRanking(data.continuousSurvival);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    displayKillCountRanking(data.kills);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        displayEnderDragonKillRanking(data.dragonKills);

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            displayEventPointsRanking(data.eventPoints);

                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (data.dungeonLevels != null && !data.dungeonLevels.isEmpty()) {
                                    displayDungeonRanking(data.dungeonLevels);
                                }

                                Bukkit.getServer().broadcastMessage("");
                                Bukkit.getServer()
                                        .broadcastMessage(ChatColor.GRAY + "※ " + ChatColor.GOLD + "[★]" + ChatColor.GRAY
                                                + " は " + ChatColor.RED + "ヴォイド・ドラゴン" + ChatColor.GRAY + " 討伐の証です");
                            }, 40L);
                        }, 40L);
                    }, 40L);
                }, 40L);
            }, 40L);
        }, 40L);
    }

    private void broadcastToDiscord(String title, List<String> lines) {
        if (plugin instanceof PatrolSpectatorPlugin) {
            DiscordWebhookClient client = ((PatrolSpectatorPlugin) plugin).getDiscordWebhookClient();
            if (client != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("**").append(title).append("**\n");
                for (String line : lines) {
                    sb.append(line).append("\n");
                }
                client.send(sb.toString());
            }
        }
    }

    /**
     * 累計プレイ時間ランキングを表示します。
     */
    private void displayTotalPlayTimeRanking(List<Map.Entry<UUID, Long>> ranking) {

        List<String> discordLines = new ArrayList<>();

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
                String playerName = getDisplayName(entry.getKey());
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

                String line = "  " + medal + " " + status + " " + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": "
                        + ChatColor.GOLD + timeDisplay;
                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + line);

                // Remove color codes for Discord
                discordLines.add(ChatColor.stripColor(medal + " " + playerName + ": " + timeDisplay));
            }
        } else {
            Bukkit.getServer()
                    .broadcastMessage(ChatColor.GRAY + "  📊 まだ記録保持者がいません。あなたの挑戦を待っています！");
            discordLines.add("記録保持者なし");
        }

        broadcastToDiscord("🏆 累計プレイ時間ランキング", discordLines);
    }

    /**
     * 今日のプレイ時間ランキングを表示します。
     */
    private void displayTodayPlayTimeRanking(List<Map.Entry<UUID, Long>> ranking) {
        List<String> discordLines = new ArrayList<>();

        // Title表示
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.GREEN + "📅 今日のプレイ時間ランキング",
                    ChatColor.YELLOW + "本日（0時以降）のプレイ時間です",
                    10, 40, 10);
        }

        // チャット表示
        Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "📅 今日のプレイ時間ランキング 📅");
        Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  本日（0時以降）のプレイ時間です");
        Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Long> entry = ranking.get(i);
                String playerName = getDisplayName(entry.getKey());
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

                String line = "  " + medal + " " + status + " " + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": "
                        + ChatColor.GREEN + timeDisplay;
                Bukkit.getServer().broadcastMessage(line);

                discordLines.add(ChatColor.stripColor(medal + " " + playerName + ": " + timeDisplay));
            }
        } else {
            Bukkit.getServer()
                    .broadcastMessage(ChatColor.GRAY + "  📅 まだ本日の記録保持者がいません。");
            discordLines.add("記録保持者なし");
        }

        broadcastToDiscord("📅 今日のプレイ時間ランキング", discordLines);
    }

    /**
     * 連続生存時間ランキングを表示します。
     */
    private void displayContinuousSurvivalTimeRanking(List<Map.Entry<UUID, Long>> ranking) {

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
                String playerName = getDisplayName(entry.getKey());
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
    private void displayKillCountRanking(List<Map.Entry<UUID, Integer>> ranking) {

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
                String playerName = getDisplayName(entry.getKey());
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
    private void displayEnderDragonKillRanking(List<Map.Entry<UUID, Integer>> ranking) {
        List<String> discordLines = new ArrayList<>();

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
                String playerName = getDisplayName(entry.getKey());
                int dragonKills = entry.getValue();
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " "
                        + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.LIGHT_PURPLE
                        + dragonKills + "討伐");

                discordLines.add(ChatColor.stripColor(medal + " " + playerName + ": " + dragonKills + "討伐"));
            }
        } else {
            Bukkit.getServer().broadcastMessage(
                    ChatColor.GRAY + "  🐉 まだドラゴンスレイヤーはいません。伝説を作るのはあなたです！");
            discordLines.add("記録保持者なし");
        }

        broadcastToDiscord("🐉 エンダードラゴン討伐数ランキング", discordLines);
    }

    /**
     * イベントポイントランキングを表示します。
     */
    private void displayEventPointsRanking(List<Map.Entry<UUID, Integer>> ranking) {

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
                String playerName = getDisplayName(entry.getKey());
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
     * 迷宮踏破ランキングを表示します。
     */
    private void displayDungeonRanking(List<Map.Entry<UUID, Integer>> ranking) {
        // Title表示
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.DARK_RED + "💀 迷宮踏破ランキング 💀",
                    ChatColor.YELLOW + "地下深く、魔境に挑んだ勇者たちです",
                    10, 40, 10);
        }

        // チャット表示
        Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + "💀 迷宮踏破ランキング 💀");
        Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  地下深く、魔境に挑んだ勇者たちです");
        Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Integer> entry = ranking.get(i);
                String playerName = getDisplayName(entry.getKey());
                int level = entry.getValue();
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " "
                        + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.RED + "地下 " + level
                        + " 階");
            }
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
     * 今日のプレイ時間ランキングを取得します。
     * 
     * @return 今日のプレイ時間ランキング（上位から順）
     */
    private List<Map.Entry<UUID, Long>> getTodayPlayTimeRanking() {
        List<Map.Entry<UUID, Long>> ranking = new ArrayList<>();

        for (UUID playerId : statsStorage.getAllPlayerIds()) {
            if (playerId.equals(excludedPlayerUuid)) continue;
            long todayTime = statsStorage.getTodayPlayTimeMillis(playerId);
            if (todayTime > 60000) { // 1分以上
                ranking.add(new SimpleEntry<>(playerId, todayTime));
            }
        }

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

    /**
     * プレイヤー名を取得し、称号があれば付与します。
     * 
     * @param uuid プレイヤーのUUID
     * @return 表示名
     */
    private String getDisplayName(UUID uuid) {
        String name = statsStorage.getPlayerName(uuid);
        if (statsStorage.isHardDragonSlayer(uuid)) {
            return name + ChatColor.GOLD + " [★]" + ChatColor.RESET;
        }
        return name;
    }
}
