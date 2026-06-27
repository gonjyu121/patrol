package dev.gonjy.patrolspectator;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.logging.Logger;

public final class EngagementSystem {
    private final PatrolSpectatorPlugin plugin;
    private final Logger log;

    public EngagementSystem(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /** 起動時や必要時に呼ぶ：公平性維持のためHUDを抑制 */
    public void applyServerRules() {
        // 標準ルールはAPI経由で設定（チャットログが出ないようにするため）
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            // ワールドをハードモードに設定
            if (world.getDifficulty() != org.bukkit.Difficulty.HARD) {
                world.setDifficulty(org.bukkit.Difficulty.HARD);
                log.info("[Rules] set difficulty to HARD in " + world.getName());
            }

            setDynamicRule(world, new String[]{"doDaylightCycle", "do_daylight_cycle", "minecraft:do_daylight_cycle"}, true);
            setDynamicRule(world, new String[]{"keepInventory", "keep_inventory", "minecraft:keep_inventory"}, false);

            // Bedrock/Java 1.21.11+ 用のルールをAPIで安全に適用
            // API で認識されない場合はコマンドフォールバックで確実に設定
            setDynamicRuleWithFallback(world, new String[] { "locator_bar", "minecraft:locator_bar", "locatorBar" }, false, "locator_bar");
            setDynamicRuleWithFallback(world, new String[] { "show_coordinates", "minecraft:show_coordinates", "showCoordinates" }, false, "showCoordinates");
        }
    }


    /** サーバーバージョンによって名称が異なる可能性があるルールを、候補リストから安全に設定 */
    private <T> void setDynamicRule(org.bukkit.World world, String[] candidates, T value) {
        for (String name : candidates) {
            try {
                org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName(name);
                if (rule != null) {
                    Object current = world.getGameRuleValue(rule);
                    if (current != null && current.equals(value)) {
                        return; // すでに設定済みならスキップ
                    }
                    @SuppressWarnings("unchecked")
                    org.bukkit.GameRule<T> tRule = (org.bukkit.GameRule<T>) rule;
                    world.setGameRule(tRule, value);
                    log.info("[Rules] applied (Dynamic API): " + name + " = " + value + " in " + world.getName());
                    return; // 適用できたら終了
                }
            } catch (Throwable ignored) {
                // 次の候補へ
            }
        }
    }

