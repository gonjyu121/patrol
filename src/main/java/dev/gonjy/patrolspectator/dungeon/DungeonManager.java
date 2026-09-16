package dev.gonjy.patrolspectator.dungeon;

import dev.gonjy.patrolspectator.PatrolSpectatorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class DungeonManager {
    private final PatrolSpectatorPlugin plugin;
    private final File configFile;
    private FileConfiguration config;

    private Location center;
    private boolean enabled = false;
    private boolean built = false;
    static final int DUNGEON_SIZE = 60;
    static final int FLOOR_HEIGHT = 6;

    public DungeonManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "dungeon_config.yml");
        loadConfig();
    }

    public PatrolSpectatorPlugin getPlugin() {
        return plugin;
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("dungeon_config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        String worldName = config.getString("center.world");
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = config.getDouble("center.x");
                double y = config.getDouble("center.y");
                double z = config.getDouble("center.z");
                center = new Location(world, x, y, z);
            }
        }
        enabled = config.getBoolean("enabled", false);
        built = config.getBoolean("built", false);
    }

    public void saveConfig() {
        if (center != null) {
            config.set("center.world", center.getWorld().getName());
            config.set("center.x", center.getX());
            config.set("center.y", center.getY());
            config.set("center.z", center.getZ());
        }
        config.set("enabled", enabled);
        config.set("built", built);
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save dungeon_config.yml!");
        }
    }

    public void setCenter(Location loc) {
        this.center = loc;
        saveConfig();
    }

    public Location getCenter() {
        return center;
    }

    public boolean isEnabled() {
        return enabled && center != null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        saveConfig();
    }

    public boolean isBuilt() {
        return built && config.getInt("floors.builtCount", 1) >= getFloorCount();
    }

    public void setBuilt(boolean built) {
        this.built = built;
        if (built) {
            config.set("floors.builtCount", getFloorCount());
        }
        saveConfig();
    }

    /**
     * 指定ロケーションが迷宮の範囲内か判定
     */
    public boolean isInDungeon(Location loc) {
        if (!isEnabled())
            return false;
        if (!loc.getWorld().equals(center.getWorld()))
            return false;

        double half = DUNGEON_SIZE / 2.0;
        return loc.getX() >= center.getX() - half && loc.getX() <= center.getX() + half &&
                loc.getZ() >= center.getZ() - half && loc.getZ() <= center.getZ() + half &&
                loc.getY() >= getDeepestBaseY() - 1 && loc.getY() <= center.getY() + 4;
    }

    public int getFloorCount() {
        if (center == null || center.getWorld() == null)
            return 1;
        int configuredLimit = Math.max(1, config.getInt("floors.max", 100));
        return calculateFloorCount(center.getBlockY(), center.getWorld().getMinHeight(), configuredLimit);
    }

    static int calculateFloorCount(int baseY, int minHeight, int configuredLimit) {
        int safetyBottomY = minHeight + 2;
        int possible = Math.floorDiv(baseY - safetyBottomY, FLOOR_HEIGHT) + 1;
        return Math.max(1, Math.min(possible, Math.max(1, configuredLimit)));
    }

    public int getFloorBaseY(int floor) {
        int normalized = Math.max(1, Math.min(floor, getFloorCount()));
        return center.getBlockY() - ((normalized - 1) * FLOOR_HEIGHT);
    }

    public int getFloor(Location location) {
        if (!isInDungeon(location))
            return 0;
        int floor = Math.floorDiv(center.getBlockY() - location.getBlockY() + 1, FLOOR_HEIGHT) + 1;
        return Math.max(1, Math.min(floor, getFloorCount()));
    }

    private int getDeepestBaseY() {
        return getFloorBaseY(getFloorCount());
    }

    /**
     * 迷宮範囲内の安全スキャン（既存建造物の保護）
     * 岩盤、空気、石、土、砂利 以外のブロックがあれば「建造物あり」とみなす
     */
    public boolean scanForSafety(StringBuilder report) {
        if (center == null)
            return false;
        World world = center.getWorld();
        int minX = center.getBlockX() - (DUNGEON_SIZE / 2);
        int maxX = center.getBlockX() + (DUNGEON_SIZE / 2);
        int minZ = center.getBlockZ() - (DUNGEON_SIZE / 2);
        int maxZ = center.getBlockZ() + (DUNGEON_SIZE / 2);
        // 同期スキャンで全深度を走査するとサーバーを停止させるため、入口階のみ確認する。
        // 下層は現在の迷宮と同じXZ範囲内へ分割生成する。
        int minY = center.getBlockY() - 2;
        int maxY = center.getBlockY() + 5;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    Material type = b.getType();
                    if (isSignificantBlock(type)) {
                        report.append("§c警告: 既存ブロックを発見: ").append(type.name())
                                .append(" at ").append(x).append(", ").append(y).append(", ").append(z).append("\n");
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isSignificantBlock(Material m) {
        if (m == Material.AIR || m == Material.CAVE_AIR || m == Material.BEDROCK ||
                m == Material.STONE || m == Material.DIRT || m == Material.GRASS_BLOCK ||
                m == Material.GRAVEL || m == Material.DEEPSLATE || m == Material.COBBLESTONE) {
            return false;
        }
        return true; // 看板、チェスト、人工的なブロックなど
    }
}
