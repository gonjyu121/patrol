package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import java.util.logging.Logger;

public final class EngagementSystem {
    private final Logger log;

    public EngagementSystem(PatrolSpectatorPlugin plugin) {
        this.log = plugin.getLogger();
    }

    /** 起動時や必要時に呼ぶ：公平性維持のためHUDを抑制 */
    public void applyServerRules() {
        // 標準ルールはAPI経由で設定（チャットログが出ないようにするため）
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            setRule(world, org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
            setRule(world, org.bukkit.GameRule.KEEP_INVENTORY, false);

            // Bedrock/Java 1.21.11+ 用のルールをAPIで安全に適用
            // 1.21.11からはスネークケース（locator_bar）に変更された可能性があるため、複数候補を試す
            setDynamicRule(world, new String[] { "locator_bar", "minecraft:locator_bar", "locatorBar" }, false);
            setDynamicRule(world, new String[] { "show_coordinates", "minecraft:show_coordinates", "showCoordinates" },
                    false);
        }
    }

    private <T> void setRule(org.bukkit.World world, org.bukkit.GameRule<T> rule, T value) {
        try {
            T current = world.getGameRuleValue(rule);
            if (current != null && current.equals(value)) {
                return; // すでに設定済みならスキップ
            }
            if (world.setGameRule(rule, value)) {
                log.info("[Rules] applied (API): " + rule.getName() + " = " + value + " in " + world.getName());
            }
        } catch (Throwable t) {
            log.warning("[Rules] failed (API): " + rule.getName() + " (" + t.getMessage() + ")");
        }
    }

    /** サーバーバージョンによって名称が異なる可能性があるルールを、候補リストから安全に設定 */
    private void setDynamicRule(org.bukkit.World world, String[] candidates, boolean value) {
        for (String name : candidates) {
            try {
                org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName(name);
                if (rule != null) {
                    // 型安全のためBooleanとして取得・比較
                    Object current = world.getGameRuleValue(rule);
                    if (Boolean.valueOf(value).equals(current)) {
                        return; // すでに設定済みならスキップ
                    }
                    world.setGameRule((org.bukkit.GameRule<Boolean>) rule, value);
                    log.info("[Rules] applied (Dynamic API): " + name + " = " + value + " in " + world.getName());
                    return; // 適用できたら終了
                }
            } catch (Throwable ignored) {
                // 次の候補へ
            }
        }
    }

    private void run(String cmd) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            log.info("[Rules] applied: " + cmd);
        } catch (Throwable t) {
            log.warning("[Rules] failed: " + cmd + " (" + t.getMessage() + ")");
        }
    }

    private void runQuietly(String cmd) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            // ログ出力なし
        } catch (Throwable ignored) {
            // エラーも無視
        }
    }

    /** 定期実行用：ログを出さずにルールを適用 */
    public void applyServerRulesQuietly() {
        // 基本ルールの強制（API経由）
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            try {
                world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
                world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, false);
                world.setGameRule(org.bukkit.GameRule.PLAYERS_SLEEPING_PERCENTAGE, 0);

                // 動的な設定（ログなし）
                setDynamicRuleQuietly(world, new String[] { "locator_bar", "minecraft:locator_bar", "locatorBar" },
                        false);
                setDynamicRuleQuietly(world,
                        new String[] { "show_coordinates", "minecraft:show_coordinates", "showCoordinates" }, false);

            } catch (Throwable ignored) {
            }
        }
    }

    private void setDynamicRuleQuietly(org.bukkit.World world, String[] candidates, boolean value) {
        for (String name : candidates) {
            try {
                org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName(name);
                if (rule != null) {
                    world.setGameRule((org.bukkit.GameRule<Boolean>) rule, value);
                    return;
                }
            } catch (Throwable ignored) {
            }
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
}
