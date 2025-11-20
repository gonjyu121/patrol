package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤー保護データをYAMLに保存/読み込みするユーティリティ。
 * - 保護の有効期限 (expireAtMs)
 * - 保護半径 (radius)
 * などをプレイヤー毎に保持します。
 *
 * 機能:
 * - extendProtectionDuration(UUID, long)
 * - extendProtectionRadius(UUID, int)
 * - isProtected(UUID)
 * - getRemainingMillis(UUID)
 */
public class ProtectionData {

    private final JavaPlugin plugin;
    private final File file;
    private final FileConfiguration yaml;

    // メモリキャッシュ（任意）
    private final Map<UUID, Long> expireMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> radiusMap = new ConcurrentHashMap<>();

    public ProtectionData(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "protections.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        loadFromYaml();
        saveSync(); // ファイルが無い初回でも作成されるように
    }

    // ====== 外部API ======

    /** 保護の残りミリ秒（無ければ0） */
    public long getRemainingMillis(UUID playerId) {
        long now = System.currentTimeMillis();
        Long expire = expireMap.get(playerId);
        if (expire == null)
            return 0L;
        return Math.max(0L, expire - now);
    }

    /** 現在保護中か */
    public boolean isProtected(UUID playerId) {
        return getRemainingMillis(playerId) > 0;
    }

    /** 保護の有効期限を延長（追加）する。deltaMsが負でも可だが0未満にはならないようクリップ。 */
    public void extendProtectionDuration(UUID playerId, long deltaMs) {
        long now = System.currentTimeMillis();
        long base = Math.max(now, expireMap.getOrDefault(playerId, 0L));
        long next = Math.max(now, base + deltaMs);
        expireMap.put(playerId, next);
        // YAMLへ反映
        String basePath = path(playerId);
        yaml.set(basePath + ".expireAtMs", next);
        saveSyncAsync();
    }

    /** 保護半径を拡張（加算）。負の場合は縮小。最小0でクリップ。 */
    public void extendProtectionRadius(UUID playerId, int delta) {
        int cur = Math.max(0, radiusMap.getOrDefault(playerId, 0));
        int next = Math.max(0, cur + delta);
        radiusMap.put(playerId, next);
        String basePath = path(playerId);
        yaml.set(basePath + ".radius", next);
        saveSyncAsync();
    }

    /** 現在の半径を取得（未設定なら0） */
    public int getRadius(UUID playerId) {
        return Math.max(0, radiusMap.getOrDefault(playerId, 0));
    }

    /**
     * 保護期間を延長します（extendProtectionDurationのエイリアス）。
     * 
     * @param playerId プレイヤーのUUID
     * @param deltaMs  延長するミリ秒
     */
    public void extend(UUID playerId, long deltaMs) {
        extendProtectionDuration(playerId, deltaMs);
    }

    // ====== 内部：YAMLロード/セーブ ======

    private void loadFromYaml() {
        if (!yaml.isConfigurationSection("players"))
            return;
        Set<String> keys = Objects.requireNonNull(yaml.getConfigurationSection("players")).getKeys(false);
        for (String key : keys) {
            try {
                UUID id = UUID.fromString(key);
                long expireAt = yaml.getLong("players." + key + ".expireAtMs", 0L);
                int radius = yaml.getInt("players." + key + ".radius", 0);
                if (expireAt > 0L) {
                    expireMap.put(id, expireAt);
                }
                if (radius > 0) {
                    radiusMap.put(id, radius);
                }
            } catch (IllegalArgumentException ignored) {
                // 不正なUUID文字列はスキップ
            }
        }
    }

    private void saveSync() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save protections.yml: " + e.getMessage());
        }
    }

    /** 軽負荷用：今は同期保存で十分。必要なら非同期化も可能。 */
    private void saveSyncAsync() {
        // Paper環境なら非同期にしてもよいが、簡潔に同期保存
        saveSync();
    }

    private String path(UUID id) {
        return "players." + id.toString();
    }
}
