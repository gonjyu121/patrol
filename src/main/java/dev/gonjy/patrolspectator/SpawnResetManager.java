package dev.gonjy.patrolspectator;

import dev.gonjy.patrolspectator.dungeon.DungeonManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Safely regenerates a small square of chunks around the primary world spawn. */
public final class SpawnResetManager {
    static final int DEFAULT_RADIUS_CHUNKS = 4;
    static final long CONFIRMATION_MILLIS = 60_000L;

    private final PatrolSpectatorPlugin plugin;
    private final DungeonManager dungeonManager;
    private final Map<String, Long> confirmations = new HashMap<>();
    private BukkitTask resetTask;

    public SpawnResetManager(PatrolSpectatorPlugin plugin, DungeonManager dungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
    }

    public void preview(CommandSender sender) {
        if (isRunning()) {
            sender.sendMessage("§e[Patrol] 初期リス周辺の再生成はすでに実行中です。");
            return;
        }

        World world = primaryWorld();
        if (world == null) {
            sender.sendMessage("§c[Patrol] 対象ワールドを取得できませんでした。");
            return;
        }

        ResetPlan plan = createSafePlan(world);
        List<ChunkPosition> chunks = plan.chunks();
        String refusal = refusalReason(world, chunks);
        if (refusal != null) {
            sender.sendMessage(refusal);
            return;
        }

        confirmations.put(senderKey(sender), System.currentTimeMillis() + CONFIRMATION_MILLIS);
        sender.sendMessage("§e[Patrol] 初期リス周辺の" + chunks.size() + "チャンクを自然地形へ再生成します。");
        if (plan.excludedDungeonChunks() > 0) {
            sender.sendMessage("§6[Patrol] 死の迷宮と重なる" + plan.excludedDungeonChunks()
                    + "チャンクは保護し、再生成対象から除外します。");
        }
        sender.sendMessage("§c建築物や設置物は失われます。60秒以内に §f/patrol spawnreset confirm §cで確定してください。");
    }

