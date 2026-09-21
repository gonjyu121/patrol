package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.Locale;
import java.util.logging.Level;

/**
 * カメラ役ごとの常設帰還地点を2枠まで保存します。
 */
public class PatrolHomeStorage {

    public static final int MIN_SLOT = 1;
    public static final int MAX_SLOT = 2;

    private final JavaPlugin plugin;
    private final File file;

    public PatrolHomeStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "patrol_homes.yml");
    }

    public boolean save(UUID playerId, int slot, Location location) {
        if (!isValidSlot(slot) || playerId == null || location == null || location.getWorld() == null) {
            return false;
        }

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("[PatrolHome] データフォルダを作成できませんでした。");
            return false;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = path(playerId, slot);
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());

        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[PatrolHome] 保存地点の書き込みに失敗しました。", e);
            return false;
        }
    }

    public boolean save(String ownerKey, UUID legacyPlayerId, int slot, Location location) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return save(legacyPlayerId, slot, location);
        }
        return saveAtPath(ownerPath(ownerKey, slot), slot, location);
    }

    public Location load(UUID playerId, int slot) {
        if (!isValidSlot(slot) || playerId == null || !file.exists()) {
            return null;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = path(playerId, slot);
        String worldName = config.getString(path + ".world");
        if (worldName == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[PatrolHome] 保存地点" + slot + "のワールドが見つかりません: " + worldName);
            return null;
        }

        return new Location(
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw"),
                (float) config.getDouble(path + ".pitch"));
    }

    public Location load(String ownerKey, UUID legacyPlayerId, int slot) {
        if (!isValidSlot(slot) || ownerKey == null || ownerKey.isBlank()) {
            return load(legacyPlayerId, slot);
        }

        Location shared = loadAtPath(ownerPath(ownerKey, slot), slot);
        if (shared != null) {
            return shared;
        }

        Location legacy = load(legacyPlayerId, slot);
        if (legacy != null) {
            save(ownerKey, legacyPlayerId, slot, legacy);
        }
        return legacy;
    }

    static String canonicalOwnerKey(String playerName, UUID playerId) {
        if (playerName != null) {
            String canonical = playerName.trim();
            if (canonical.startsWith(".")) {
                canonical = canonical.substring(1);
            }
            if (!canonical.isBlank()) {
                return canonical.toLowerCase(Locale.ROOT);
            }
        }
        return playerId == null ? null : "uuid-" + playerId;
    }

    public static boolean isValidSlot(int slot) {
        return slot >= MIN_SLOT && slot <= MAX_SLOT;
    }

    private String path(UUID playerId, int slot) {
        return "players." + playerId + ".homes." + slot;
    }

    private String ownerPath(String ownerKey, int slot) {
        return "owners." + ownerKey + ".homes." + slot;
    }

    private boolean saveAtPath(String path, int slot, Location location) {
        if (!isValidSlot(slot) || location == null || location.getWorld() == null) {
            return false;
        }
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("[PatrolHome] データフォルダを作成できませんでした。");
            return false;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[PatrolHome] 保存地点の書き込みに失敗しました。", e);
            return false;
        }
    }

    private Location loadAtPath(String path, int slot) {
        if (!isValidSlot(slot) || !file.exists()) {
            return null;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String worldName = config.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[PatrolHome] 保存地点" + slot + "のワールドが見つかりません: " + worldName);
            return null;
        }
        return new Location(world, config.getDouble(path + ".x"), config.getDouble(path + ".y"),
                config.getDouble(path + ".z"), (float) config.getDouble(path + ".yaw"),
                (float) config.getDouble(path + ".pitch"));
    }
}
