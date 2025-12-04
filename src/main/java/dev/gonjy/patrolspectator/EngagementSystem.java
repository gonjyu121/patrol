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
        run("gamerule doDaylightCycle true");
        run("gamerule keepInventory false");
        // Bedrock環境の公平性維持（いずれか片方のみ有効な環境あり）
        run("gamerule locatorBar false");
        run("gamerule showCoordinates false");
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
        // Bedrock環境の公平性維持
        runQuietly("gamerule locatorBar false");
        runQuietly("gamerule showCoordinates false");
        // 基本ルールの強制
        runQuietly("gamerule doDaylightCycle true");
        runQuietly("gamerule keepInventory false");
        // 一人寝たら朝にする
        runQuietly("gamerule playersSleepingPercentage 0");
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
            if (p.getLocation().distanceSquared(camLoc) <= radiusSq) {
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

        log.info("[Debug] 観戦対象が見つかりませんでした。現在のプレイヤー状態:");
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