    public void confirm(CommandSender sender) {
        Long expiresAt = confirmations.remove(senderKey(sender));
        if (!isConfirmationValid(expiresAt, System.currentTimeMillis())) {
            sender.sendMessage("§c[Patrol] 確認が未実行か期限切れです。先に /patrol spawnreset を実行してください。");
            return;
        }
        if (isRunning()) {
            sender.sendMessage("§e[Patrol] 初期リス周辺の再生成はすでに実行中です。");
            return;
        }

        World world = primaryWorld();
        if (world == null) {
            sender.sendMessage("§c[Patrol] 対象ワールドを取得できませんでした。");
            return;
        }
        ResetPlan plan = createSafePlan(world);
        List<ChunkPosition> chunks = plan.chunks();
        String refusal = refusalReason(world, chunks);
        if (refusal != null) {
            sender.sendMessage(refusal);
            return;
        }

        plugin.getLogger().warning("[SpawnReset] 初期リス周辺の再生成を開始します（対象=" + chunks.size()
                + "チャンク、迷宮保護による除外=" + plan.excludedDungeonChunks() + "チャンク、座標非表示）。");
        sender.sendMessage("§a[Patrol] 初期リス周辺の再生成を開始しました。負荷を抑えて順番に処理します。");
        int[] index = {0};
        resetTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (hasPlayerInArea(world, new HashSet<>(chunks))) {
                plugin.getLogger().warning("[SpawnReset] 対象範囲へのプレイヤー進入を検知したため、安全のため中断しました。");
                sender.sendMessage("§c[Patrol] 対象範囲にプレイヤーが入ったため再生成を中断しました。");
                stopTask();
                return;
            }
            if (index[0] >= chunks.size()) {
                plugin.getLogger().info("[SpawnReset] 初期リス周辺の再生成が完了しました（座標非表示）。");
                sender.sendMessage("§a[Patrol] 初期リス周辺の再生成が完了しました。");
                stopTask();
                return;
            }

            ChunkPosition chunk = chunks.get(index[0]++);
            if (!world.regenerateChunk(chunk.x(), chunk.z())) {
                plugin.getLogger().warning("[SpawnReset] 再生成できないチャンクがあり、安全のため処理を中断しました（座標非表示）。");
                sender.sendMessage("§c[Patrol] 再生成できない範囲があったため処理を中断しました。サーバーログを確認してください。");
                stopTask();
            }
        }, 1L, 2L);
    }

    public boolean isRunning() {
        return resetTask != null;
    }

    private void stopTask() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
    }

    private World primaryWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private String refusalReason(World world, List<ChunkPosition> chunks) {
        if (chunks.isEmpty()) {
            return "§c[Patrol] 対象範囲の全チャンクが死の迷宮と重なるため再生成できません。";
        }
        Set<ChunkPosition> area = new HashSet<>(chunks);
        if (hasPlayerInArea(world, area)) {
            return "§c[Patrol] 対象範囲にプレイヤーがいるため再生成できません。全員が離れてから再実行してください。";
        }
        return null;
    }

    private ResetPlan createSafePlan(World world) {
        List<ChunkPosition> planned = createPlan(world.getSpawnLocation(), DEFAULT_RADIUS_CHUNKS);
        Location center = dungeonManager == null ? null : dungeonManager.getCenter();
        if (dungeonManager == null || !dungeonManager.isEnabled() || center == null
                || center.getWorld() == null || !center.getWorld().equals(world)) {
            return new ResetPlan(planned, 0);
        }
        return excludeDungeonChunks(planned, center, DungeonManager.DUNGEON_RADIUS_BLOCKS);
    }

    static List<ChunkPosition> createPlan(Location spawn, int radiusChunks) {
        int radius = Math.max(0, radiusChunks);
        int centerX = spawn.getBlockX() >> 4;
        int centerZ = spawn.getBlockZ() >> 4;
        List<ChunkPosition> chunks = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int ring = 0; ring <= radius; ring++) {
            for (int x = centerX - ring; x <= centerX + ring; x++) {
                for (int z = centerZ - ring; z <= centerZ + ring; z++) {
                    if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) == ring) {
                        chunks.add(new ChunkPosition(x, z));
                    }
                }
            }
        }
        return chunks;
    }

    static boolean overlapsDungeon(Set<ChunkPosition> resetArea, Location center, int radiusBlocks) {
        int minChunkX = (center.getBlockX() - radiusBlocks) >> 4;
        int maxChunkX = (center.getBlockX() + radiusBlocks) >> 4;
        int minChunkZ = (center.getBlockZ() - radiusBlocks) >> 4;
        int maxChunkZ = (center.getBlockZ() + radiusBlocks) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (resetArea.contains(new ChunkPosition(x, z))) return true;
            }
        }
        return false;
    }

    static ResetPlan excludeDungeonChunks(List<ChunkPosition> planned, Location center, int radiusBlocks) {
        int minChunkX = (center.getBlockX() - radiusBlocks) >> 4;
        int maxChunkX = (center.getBlockX() + radiusBlocks) >> 4;
        int minChunkZ = (center.getBlockZ() - radiusBlocks) >> 4;
        int maxChunkZ = (center.getBlockZ() + radiusBlocks) >> 4;
        List<ChunkPosition> safe = new ArrayList<>(planned.size());
        int excluded = 0;
        for (ChunkPosition chunk : planned) {
            boolean dungeonChunk = chunk.x() >= minChunkX && chunk.x() <= maxChunkX
                    && chunk.z() >= minChunkZ && chunk.z() <= maxChunkZ;
            if (dungeonChunk) excluded++;
            else safe.add(chunk);
        }
        return new ResetPlan(List.copyOf(safe), excluded);
    }

    static boolean isConfirmationValid(Long expiresAt, long now) {
        return expiresAt != null && expiresAt >= now;
    }

    private static boolean hasPlayerInArea(World world, Set<ChunkPosition> area) {
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            if (area.contains(new ChunkPosition(location.getBlockX() >> 4, location.getBlockZ() >> 4))) return true;
        }
        return false;
    }

    private static String senderKey(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "console:" + sender.getName();
    }

    record ChunkPosition(int x, int z) { }
    record ResetPlan(List<ChunkPosition> chunks, int excludedDungeonChunks) { }
}
