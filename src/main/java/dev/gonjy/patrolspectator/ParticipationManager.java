package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 参加回数＋ランキング統合管理
 */
public class ParticipationManager {
    private final PatrolSpectatorPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private volatile boolean dirty = false;
    private BukkitTask autoSaveTask;

    public ParticipationManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "participation.yml");
        this.yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        startAutoSaveTask();
    }

    /**
     * PlayerStatsStorage を受け取るコンストラクタ（互換性のため）。
     */
    public ParticipationManager(PatrolSpectatorPlugin plugin, PlayerStatsStorage statsStorage) {
        this(plugin);
    }

    private void startAutoSaveTask() {
        // 1分ごとにチェックして保存
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (dirty) {
                saveSync();
            }
        }, 1200L, 1200L);
    }

    private String base(UUID id) {
        return "players." + id;
    }

    /**
     * 同期的に保存します（内部用、またはプラグイン終了時用）。
     */
    public void saveSync() {
        synchronized (yaml) {
            if (!dirty && !plugin.isEnabled()) {
                // シャットダウン時以外で dirty でないならスキップ
                // ただし flush 的な意味で常に保存したい場合は dirty チェックのみ
            }
            try {
                yaml.save(file);
                dirty = false;
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save participation.yml: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (dirty) {
            saveSync();
        }
    }

    public int incrementJoinCount(UUID id, String name) {
        synchronized (yaml) {
            String k = base(id) + ".count";
            int c = yaml.getInt(k, 0) + 1;
            yaml.set(k, c);
            yaml.set(base(id) + ".name", name);
            dirty = true;
            return c;
        }
    }

    public void addPoints(UUID id, String name, int pts, String reason) {
        if (pts == 0)
            return;
        synchronized (yaml) {
            String k = base(id) + ".score";
            int c = yaml.getInt(k, 0) + pts;
            yaml.set(k, c);
            yaml.set(base(id) + ".name", name);
            yaml.set(base(id) + ".lastReason", reason);
            dirty = true;
        }
    }

    public List<Entry> topN(int n) {
        synchronized (yaml) {
            var s = yaml.getConfigurationSection("players");
            if (s == null)
                return Collections.emptyList();
            return s.getKeys(false).stream().map(id -> {
                String b = "players." + id;
                return new Entry(UUID.fromString(id),
                        yaml.getString(b + ".name", id),
                        yaml.getInt(b + ".score", 0),
                        yaml.getInt(b + ".count", 0));
            }).sorted(Comparator.comparingInt(Entry::score).reversed()).limit(n).collect(Collectors.toList());
        }
    }

    public record Entry(UUID id, String name, int score, int count) {
    }

    /**
     * プレイヤーが観戦された（映った）ことを記録します。
     */
    public void noteParticipation(UUID uuid, String name) {
        if (uuid == null || name == null)
            return;

        // 参加回数をインクリメント
        incrementJoinCount(uuid, name);

        // ポイント付与（設定で有効な場合）
        int pts = plugin.getConfig().getInt("patrol.participation.points", 1);
        if (pts > 0) {
            addPoints(uuid, name, pts, "Now On Air");
        }
    }
}