    /** 定期実行用：ログを出さずにルールを適用 */
    public void applyServerRulesQuietly() {
        // 基本ルールの強制（API経由）
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            try {
                // ワールドをハードモードに強制
                if (world.getDifficulty() != org.bukkit.Difficulty.HARD) {
                    world.setDifficulty(org.bukkit.Difficulty.HARD);
                }

                // 変更が必要な場合のみセットする
                setDynamicRuleIfNotMatch(world, new String[]{"doDaylightCycle", "do_daylight_cycle", "minecraft:do_daylight_cycle"}, true);
                setDynamicRuleIfNotMatch(world, new String[]{"keepInventory", "keep_inventory", "minecraft:keep_inventory"}, false);
                setDynamicRuleIfNotMatch(world, new String[]{"playersSleepingPercentage", "players_sleeping_percentage", "minecraft:players_sleeping_percentage"}, 0);

                // 動的な設定（ログなし）。API で認識されない場合はコマンドで設定
                setDynamicRuleWithFallbackQuiet(world, new String[] { "locator_bar", "minecraft:locator_bar", "locatorBar" }, false, "locator_bar");
                setDynamicRuleWithFallbackQuiet(world, new String[] { "show_coordinates", "minecraft:show_coordinates", "showCoordinates" }, false, "showCoordinates");

            } catch (Throwable ignored) {
            }
        }
    }


    private <T> void setDynamicRuleIfNotMatch(org.bukkit.World world, String[] candidates, T value) {
        for (String name : candidates) {
            try {
                org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName(name);
                if (rule != null) {
                    Object current = world.getGameRuleValue(rule);
                    if (current != null && current.equals(value)) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    org.bukkit.GameRule<T> tRule = (org.bukkit.GameRule<T>) rule;
                    world.setGameRule(tRule, value);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * API で GameRule が認識されない場合に /gamerule コマンドで確実に設定するメソッド（ログあり）。
     * commandRuleName: コマンドで使用するルール名（例: "locator_bar"）
     */
    private <T> void setDynamicRuleWithFallback(org.bukkit.World world, String[] candidates, T value, String commandRuleName) {
        for (String name : candidates) {
            try {
                org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName(name);
                if (rule != null) {
                    Object current = world.getGameRuleValue(rule);
                    if (current != null && current.equals(value)) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    org.bukkit.GameRule<T> tRule = (org.bukkit.GameRule<T>) rule;
                    world.setGameRule(tRule, value);
                    log.info("[Rules] applied (Dynamic API): " + name + " = " + value + " in " + world.getName());
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        // API で認識できなかった場合はコマンドフォールバック
        try {
            String cmd = "gamerule " + commandRuleName + " " + value;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            log.info("[Rules] applied (command fallback): " + cmd + " in " + world.getName());
        } catch (Throwable t) {
            log.warning("[Rules] failed to apply " + commandRuleName + ": " + t.getMessage());
        }
    }

    /**
     * API で GameRule が認識されない場合に /gamerule コマンドで確実に設定するメソッド（ログなし）。
     */
    private <T> void setDynamicRuleWithFallbackQuiet(org.bukkit.World world, String[] candidates, T value, String commandRuleName) {
        for (String name : candidates) {
            try {
                org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName(name);
                if (rule != null) {
                    Object current = world.getGameRuleValue(rule);
                    if (current != null && current.equals(value)) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    org.bukkit.GameRule<T> tRule = (org.bukkit.GameRule<T>) rule;
                    world.setGameRule(tRule, value);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        // API で認識できなかった場合はコマンドフォールバック
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule " + commandRuleName + " " + value);
        } catch (Throwable ignored) {
        }
    }

    /**
     * カメラ役の近くにいる「観戦対象として適切なプレイヤー」を探します。
     * <p>
     * 条件:
     * - カメラ役本人は除外
     * - 指定された半径内にいる
     * - サバイバルモードである（スペクテイターは除外）
     * 
     * @param camera カメラ役プレイヤー
     * @param radius 検索半径（ブロック単位）
     * @return 観戦対象プレイヤー、見つからない場合は null
     */
    public org.bukkit.entity.Player findGoodTargetNear(org.bukkit.entity.Player camera, double radius) {
        if (camera == null || !camera.isOnline())
            return null;

        org.bukkit.Location camLoc = camera.getLocation();
        double radiusSq = radius * radius;

        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            // カメラ本人は除外
            if (p.getUniqueId().equals(camera.getUniqueId()))
                continue;

            // サバイバルまたはアドベンチャーモードのみ対象
            if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL && p.getGameMode() != org.bukkit.GameMode.ADVENTURE)
                continue;

            // 半径内チェック
            if (p.getWorld().equals(camLoc.getWorld()) && p.getLocation().distanceSquared(camLoc) <= radiusSq) {
                return p;
            }
        }
        return null;
    }

    /**
     * 全ワールドから「観戦対象として適切なプレイヤー」を探します。
     * <p>
     * 条件:
     * - カメラ役本人は除外
     * - サバイバルモードである
     * 
     * @param camera カメラ役プレイヤー
     * @return 観戦対象プレイヤー、見つからない場合は null
     */
    public org.bukkit.entity.Player findGoodTargetGlobal(org.bukkit.entity.Player camera) {
        if (camera == null || !camera.isOnline())
            return null;

        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            // カメラ本人は除外
            if (p.getUniqueId().equals(camera.getUniqueId()))
                continue;

            // サバイバルまたはアドベンチャーモードのみ対象
            if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL && p.getGameMode() != org.bukkit.GameMode.ADVENTURE)
                continue;

            return p;
        }

        // デバッグログ出力（ターゲットが見つからなかった場合のみ、1分に1回）
        logDebugInfoIfNeeded(camera);

        return null;
    }

    /**
     * 観戦対象として有効な全プレイヤーのリストを返します。
     * 
     * @param camera カメラ役プレイヤー
     * @return 有効なプレイヤーのリスト
     */
    public java.util.List<org.bukkit.entity.Player> getValidTargets(org.bukkit.entity.Player camera) {
        java.util.List<org.bukkit.entity.Player> targets = new java.util.ArrayList<>();
        if (camera == null || !camera.isOnline())
            return targets;

        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            // カメラ本人は除外
            if (p.getUniqueId().equals(camera.getUniqueId()))
                continue;

            // サバイバルまたはアドベンチャーモードのみ対象
            if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL && p.getGameMode() != org.bukkit.GameMode.ADVENTURE)
                continue;

            targets.add(p);
        }
        return targets;
    }

    private long lastDebugLogTime = 0;

    private void logDebugInfoIfNeeded(org.bukkit.entity.Player camera) {
        long now = System.currentTimeMillis();
        if (now - lastDebugLogTime < 60000) { // 1分間隔
            return;
        }
        lastDebugLogTime = now;

        log.info("[Debug] 参加者が見つかりませんでした。現在のプレイヤー状態:");
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            StringBuilder sb = new StringBuilder();
            sb.append("- ").append(p.getName()).append(": ");

            if (p.getUniqueId().equals(camera.getUniqueId())) {
                sb.append("SKIP(Camera)");
            } else {
                sb.append("GM=").append(p.getGameMode());
                if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL
                        && p.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
                    sb.append("(対象外)");
                } else {
                    sb.append("(対象)");
                }
            }
            log.info(sb.toString());
        }
    }

    /**
     * 指定されたプレイヤーに音を再生します。
     * <p>
     * 音の種類は文字列で指定し、Bukkit の Sound enum に変換を試みます。
     * 変換に失敗した場合は何もしません。
     * 
     * @param player    音を再生する対象プレイヤー
     * @param soundName 音の名前（例: "UI_TOAST_CHALLENGE_COMPLETE"）
     * @param volume    音量
     * @param pitch     ピッチ
     */
    public void playNamedSound(org.bukkit.entity.Player player, String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isEmpty())
            return;

        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            log.warning("無効な音名: " + soundName);
        } catch (Throwable t) {
            log.warning("音の再生に失敗: " + t.getMessage());
        }
    }

    /**
     * マイルストーンとランクのチェックを行います。
     */
    public void checkEngagement(Player player) {
        if (player == null || !player.isOnline())
            return;
        checkMilestones(player);
        updatePlayerRank(player);
    }

    private void checkMilestones(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatsStorage stats = plugin.getStatsStorage();
        long totalMs = stats.getTotalPlayTimeMillis(uuid);
        long lastNotified = stats.getLastNotifiedMilestoneMs(uuid);

        // Milestones: 1h, 5h, 10h, 24h, 50h, 100h
        long[] milestones = {
                1 * 3600000L,
                5 * 3600000L,
                10 * 3600000L,
                24 * 3600000L,
                50 * 3600000L,
                100 * 3600000L
        };

        for (long m : milestones) {
            if (totalMs >= m && lastNotified < m) {
                notifyMilestone(player, m);
                stats.setLastNotifiedMilestoneMs(uuid, m);
                break; // 複数を同時に通知しないように1つずつ
            }
        }
    }

    private void notifyMilestone(Player player, long milestoneMs) {
        long hours = milestoneMs / 3600000L;
        String message = ChatColor.GOLD + "🎉 素晴らしい！プレイ時間が " 
                + ChatColor.YELLOW + hours + "時間" 
                + ChatColor.GOLD + " を突破しました！";

        player.sendMessage(message);
        // Title通知の追加
        player.sendTitle(
                org.bukkit.ChatColor.GOLD + "🎉 Milestone Reached! 🎉",
                org.bukkit.ChatColor.YELLOW + "累計プレイ時間 " + hours + "時間突破！",
                10, 70, 20);

        playNamedSound(player, "UI_TOAST_CHALLENGE_COMPLETE", 1.0f, 1.0f);

        DiscordWebhookClient discord = plugin.getDiscordWebhookClient();
        if (discord != null) {
            discord.send("🎉 **" + player.getName() + "** さんが累計プレイ時間 **" + hours + "時間** を突破しました！");
        }
    }

    private void updatePlayerRank(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatsStorage stats = plugin.getStatsStorage();
        long totalMs = stats.getTotalPlayTimeMillis(uuid);
        int points = stats.getEventPoints(uuid);
        String currentRank = stats.getPlayerRank(uuid);

        String newRank = "None";
        if (totalMs >= 100 * 3600000L) {
            newRank = "Legend";
        } else if (totalMs >= 50 * 3600000L && points >= 2000) {
            newRank = "Gold";
        } else if (totalMs >= 10 * 3600000L && points >= 500) {
            newRank = "Silver";
        } else if (totalMs >= 1 * 3600000L || points >= 100) {
            newRank = "Bronze";
        }

        if (!newRank.equals(currentRank) && !newRank.equals("None")) {
            // ランクアップ時のみ通知（ランクダウンはサイレントまたは考慮外）
            if (isRankBetter(newRank, currentRank)) {
                notifyRankUp(player, newRank);
            }
            stats.setPlayerRank(uuid, newRank);
        }
    }

    private boolean isRankBetter(String newRank, String oldRank) {
        java.util.List<String> order = java.util.Arrays.asList("None", "Bronze", "Silver", "Gold", "Legend");
        return order.indexOf(newRank) > order.indexOf(oldRank);
    }

    private void notifyRankUp(Player player, String rank) {
        org.bukkit.ChatColor color = getLegacyRankColor(rank);

        Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " さんがランク " 
                + color + rank 
                + ChatColor.YELLOW + " になりました！");

        player.sendMessage(ChatColor.GOLD + "✨ ランクアップ！ ✨ 新しいランク: " + color + rank);

        // Title通知の追加
        player.sendTitle(
                org.bukkit.ChatColor.GOLD + "✨ RANK UP! ✨",
                org.bukkit.ChatColor.YELLOW + "新ランク: " + getLegacyRankColor(rank) + rank,
                10, 80, 20);

        playNamedSound(player, "ENTITY_PLAYER_LEVELUP", 1.0f, 0.8f);

        DiscordWebhookClient discord = plugin.getDiscordWebhookClient();
        if (discord != null) {
            discord.send("✨ **" + player.getName() + "** さんがランク **" + rank + "** にランクアップしました！");
        }
    }


    private org.bukkit.ChatColor getLegacyRankColor(String rank) {
        switch (rank) {
            case "Bronze":
                return org.bukkit.ChatColor.GOLD;
            case "Silver":
                return org.bukkit.ChatColor.GRAY;
            case "Gold":
                return org.bukkit.ChatColor.YELLOW;
            case "Legend":
                return org.bukkit.ChatColor.LIGHT_PURPLE;
            default:
                return org.bukkit.ChatColor.WHITE;
        }
    }

    /**
     * 次のランクまでの進捗情報を取得
     */
    public String getRankProgressMessage(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStatsStorage stats = plugin.getStatsStorage();
        long totalMs = stats.getTotalPlayTimeMillis(uuid);
        int points = stats.getEventPoints(uuid);
        String currentRank = stats.getPlayerRank(uuid);

        String nextRank;
        String requirement;
        double progress = 0;

        switch (currentRank) {
            case "None":
            case "Bronze":
                nextRank = "Silver";
                requirement = "10時間 & 500pt";
                double timeProg = Math.min(1.0, (double) totalMs / (10 * 3600000L));
                double pointProg = Math.min(1.0, (double) points / 500.0);
                progress = (timeProg + pointProg) / 2.0;
                break;
            case "Silver":
                nextRank = "Gold";
                requirement = "50時間 & 2000pt";
                timeProg = Math.min(1.0, (double) totalMs / (50 * 3600000L));
                pointProg = Math.min(1.0, (double) points / 2000.0);
                progress = (timeProg + pointProg) / 2.0;
                break;
            case "Gold":
                nextRank = "Legend";
                requirement = "100時間";
                progress = Math.min(1.0, (double) totalMs / (100 * 3600000L));
                break;
            case "Legend":
                return "§bあなたは伝説のプレイヤーです！";
            default:
                return "§cデータの取得に失敗しました。";
        }

        int percent = (int) (progress * 100);
        StringBuilder bar = new StringBuilder("§7[");
        int filled = percent / 10;
        for (int i = 0; i < 10; i++) {
            if (i < filled)
                bar.append("§a■");
            else
                bar.append("§8□");
        }
        bar.append("§7]");

        return "§f次のランク: " + getLegacyRankColor(nextRank) + nextRank + " §7(" + requirement + ")\n" +
                bar.toString() + " §e" + percent + "%";
    }
}
