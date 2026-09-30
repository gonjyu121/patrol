package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks only player-caused spawn hazards and removes them after the area becomes empty. */
final class SpawnHazardCleanupManager implements Listener {
    static final int DEFAULT_RADIUS_CHUNKS = 4;
    static final long DEFAULT_GRACE_MILLIS = 60L * 60L * 1000L;
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 60L * 5L;
    private static final long SAVE_INTERVAL_TICKS = 20L * 60L;

    private final PatrolSpectatorPlugin plugin;
    private final File storageFile;
    private final Map<BlockKey, TrackedBlock> blocks = new HashMap<>();
    private final Map<UUID, TrackedEntity> entities = new HashMap<>();
    private BukkitTask cleanupTask;
    private BukkitTask saveTask;
    private boolean dirty;

    SpawnHazardCleanupManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "spawn_hazards.yml");
    }

    void start() {
        load();
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runCleanup,
                CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty,
                SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    void shutdown() {
        if (cleanupTask != null) cleanupTask.cancel();
        if (saveTask != null) saveTask.cancel();
        saveIfDirty();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!isEnabled()) return;
        if (event.getBucket() != Material.LAVA_BUCKET) return;
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!isInManagedArea(target.getLocation())) return;
        BlockData original = target.getBlockData().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.getType() == Material.LAVA) trackBlock(target, HazardKind.LAVA, original);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isEnabled()) return;
        HazardKind kind = switch (event.getBlockPlaced().getType()) {
            case TNT -> HazardKind.TNT;
            case FIRE, SOUL_FIRE -> HazardKind.FIRE;
            default -> null;
        };
        if (kind == null || !isInManagedArea(event.getBlockPlaced().getLocation())) return;
        trackBlock(event.getBlockPlaced(), kind, event.getBlockReplacedState().getBlockData().clone());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!isEnabled()) return;
        if (!isPlayerIgnition(event) || !isInManagedArea(event.getBlock().getLocation())) return;
        Block block = event.getBlock();
        BlockData original = block.getBlockData().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isFire(block.getType())) trackBlock(block, HazardKind.FIRE, original);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLavaFlow(BlockFromToEvent event) {
        if (!isEnabled()) return;
        if (event.getBlock().getType() != Material.LAVA) return;
        BlockKey source = BlockKey.of(event.getBlock().getLocation());
        TrackedBlock sourceRecord = blocks.get(source);
        if (sourceRecord == null || sourceRecord.kind() != HazardKind.LAVA) return;
        Block target = event.getToBlock();
        if (!isInManagedArea(target.getLocation())) return;
        BlockData original = target.getBlockData().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.getType() == Material.LAVA) trackBlock(target, HazardKind.LAVA, original);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof EnderCrystal)) return;
        if (!isInManagedArea(event.getEntity().getLocation())) return;
        entities.put(event.getEntity().getUniqueId(), new TrackedEntity(
                event.getEntity().getWorld().getUID(), cleanupAfter(System.currentTimeMillis())));
        dirty = true;
        logThresholdIfNeeded();
    }

    private void trackBlock(Block block, HazardKind kind, BlockData original) {
        BlockKey key = BlockKey.of(block.getLocation());
        blocks.computeIfAbsent(key, ignored -> new TrackedBlock(
                kind, original.getAsString(), cleanupAfter(System.currentTimeMillis())));
        dirty = true;
        logThresholdIfNeeded();
    }

    private long cleanupAfter(long now) {
        long graceMinutes = Math.max(5L, plugin.getConfig().getLong("spawnHazardCleanup.graceMinutes", 60L));
        return now + graceMinutes * 60_000L;
    }

    private void runCleanup() {
        if (!isEnabled()) return;
        World world = primaryWorld();
        if (world == null || hasBlockingPlayer(world)) return;

        long now = System.currentTimeMillis();
        int removedBlocks = cleanBlocks(now);
        int removedEntities = cleanEntities(now);
        if (removedBlocks > 0 || removedEntities > 0) {
            plugin.getLogger().info("[SpawnHazardCleanup] プレイヤー由来の危険物を局所清掃しました（ブロック="
                    + removedBlocks + "、エンティティ=" + removedEntities + "、座標非表示）。");
            saveIfDirty();
        }
    }

    private int cleanBlocks(long now) {
        int removed = 0;
        Iterator<Map.Entry<BlockKey, TrackedBlock>> iterator = blocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockKey, TrackedBlock> entry = iterator.next();
            TrackedBlock tracked = entry.getValue();
            if (tracked.cleanupAfter() > now) continue;
            World world = Bukkit.getWorld(entry.getKey().worldId());
            if (world == null || !world.isChunkLoaded(entry.getKey().x() >> 4, entry.getKey().z() >> 4)) continue;
            Block block = world.getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z());
            if (tracked.kind().matches(block.getType())) {
                restore(block, tracked.originalBlockData());
                removed++;
            }
            iterator.remove();
            dirty = true;
        }
        return removed;
    }

    private int cleanEntities(long now) {
        int removed = 0;
        Iterator<Map.Entry<UUID, TrackedEntity>> iterator = entities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedEntity> entry = iterator.next();
            if (entry.getValue().cleanupAfter() > now) continue;
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof EnderCrystal && entity.isValid()) {
                entity.remove();
                removed++;
            }
            iterator.remove();
            dirty = true;
        }
        return removed;
    }

    private void restore(Block block, String originalBlockData) {
        try {
            block.setBlockData(Bukkit.createBlockData(originalBlockData), false);
        } catch (IllegalArgumentException ex) {
            block.setType(Material.AIR, false);
        }
    }

    private boolean isInManagedArea(Location location) {
        World world = primaryWorld();
        if (world == null || location.getWorld() == null || !world.equals(location.getWorld())) return false;
        int radius = Math.max(0, plugin.getConfig().getInt("spawnHazardCleanup.radiusChunks", DEFAULT_RADIUS_CHUNKS));
        return isWithinChunkRadius(world.getSpawnLocation(), location, radius);
    }

    private boolean hasBlockingPlayer(World world) {
        String cameraName = plugin.getConfig().getString("patrol.autoStart.cameraPlayerName", "OtouGame");
        int radius = Math.max(0, plugin.getConfig().getInt("spawnHazardCleanup.radiusChunks", DEFAULT_RADIUS_CHUNKS));
        Location spawn = world.getSpawnLocation();
        for (Player player : world.getPlayers()) {
            if (SpawnResetManager.isCameraPlayer(player.getName(), cameraName)) continue;
            if (isWithinChunkRadius(spawn, player.getLocation(), radius)) return true;
        }
        return false;
    }

    static boolean isWithinChunkRadius(Location spawn, Location target, int radiusChunks) {
        if (spawn == null || target == null || spawn.getWorld() == null || target.getWorld() == null
                || !spawn.getWorld().equals(target.getWorld())) return false;
        int radius = Math.max(0, radiusChunks);
        int dx = Math.abs((spawn.getBlockX() >> 4) - (target.getBlockX() >> 4));
        int dz = Math.abs((spawn.getBlockZ() >> 4) - (target.getBlockZ() >> 4));
        return dx <= radius && dz <= radius;
    }

    static boolean isPlayerIgnition(BlockIgniteEvent event) {
        return event.getPlayer() != null;
    }

    static boolean isFire(Material material) {
        return material == Material.FIRE || material == Material.SOUL_FIRE;
    }

    private World primaryWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    void clearTrackedHazardsForFullReset() {
        int count = blocks.size() + entities.size();
        blocks.clear();
        entities.clear();
        dirty = true;
        saveIfDirty();
        if (count > 0) {
            plugin.getLogger().info("[SpawnHazardCleanup] 全面再生成に合わせて古い危険物追跡記録を破棄しました（件数="
                    + count + "、座標非表示）。");
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("spawnHazardCleanup.enabled", true);
    }

    private void logThresholdIfNeeded() {
        int count = blocks.size() + entities.size();
        if (count == 10 || count == 50 || count == 100 || (count > 100 && count % 100 == 0)) {
            plugin.getLogger().warning("[SpawnHazardCleanup] 初期リス周辺でプレイヤー由来の危険物を"
                    + count + "件追跡中です（座標・設置者非表示）。");
        }
    }

    private void load() {
        if (!storageFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection blockSection = yaml.getConfigurationSection("blocks");
        if (blockSection != null) {
            for (String id : blockSection.getKeys(false)) {
                try {
                    ConfigurationSection item = blockSection.getConfigurationSection(id);
                    if (item == null) continue;
                    BlockKey key = new BlockKey(UUID.fromString(item.getString("world")),
                            item.getInt("x"), item.getInt("y"), item.getInt("z"));
                    blocks.put(key, new TrackedBlock(HazardKind.valueOf(item.getString("kind")),
                            item.getString("original", Material.AIR.createBlockData().getAsString()),
                            item.getLong("cleanupAfter")));
                } catch (RuntimeException ignored) {
                    plugin.getLogger().warning("[SpawnHazardCleanup] 読み込めない危険物記録を1件無視しました（座標非表示）。");
                }
            }
        }
        ConfigurationSection entitySection = yaml.getConfigurationSection("entities");
        if (entitySection != null) {
            for (String id : entitySection.getKeys(false)) {
                try {
                    ConfigurationSection item = entitySection.getConfigurationSection(id);
                    if (item == null) continue;
                    entities.put(UUID.fromString(id), new TrackedEntity(
                            UUID.fromString(item.getString("world")), item.getLong("cleanupAfter")));
                } catch (RuntimeException ignored) {
                    plugin.getLogger().warning("[SpawnHazardCleanup] 読み込めない危険エンティティ記録を1件無視しました。");
                }
            }
        }
    }

    private void saveIfDirty() {
        if (!dirty) return;
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (Map.Entry<BlockKey, TrackedBlock> entry : new ArrayList<>(blocks.entrySet())) {
            String path = "blocks." + index++;
            BlockKey key = entry.getKey();
            TrackedBlock value = entry.getValue();
            yaml.set(path + ".world", key.worldId().toString());
            yaml.set(path + ".x", key.x());
            yaml.set(path + ".y", key.y());
            yaml.set(path + ".z", key.z());
            yaml.set(path + ".kind", value.kind().name());
            yaml.set(path + ".original", value.originalBlockData());
            yaml.set(path + ".cleanupAfter", value.cleanupAfter());
        }
        for (Map.Entry<UUID, TrackedEntity> entry : new ArrayList<>(entities.entrySet())) {
            String path = "entities." + entry.getKey();
            yaml.set(path + ".world", entry.getValue().worldId().toString());
            yaml.set(path + ".cleanupAfter", entry.getValue().cleanupAfter());
        }
        try {
            yaml.save(storageFile);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("[SpawnHazardCleanup] 危険物記録を保存できませんでした。次回の定期保存で再試行します。");
        }
    }

    enum HazardKind {
        LAVA { boolean matches(Material material) { return material == Material.LAVA; } },
        FIRE { boolean matches(Material material) { return isFire(material); } },
        TNT { boolean matches(Material material) { return material == Material.TNT; } };
        abstract boolean matches(Material material);
    }

    record BlockKey(UUID worldId, int x, int y, int z) {
        static BlockKey of(Location location) {
            return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
    record TrackedBlock(HazardKind kind, String originalBlockData, long cleanupAfter) { }
    record TrackedEntity(UUID worldId, long cleanupAfter) { }
}
