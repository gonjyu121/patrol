package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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
    private boolean dirty = false;
    private BukkitTask autoSaveTask;

    public PlayerStatsStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "player_stats.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);

        // クリーンアップ：再起動時に個別の「オンライン中フラグ」をリセット
        resetOnlineStatusOnStartup();

        // データの整合性チェックと初期保存
        synchronized (yaml) {
            saveSync();
        }

        startAutoSaveTask();
    }

    private void resetOnlineStatusOnStartup() {
        synchronized (yaml) {
            if (yaml.getConfigurationSection("players") == null)
                return;
            for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
                String base = "players." + key;
                // クラッシュ等で残ったオンライン状態をフラッシュ
                yaml.set(base + ".lastJoinAtMs", 0L);
                yaml.set(base + ".lastContinuousJoinAtMs", 0L);
            }
            dirty = true;
        }
    }

    private void startAutoSaveTask() {
        // 30秒ごとにチェックして保存
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (dirty) {
                saveSync();
            }
        }, 600L, 600L);
    }

    /** ログイン記録（回数+1, 名前更新, 最終ログイン時刻更新） */
    public int recordLogin(UUID playerId, String playerName) {
        if (playerId == null)
            return 0;
        synchronized (yaml) {
            String base = basePath(playerId);
            int count = yaml.getInt(base + ".loginCount", 0) + 1;
            yaml.set(base + ".loginCount", count);
            yaml.set(base + ".name", playerName);
            long now = System.currentTimeMillis();
            yaml.set(base + ".lastJoinAtMs", now);
            yaml.set(base + ".lastContinuousJoinAtMs", now);

            dirty = true;
            return count;
        }
    }

    /** ログアウト記録（セッション時間を加算, 最終ログアウト時刻を更新） */
    public void recordQuit(UUID playerId) {
        if (playerId == null)
            return;
        synchronized (yaml) {
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

            dirty = true;
            saveSync(); // ログアウト時は重要なので即時保存
        }
    }

    /** 名前のみ保存（AutoEventSystem等からの呼び出し用） */
    public void ensureName(UUID playerId, String playerName) {
        if (playerId == null)
            return;
        synchronized (yaml) {
            String base = basePath(playerId);
            yaml.set(base + ".name", playerName);
            dirty = true;
        }
    }

    /** 総プレイ時間（ms）を取得 */
    public long getTotalPlayTimeMillis(UUID playerId) {
        synchronized (yaml) {
            String base = basePath(playerId);
            long storedTotal = yaml.getLong(base + ".totalPlayMs", 0L);
            long lastJoin = yaml.getLong(base + ".lastJoinAtMs", 0L);

            // 現在進行中のセッション時間を反映（オンラインの場合）
            long currentTotal = storedTotal;
            if (lastJoin > 0) {
                currentTotal += (System.currentTimeMillis() - lastJoin);
            }

            long survival = getContinuousSurvivalTimeMillis(playerId);

            // データの整合性補正: 連続生存時間が総プレイ時間を超えている場合、大きい方を採用
            // (ここではYAMLへの保存は行わず、計算結果のみを返す。保存は recordQuit に任せる)
            return Math.max(currentTotal, survival);
        }
    }

    /** 連続生存時間（ms）を取得 */
    public long getContinuousSurvivalTimeMillis(UUID playerId) {
        synchronized (yaml) {
            String base = basePath(playerId);
            long stored = yaml.getLong(base + ".continuousSurvivalMs", 0L);
            long lastContinuousJoin = yaml.getLong(base + ".lastContinuousJoinAtMs", 0L);

            // オンライン（lastContinuousJoinAtMs > 0）なら、現在のセッション時間を加算
            if (lastContinuousJoin > 0) {
                return stored + (System.currentTimeMillis() - lastContinuousJoin);
            }
            return stored;
        }
    }

    /** 連続生存時間をリセット（死亡時など） */
    public void resetContinuousSurvivalTime(UUID playerId) {
        if (playerId == null)
            return;
        synchronized (yaml) {
            String base = basePath(playerId);
            yaml.set(base + ".continuousSurvivalMs", 0L);
            yaml.set(base + ".lastContinuousJoinAtMs", System.currentTimeMillis());
            dirty = true;
        }
    }

    /** ログイン回数を取得 */
    public int getLoginCount(UUID playerId) {
        synchronized (yaml) {
            return yaml.getInt(basePath(playerId) + ".loginCount", 0);
        }
    }

    /** 全データ保存（即同期） */
    public void saveSync() {
        synchronized (yaml) {
            try {
                yaml.save(file);
                dirty = false;
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save player_stats.yml: " + e.getMessage());
            }
        }
    }

    /** パス構築補助 */
    private String basePath(UUID playerId) {
        return "players." + playerId;
    }

    /**
     * データをフラッシュ（保存）します。
     * プラグイン終了時に呼ばれ、自動保存タスクを停止して最終保存を行います。
     */
    public void flush() {
        // 自動保存タスクを停止
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        // 最終保存を確実に実行
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
        synchronized (yaml) {
            String base = basePath(playerId);
            int current = yaml.getInt(base + ".eventPoints", 0);
            yaml.set(base + ".eventPoints", current + points);
            yaml.set(base + ".lastEventReason", reason);
            dirty = true;
        }
    }

    /**
     * プレイヤーキル数を追加します。
     * 
     * @param playerId プレイヤーのUUID
     */
    public void addPlayerKill(UUID playerId) {
        if (playerId == null)
            return;
        synchronized (yaml) {
            String base = basePath(playerId);
            int current = yaml.getInt(base + ".playerKills", 0);
            yaml.set(base + ".playerKills", current + 1);
            dirty = true;
        }
    }

    /**
     * エンダードラゴン討伐数を追加します。
     * 
     * @param playerId プレイヤーのUUID
     */
    public void addEnderDragonKill(UUID playerId) {
        if (playerId == null)
            return;
        synchronized (yaml) {
            String base = basePath(playerId);
            int current = yaml.getInt(base + ".enderDragonKills", 0);
            yaml.set(base + ".enderDragonKills", current + 1);
            dirty = true;
            saveSync(); // 重要データなので即座に保存
        }
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
        synchronized (yaml) {
            return yaml.getInt(basePath(playerId) + ".playerKills", 0);
        }
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
        synchronized (yaml) {
            return yaml.getInt(basePath(playerId) + ".enderDragonKills", 0);
        }
    }

    /**
     * ハードモードのエンダードラゴン討伐者として記録します。
     * 
     * @param playerId プレイヤーのUUID
     * @param isSlayer 討伐者かどうか
     */
    public void setHardDragonSlayer(UUID playerId, boolean isSlayer) {
        if (playerId == null)
            return;
        synchronized (yaml) {
            String base = basePath(playerId);
            yaml.set(base + ".hasKilledHardDragon", isSlayer);
            dirty = true;
        }
    }

    /**
     * ハードモードのエンダードラゴン討伐者かどうかを確認します。
     * 
     * @param playerId プレイヤーのUUID
     * @return 討伐者ならtrue
     */
    public boolean isHardDragonSlayer(UUID playerId) {
        if (playerId == null)
            return false;
        synchronized (yaml) {
            return yaml.getBoolean(basePath(playerId) + ".hasKilledHardDragon", false);
        }
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
        synchronized (yaml) {
            return yaml.getInt(basePath(playerId) + ".eventPoints", 0);
        }
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
        synchronized (yaml) {
            return yaml.getString(basePath(playerId) + ".name", "Unknown");
        }
    }

    /**
     * 全プレイヤーの連続生存時間をリセットします。
     */
    public void resetAllContinuousSurvivalTime() {
        synchronized (yaml) {
            if (yaml.getConfigurationSection("players") == null)
                return;

            long now = System.currentTimeMillis();
            for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
                String base = "players." + key;
                yaml.set(base + ".continuousSurvivalMs", 0L);
                yaml.set(base + ".lastContinuousJoinAtMs", now);
            }
            dirty = true;
        }
    }

    /**
     * 全プレイヤーのUUIDリストを取得します。
     * 
     * @return プレイヤーUUIDのリスト
     */
    public java.util.List<UUID> getAllPlayerIds() {
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        synchronized (yaml) {
            if (yaml.getConfigurationSection("players") == null)
                return ids;
            for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
                try {
                    ids.add(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return ids;
    }

    /**
     * 最後に通知したマイルストーン時間を取得します。
     */
    public long getLastNotifiedMilestoneMs(UUID playerId) {
        if (playerId == null)
            return 0;
        synchronized (yaml) {
            return yaml.getLong(basePath(playerId) + ".lastNotifiedMilestoneMs", 0L);
        }
    }

    /**
     * 最後に通知したマイルストーン時間を設定します。
     */
    public void setLastNotifiedMilestoneMs(UUID playerId, long milestoneMs) {
        if (playerId == null)
            return;
        synchronized (yaml) {
            yaml.set(basePath(playerId) + ".lastNotifiedMilestoneMs", milestoneMs);
            dirty = true;
        }
    }

    /**
     * 現在のプレイヤーランクを取得します。
     */
    public String getPlayerRank(UUID playerId) {
        if (playerId == null)
            return "None";
        synchronized (yaml) {
            return yaml.getString(basePath(playerId) + ".currentRank", "None");
        }
    }

    /**
     * プレイヤーランクを設定します。
     */
    public void setPlayerRank(UUID playerId, String rank) {
        if (playerId == null)
            return;
        synchronized (yaml) {
            yaml.set(basePath(playerId) + ".currentRank", rank);
            dirty = true;
        }
    }
}
