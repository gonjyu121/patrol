package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Simple YAML-backed storage for per-player participation stats.
 * Tracks total login count and cumulative play time in milliseconds.
 */
public class PlayerStatsStorage {

    private final JavaPlugin plugin;
    private final File file;
    private final FileConfiguration yaml;

    public PlayerStatsStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "player_stats.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        saveSync(); // ensure file exists on first run
    }

    public int recordLogin(UUID playerId, String playerName) {
        String base = basePath(playerId);
        int count = yaml.getInt(base + ".loginCount", 0) + 1;
        yaml.set(base + ".name", playerName);
        yaml.set(base + ".loginCount", count);
        yaml.set(base + ".lastJoinAtMs", System.currentTimeMillis());
        return count;
    }

    public void recordQuit(UUID playerId) {
        String base = basePath(playerId);
        long lastJoin = yaml.getLong(base + ".lastJoinAtMs", 0L);
        if (lastJoin > 0) {
            long total = yaml.getLong(base + ".totalPlayTimeMs", 0L);
            long session = Math.max(0L, System.currentTimeMillis() - lastJoin);
            yaml.set(base + ".totalPlayTimeMs", total + session);
            yaml.set(base + ".lastJoinAtMs", 0L);
        }
    }

    public long getTotalPlayTimeMillis(UUID playerId) {
        return yaml.getLong(basePath(playerId) + ".totalPlayTimeMs", 0L);
    }

    public int getLoginCount(UUID playerId) {
        return yaml.getInt(basePath(playerId) + ".loginCount", 0);
    }

    public void saveSync() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player_stats.yml: " + e.getMessage());
        }
    }

    private String basePath(UUID playerId) {
        return "players." + playerId.toString();
    }
}


