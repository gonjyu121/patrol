package dev.gonjy.patrolspectator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.time.Duration;

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
public class RankingDisplaySystem {

    private final JavaPlugin plugin;
    private final PlayerStatsStorage statsStorage;
    private boolean isDisplaying = false;

    private BukkitTask rankingTask;
    private static final long RANKING_CYCLE_INTERVAL = 300L; // 5分 = 300秒
    private static final long RANKING_STEP_DELAY = 160L; // 8秒 = 160 ticks

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
                RANKING_CYCLE_INTERVAL * 20L, RANKING_CYCLE_INTERVAL * 20L);
        plugin.getLogger().info("ランキング表示を開始しました（" + RANKING_CYCLE_INTERVAL + "秒間隔）");
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
        if (isDisplaying) return;
        
        // サーバー負荷が高い場合はランキング表示をスキップ
        if (plugin instanceof PatrolSpectatorPlugin) {
            TickMonitor monitor = ((PatrolSpectatorPlugin) plugin).getTickMonitor();
            if (monitor != null && monitor.isHighLoad()) {
                plugin.getLogger().warning("[Ranking] サーバー負荷が高いため、今回のランキング発表をスキップします。");
                return;
            }
        }

        isDisplaying = true;

        // 通知
        for (Player player : CameraMessageRouter.recipients(plugin)) {
            showRankingTitle(player,
                    Component.text("🏆 ランキング発表 🏆", NamedTextColor.GOLD),
                    Component.text("集計中...", NamedTextColor.YELLOW));
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

            // メインスレッドに戻して順次表示
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    displayAllRankingsSequentially(data);
                } finally {
                    isDisplaying = false;
                }
            });
        });
    }

    private void displayAllRankingsSequentially(RankingData data) {
        StringBuilder discordSummary = new StringBuilder();
        discordSummary.append("🏆 **サーバーランキング発表** 🏆\n\n");

        // 1. 累計プレイ時間
        displayTotalPlayTimeRanking(data.totalPlayTime, discordSummary);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 2. 今日のプレイ時間
            displayTodayPlayTimeRanking(data.todayPlayTime, discordSummary);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // 3. 連続生存時間
                displayContinuousSurvivalTimeRanking(data.continuousSurvival, discordSummary);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // 4. PK数
                    displayKillCountRanking(data.kills, discordSummary);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // 5. エンドラ討伐数
                        displayEnderDragonKillRanking(data.dragonKills, discordSummary);

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            // 6. イベントポイント
                            displayEventPointsRanking(data.eventPoints, discordSummary);

                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                // 7. 迷宮踏破
                                if (shouldDisplayDungeonRanking(data.dungeonLevels)) {
                                    displayDungeonRanking(data.dungeonLevels, discordSummary);
                                }
                                displayDungeonBuildAnnouncement(discordSummary);

                                CameraMessageRouter.send(plugin, Component.empty());
                                CameraMessageRouter.send(plugin, Component.text("※ ", NamedTextColor.GRAY)
                                        .append(Component.text("[★]", NamedTextColor.GOLD))
                                        .append(Component.text(" は ", NamedTextColor.GRAY))
                                        .append(Component.text("ヴォイド・ドラゴン", NamedTextColor.RED))
                                        .append(Component.text(" 討伐の証です", NamedTextColor.GRAY)));
                                
                                // Discordへ一括送信
                                sendSummaryToDiscord(discordSummary.toString());
                            }, RANKING_STEP_DELAY);
                        }, RANKING_STEP_DELAY);
                    }, RANKING_STEP_DELAY);
                }, RANKING_STEP_DELAY);
            }, RANKING_STEP_DELAY);
        }, RANKING_STEP_DELAY);
    }

    static boolean shouldDisplayDungeonRanking(List<?> ranking) {
        // 記録が0件でも存在を告知し、挑戦のきっかけにする。未初期化(null)だけを除外する。
        return ranking != null;
    }

    /** ランキングの締めに、確認できた迷宮規模だけを告知する。座標は含めない。 */
    private void displayDungeonBuildAnnouncement(StringBuilder discord) {
        if (!(plugin instanceof PatrolSpectatorPlugin patrol)) return;
        dev.gonjy.patrolspectator.dungeon.DungeonManager manager = patrol.getDungeonManager();
        if (manager == null || !manager.isEnabled()) return;

        int planned = manager.getFloorCount();
        int verified = manager.inspectBuiltFloors().verifiedCount();
        boolean building = patrol.getDungeonBuilder() != null && patrol.getDungeonBuilder().isBuilding();
        String announcement = formatDungeonAnnouncement(building, manager.isBuilt(), planned, verified);
        if (announcement == null) return;
        CameraMessageRouter.send(plugin, Component.text("💀 ", NamedTextColor.DARK_RED)
                .append(Component.text(announcement, NamedTextColor.YELLOW)));
        discord.append("**💀 ").append(announcement).append("**\n\n");
    }

    static String formatDungeonAnnouncement(boolean building, boolean recordedComplete, int planned, int verified) {
        // 生成中の構造物や入口階さえ確認できない状態は宣伝しない。
        if (building || planned < 1 || verified < 1) return null;
        if (recordedComplete && verified == planned) {
            return "死の迷宮は地下B" + planned + "まで主要構造を確認！挑戦者募集中！";
        }
        return "死の迷宮は主要構造 " + verified + "/" + planned + "階を確認！挑戦者募集中！";
    }

    private void sendSummaryToDiscord(String content) {
        if (plugin instanceof PatrolSpectatorPlugin) {
            DiscordWebhookClient client = ((PatrolSpectatorPlugin) plugin).getDiscordWebhookClient();
            if (client != null) {
                client.send(content);
            }
        }
    }



    private void displayTotalPlayTimeRanking(List<Map.Entry<UUID, Long>> ranking, StringBuilder discord) {
        discord.append("**🏆 累計プレイ時間ランキング**\n");

        // Title表示
        for (Player player : CameraMessageRouter.recipients(plugin)) {
            showRankingTitle(player,
                    Component.text("🏆 累計プレイ時間ランキング", NamedTextColor.GOLD),
                    Component.text("サーバーで遊んでくれた時間の合計です", NamedTextColor.YELLOW));
        }

        // チャット表示
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD);
        sendLine("🏆 累計プレイ時間ランキング 🏆", NamedTextColor.GOLD);
        sendLine("  サーバーで遊んでくれた時間の合計です", NamedTextColor.YELLOW);
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD);

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

                CameraMessageRouter.send(plugin, rankingLine(entry.getKey(), medal, status, timeDisplay, NamedTextColor.GOLD));

                discord.append(medal).append(" ").append(playerName).append(": ").append(timeDisplay).append("\n");
            }
        } else {
            sendLine("  📊 まだ記録保持者がいません。あなたの挑戦を待っています！", NamedTextColor.GRAY);
            discord.append("記録保持者なし\n");
        }
        discord.append("\n");
    }

    /**
     * 今日のプレイ時間ランキングを表示します。
     */
    private void displayTodayPlayTimeRanking(List<Map.Entry<UUID, Long>> ranking, StringBuilder discord) {
        discord.append("**📅 今日のプレイ時間ランキング**\n");

        // Title表示
        for (Player player : CameraMessageRouter.recipients(plugin)) {
            showRankingTitle(player,
                    Component.text("📅 今日のプレイ時間ランキング", NamedTextColor.GREEN),
                    Component.text("本日（0時以降）のプレイ時間です", NamedTextColor.YELLOW));
        }

        // チャット表示
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN);
        sendLine("📅 今日のプレイ時間ランキング 📅", NamedTextColor.GREEN);
        sendLine("  本日（0時以降）のプレイ時間です", NamedTextColor.YELLOW);
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN);

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

                CameraMessageRouter.send(plugin, rankingLine(entry.getKey(), medal, status, timeDisplay, NamedTextColor.GREEN));

                discord.append(medal).append(" ").append(playerName).append(": ").append(timeDisplay).append("\n");
            }
        } else {
            sendLine("  📅 まだ本日の記録保持者がいません。", NamedTextColor.GRAY);
            discord.append("記録保持者なし\n");
        }
        discord.append("\n");
    }

    /**
     * 連続生存時間ランキングを表示します。
     */
    private void displayContinuousSurvivalTimeRanking(List<Map.Entry<UUID, Long>> ranking, StringBuilder discord) {
        discord.append("**🔥 連続生存時間ランキング**\n");

        // Title表示
        for (Player player : CameraMessageRouter.recipients(plugin)) {
            showRankingTitle(player,
                    Component.text("🔥 連続生存時間ランキング 🔥", NamedTextColor.RED),
                    Component.text("死なずにプレイできているランキングです。", NamedTextColor.YELLOW));
        }

        // チャット表示
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED);
        sendLine("🔥 連続生存時間ランキング 🔥", NamedTextColor.RED);
        sendLine("  死なずにプレイできているランキングです", NamedTextColor.YELLOW);
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED);

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

                CameraMessageRouter.send(plugin, rankingLine(entry.getKey(), medal, status, timeDisplay, NamedTextColor.RED));

                discord.append(medal).append(" ").append(playerName).append(": ").append(timeDisplay).append("\n");
            }
        } else {
            sendLine("  🔥 まだ生存者はいません。生き残れ！", NamedTextColor.GRAY);
            discord.append("生存者なし\n");
        }
        discord.append("\n");
    }

    /**
     * PK数ランキングを表示します。
     */
    private void displayKillCountRanking(List<Map.Entry<UUID, Integer>> ranking, StringBuilder discord) {
        discord.append("**⚔️ PK数ランキング**\n");

        // Title表示
        for (Player player : CameraMessageRouter.recipients(plugin)) {
            showRankingTitle(player,
                    Component.text("⚔️ PK数ランキング ⚔️", NamedTextColor.RED),
                    Component.empty());
        }

        // チャット表示
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED);
        sendLine("⚔️ PK数ランキング ⚔️", NamedTextColor.RED);
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED);

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Integer> entry = ranking.get(i);
                String playerName = getDisplayName(entry.getKey());
                int kills = entry.getValue();
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                CameraMessageRouter.send(plugin, rankingLine(entry.getKey(), medal, status, kills + "キル", NamedTextColor.RED));

                discord.append(medal).append(" ").append(playerName).append(": ").append(kills).append("キル\n");
            }
        } else {
            sendLine("  ⚔️ まだPK王はいません。最初の王者になるのは誰だ！？", NamedTextColor.GRAY);
            discord.append("記録保持者なし\n");
        }
        discord.append("\n");
    }

    /**
     * エンダードラゴン討伐数ランキングを表示します。
     */
    private void displayEnderDragonKillRanking(List<Map.Entry<UUID, Integer>> ranking, StringBuilder discord) {
        discord.append("**🐉 エンダードラゴン討伐数ランキング**\n");

        // Title表示
        for (Player player : CameraMessageRouter.recipients(plugin)) {
            showRankingTitle(player,
                    Component.text("🐉 エンドラ討伐数ランキング 🐉", NamedTextColor.LIGHT_PURPLE),
                    Component.empty());
        }

        // チャット表示
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.LIGHT_PURPLE);
        sendLine("🐉 エンダードラゴン討伐数ランキング 🐉", NamedTextColor.LIGHT_PURPLE);
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.LIGHT_PURPLE);

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Integer> entry = ranking.get(i);
                String playerName = getDisplayName(entry.getKey());
                int dragonKills = entry.getValue();
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                CameraMessageRouter.send(plugin, rankingLine(entry.getKey(), medal, status,
                        dragonKills + "討伐", NamedTextColor.LIGHT_PURPLE));

                discord.append(medal).append(" ").append(playerName).append(": ").append(dragonKills).append("討伐\n");
            }
        } else {
            sendLine("  🐉 まだドラゴンスレイヤーはいません。伝説を作るのはあなたです！", NamedTextColor.GRAY);
            discord.append("記録保持者なし\n");
        }
        discord.append("\n");
    }

    /**
     * イベントポイントランキングを表示します。
     */
    private void displayEventPointsRanking(List<Map.Entry<UUID, Integer>> ranking, StringBuilder discord) {
        discord.append("**🎮 イベントポイントランキング**\n");

        // Title表示
        for (Player player : CameraMessageRouter.recipients(plugin)) {
            showRankingTitle(player,
                    Component.text("🎮 イベントPtランキング 🎮", NamedTextColor.AQUA),
                    Component.empty());
        }

        // チャット表示
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA);
        sendLine("🎮 イベントポイントランキング 🎮", NamedTextColor.AQUA);
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA);

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Integer> entry = ranking.get(i);
                String playerName = getDisplayName(entry.getKey());
                int eventPoints = entry.getValue();
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                CameraMessageRouter.send(plugin, rankingLine(entry.getKey(), medal, status,
                        eventPoints + "ポイント", NamedTextColor.AQUA));

                discord.append(medal).append(" ").append(playerName).append(": ").append(eventPoints).append("ポイント\n");
            }
        } else {
            sendLine("  🎮 まだイベント勝者はいません。次のイベントで勝利を掴め！", NamedTextColor.GRAY);
            discord.append("記録保持者なし\n");
        }
        discord.append("\n");
    }

    /**
     * 迷宮踏破ランキングを表示します。
     */
    private void displayDungeonRanking(List<Map.Entry<UUID, Integer>> ranking, StringBuilder discord) {
        discord.append("**💀 迷宮踏破ランキング**\n");

        // Title表示
        for (Player player : CameraMessageRouter.recipients(plugin)) {
            showRankingTitle(player,
                    Component.text("💀 迷宮踏破ランキング 💀", NamedTextColor.DARK_RED),
                    Component.text("地下深く、魔境に挑んだ勇者たちです", NamedTextColor.YELLOW));
        }

        // チャット表示
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED);
        sendLine("💀 迷宮踏破ランキング 💀", NamedTextColor.DARK_RED);
        sendLine("  地下深く、魔境に挑んだ勇者たちです", NamedTextColor.YELLOW);
        sendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED);

        if (!ranking.isEmpty()) {
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                Map.Entry<UUID, Integer> entry = ranking.get(i);
                String playerName = getDisplayName(entry.getKey());
                int level = entry.getValue();
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";

                CameraMessageRouter.send(plugin, rankingLine(entry.getKey(), medal, status,
                        "地下 " + level + " 階", NamedTextColor.RED));

                discord.append(medal).append(" ").append(playerName).append(": 地下 ").append(level).append(" 階\n");
            }
        } else {
            sendLine("  まだ踏破記録はありません。最初の挑戦者を待っています！", NamedTextColor.GRAY);
            discord.append("記録保持者なし\n");
        }
        discord.append("\n");
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
            return name + " [★]";
        }
        return name;
    }

    private Component getDisplayNameComponent(UUID uuid) {
        Component name = Component.text(statsStorage.getPlayerName(uuid), NamedTextColor.WHITE);
        if (statsStorage.isHardDragonSlayer(uuid)) {
            return name.append(Component.text(" [★]", NamedTextColor.GOLD));
        }
        return name;
    }

    private Component rankingLine(UUID uuid, String medal, String status, String value, NamedTextColor valueColor) {
        return Component.text("  " + medal + " " + status + " ", NamedTextColor.YELLOW)
                .append(getDisplayNameComponent(uuid))
                .append(Component.text(": ", NamedTextColor.YELLOW))
                .append(Component.text(value, valueColor));
    }

    private void sendLine(String text, NamedTextColor color) {
        CameraMessageRouter.send(plugin, Component.text(text, color));
    }

    private static void showRankingTitle(Player player, Component title, Component subtitle) {
        player.showTitle(Title.title(
                title,
                subtitle,
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))));
    }
}
