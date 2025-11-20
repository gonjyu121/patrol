package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * PlayerStatsStorage
 * プレイヤー統計情報を保存・更新するシンプルなYAMLストレージ。
 * - ログイン回数
 * - 総プレイ時間（ms）
 * - 最終ログイン/ログアウト時刻
 * - プレイヤー名の記録（ensureName）
 */
public class PlayerStatsStorage {

    private final JavaPlugin plugin;
    private final File file;
    final YamlConfiguration yaml; // 同一パッケージから参照可

    public PlayerStatsStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "player_stats.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        saveSync();
    }

    /** ログイン記録（回数+1, 名前更新, 最終ログイン時刻更新） */
    public int recordLogin(UUID playerId, String playerName) {
        if (playerId == null)
            return 0;
        String base = basePath(playerId);
        int count = yaml.getInt(base + ".loginCount", 0) + 1;
        yaml.set(base + ".loginCount", count);
        yaml.set(base + ".name", playerName);
        yaml.set(base + ".lastJoinAtMs", System.currentTimeMillis());
        saveSync();
        return count;
    }

    /** ログアウト記録（セッション時間を加算, 最終ログアウト時刻を更新） */
    public void recordQuit(UUID playerId) {
        if (playerId == null)
            return;
        String base = basePath(playerId);
        long lastJoin = yaml.getLong(base + ".lastJoinAtMs", 0L);
        if (lastJoin > 0) {
            long session = Math.max(0, System.currentTimeMillis() - lastJoin);
            long total = yaml.getLong(base + ".totalPlayMs", 0L) + session;
            yaml.set(base + ".totalPlayMs", total);
        }
        yaml.set(base + ".lastQuitAtMs", System.currentTimeMillis());
        yaml.set(base + ".lastJoinAtMs", 0L);
        saveSync();
    }

    /** 名前のみ保存（AutoEventSystem等からの呼び出し用） */
    public void ensureName(UUID playerId, String playerName) {
        if (playerId == null)
            return;
        String base = basePath(playerId);
        yaml.set(base + ".name", playerName);
        saveSync();
    }

    /** 総プレイ時間（ms）を取得 */
    public long getTotalPlayTimeMillis(UUID playerId) {
        return yaml.getLong(basePath(playerId) + ".totalPlayMs", 0L);
    }

    /** ログイン回数を取得 */
    public int getLoginCount(UUID playerId) {
        return yaml.getInt(basePath(playerId) + ".loginCount", 0);
    }

    /** 全データ保存（即同期） */
    public void saveSync() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player_stats.yml: " + e.getMessage());
        }
    }

    /** パス構築補助 */
    private String basePath(UUID playerId) {
        return "players." + playerId;
    }

    /**
     * データをフラッシュ（保存）します（saveSyncのエイリアス）。
     */
    public void flush() {
        saveSync();
    }

    /**
     * イベントポイントを追加します。
     * 
     * @param playerId プレイヤーのUUID
     * @param points   追加するポイント
     * @param reason   ポイント付与の理由
     */
    public void addEventPoint(UUID playerId, int points, String reason) {
        if (playerId == null)
            return;
        String base = basePath(playerId);
        int current = yaml.getInt(base + ".eventPoints", 0);
        yaml.set(base + ".eventPoints", current + points);
        yaml.set(base + ".lastEventReason", reason);
        saveSync();
    }
}
