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
        long now = System.currentTimeMillis();
        yaml.set(base + ".lastJoinAtMs", now);
        // 連続生存時間用の開始時刻も更新（前回の続きとして扱うため、リセットはしない）
        // ただし、もし前回のログアウト処理が正常に行われていれば、lastContinuousJoinAtMs は 0 になっているはず？
        // いや、セッションまたぎで継続させたいので、ログイン時に「現在の時刻」をセットして、
        // 取得時に (stored + (now - lastContinuous)) で計算する。
        yaml.set(base + ".lastContinuousJoinAtMs", now);

        saveSync();
        return count;
    }

    /** ログアウト記録（セッション時間を加算, 最終ログアウト時刻を更新） */
    public void recordQuit(UUID playerId) {
        if (playerId == null)
            return;
        String base = basePath(playerId);
        long now = System.currentTimeMillis();

        // 累計プレイ時間の更新
        long lastJoin = yaml.getLong(base + ".lastJoinAtMs", 0L);
        if (lastJoin > 0) {
            long session = Math.max(0, now - lastJoin);
            long total = yaml.getLong(base + ".totalPlayMs", 0L) + session;
            yaml.set(base + ".totalPlayMs", total);
        }

        // 連続生存時間の更新
        long lastContinuousJoin = yaml.getLong(base + ".lastContinuousJoinAtMs", 0L);
        if (lastContinuousJoin > 0) {
            long session = Math.max(0, now - lastContinuousJoin);
            long currentContinuous = yaml.getLong(base + ".continuousSurvivalMs", 0L) + session;
            yaml.set(base + ".continuousSurvivalMs", currentContinuous);
        }

        yaml.set(base + ".lastQuitAtMs", now);
        yaml.set(base + ".lastJoinAtMs", 0L);
        yaml.set(base + ".lastContinuousJoinAtMs", 0L);
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

    /** 連続生存時間（ms）を取得 */
    public long getContinuousSurvivalTimeMillis(UUID playerId) {
        String base = basePath(playerId);
        long stored = yaml.getLong(base + ".continuousSurvivalMs", 0L);
        long lastContinuousJoin = yaml.getLong(base + ".lastContinuousJoinAtMs", 0L);

        // オンライン（lastContinuousJoinAtMs > 0）なら、現在のセッション時間を加算
        if (lastContinuousJoin > 0) {
            return stored + (System.currentTimeMillis() - lastContinuousJoin);
        }
        return stored;
    }

    /** 連続生存時間をリセット（死亡時など） */
    public void resetContinuousSurvivalTime(UUID playerId) {
        if (playerId == null)
            return;
        String base = basePath(playerId);
        yaml.set(base + ".continuousSurvivalMs", 0L);
        // 現在のセッション開始時刻を「今」にリセットすることで、
        // 次回の計算時に (now - now) = 0 から再スタートさせる
        yaml.set(base + ".lastContinuousJoinAtMs", System.currentTimeMillis());
        saveSync();
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

    /**
     * プレイヤーキル数を追加します。
     * 
     * @param playerId プレイヤーのUUID
     */
    public void addPlayerKill(UUID playerId) {
        if (playerId == null)
            return;
        String base = basePath(playerId);
        int current = yaml.getInt(base + ".playerKills", 0);
        yaml.set(base + ".playerKills", current + 1);
        saveSync();
    }

    /**
     * エンダードラゴン討伐数を追加します。
     * 
     * @param playerId プレイヤーのUUID
     */
    public void addEnderDragonKill(UUID playerId) {
        if (playerId == null)
            return;
        String base = basePath(playerId);
        int current = yaml.getInt(base + ".enderDragonKills", 0);
        yaml.set(base + ".enderDragonKills", current + 1);
        saveSync();
    }

    /**
     * プレイヤーキル数を取得します。
     * 
     * @param playerId プレイヤーのUUID
     * @return プレイヤーキル数
     */
    public int getPlayerKills(UUID playerId) {
        if (playerId == null)
            return 0;
        return yaml.getInt(basePath(playerId) + ".playerKills", 0);
    }

    /**
     * エンダードラゴン討伐数を取得します。
     * 
     * @param playerId プレイヤーのUUID
     * @return エンダードラゴン討伐数
     */
    public int getEnderDragonKills(UUID playerId) {
        if (playerId == null)
            return 0;
        return yaml.getInt(basePath(playerId) + ".enderDragonKills", 0);
    }

    /**
     * イベントポイントを取得します。
     * 
     * @param playerId プレイヤーのUUID
     * @return イベントポイント
     */
    public int getEventPoints(UUID playerId) {
        if (playerId == null)
            return 0;
        return yaml.getInt(basePath(playerId) + ".eventPoints", 0);
    }

    /**
     * プレイヤー名を取得します。
     * 
     * @param playerId プレイヤーのUUID
     * @return プレイヤー名（存在しない場合は "Unknown"）
     */
    public String getPlayerName(UUID playerId) {
        if (playerId == null)
            return "Unknown";
        return yaml.getString(basePath(playerId) + ".name", "Unknown");
    }

    /**
     * 全プレイヤーのUUIDリストを取得します。
     * 
     * @return プレイヤーUUIDのリスト
     */
    public java.util.List<UUID> getAllPlayerIds() {
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        if (yaml.getConfigurationSection("players") == null)
            return ids;
        for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
            try {
                ids.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return ids;
    }
}
