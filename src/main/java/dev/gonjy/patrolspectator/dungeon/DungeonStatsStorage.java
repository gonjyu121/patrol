package dev.gonjy.patrolspectator.dungeon;

import dev.gonjy.patrolspectator.PatrolSpectatorPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DungeonStatsStorage {
    private final PatrolSpectatorPlugin plugin;
    private final File statsFile;
    private FileConfiguration stats;

    public DungeonStatsStorage(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "dungeon_stats.yml");
        loadStats();
    }

    private void loadStats() {
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create dungeon_stats.yml!");
            }
        }
        stats = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void saveStats() {
        try {
            stats.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save dungeon_stats.yml!");
        }
    }

    public long getGlobalDeathCount() {
        return stats.getLong("global.deaths", 0);
    }

    public void incrementGlobalDeathCount() {
        stats.set("global.deaths", getGlobalDeathCount() + 1);
        saveStats();
    }

    public int getMaxLevel(UUID uuid) {
        return stats.getInt("players." + uuid.toString() + ".max_level", 0);
    }

    public void updateMaxLevel(UUID uuid, int level, String name) {
        int current = getMaxLevel(uuid);
        if (level > current) {
            stats.set("players." + uuid.toString() + ".max_level", level);
            stats.set("players." + uuid.toString() + ".name", name);
            stats.set("players." + uuid.toString() + ".at", System.currentTimeMillis());
            saveStats();
        }
    }

    public java.util.Map<UUID, Integer> getAllPlayerLevels() {
        java.util.Map<UUID, Integer> map = new java.util.HashMap<>();
        if (stats.getConfigurationSection("players") == null)
            return map;
        for (String key : stats.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int level = stats.getInt("players." + key + ".max_level", 0);
                if (level > 0) {
                    map.put(uuid, level);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return map;
    }
}
