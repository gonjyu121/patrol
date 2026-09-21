package dev.gonjy.patrolspectator;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** 一般プレイヤーがリスキル地点から一度だけ離脱するための救済機能。 */
public final class RescueManager implements Listener {
    static final long ELIGIBLE_MILLIS = 60_000L;
    static final long COMBAT_LOCK_MILLIS = 5_000L;
    static final long COOLDOWN_MILLIS = 30L * 60L * 1_000L;
    static final long PROTECTION_MILLIS = 30_000L;
    static final int MIN_DISTANCE = 2_000;
    static final int MAX_DISTANCE = 5_000;
    private static final int MAX_SEARCH_ATTEMPTS = 32;

    private final PatrolSpectatorPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> lastDeath = new HashMap<>();
    private final Map<UUID, Long> lastPlayerDamage = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Set<UUID> searchesInProgress = new HashSet<>();

    public RescueManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        long now = System.currentTimeMillis();
        UUID id = event.getEntity().getUniqueId();
        lastDeath.put(id, now);
        protectedUntil.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        long now = System.currentTimeMillis();
        if (event instanceof EntityDamageByEntityEvent combatEvent) {
            Player attacker = attackingPlayer(combatEvent.getDamager());
            if (attacker != null && isProtected(attacker.getUniqueId(), now)) {
                protectedUntil.remove(attacker.getUniqueId());
                attacker.sendMessage("§e[Patrol] 攻撃したため救済保護を解除しました。");
            }
            if (event.getEntity() instanceof Player victim && attacker != null) {
                lastPlayerDamage.put(victim.getUniqueId(), now);
            }
        }
        if (event.getEntity() instanceof Player player && isProtected(player.getUniqueId(), now)) {
            event.setCancelled(true);
        }
    }

    public void rescue(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Denial denial = denialReason(now, lastDeath.get(id), lastPlayerDamage.get(id), cooldownUntil.get(id));
        if (denial != Denial.NONE) {
            player.sendMessage(denial.message);
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            player.sendMessage("§c[Patrol] サバイバルまたはアドベンチャーモードで使用してください。");
            return;
        }
        if (!searchesInProgress.add(id)) {
            player.sendMessage("§e[Patrol] 安全な救済先を探索中です。しばらくお待ちください。");
            return;
        }

        World world = plugin.getServer().getWorlds().stream()
                .filter(candidate -> candidate.getEnvironment() == World.Environment.NORMAL)
                .findFirst().orElse(null);
        if (world == null) {
            searchesInProgress.remove(id);
            player.sendMessage("§c[Patrol] 救済先を用意できませんでした。管理者へ連絡してください。");
            return;
        }

        player.sendMessage("§e[Patrol] 安全な救済先を探索しています...");
        findSafeDestination(player, world, player.getLocation().clone(), now, 0);
    }

    private void findSafeDestination(Player player, World world, Location origin, long requestedAt, int attempt) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            searchesInProgress.remove(id);
            return;
        }
        if (attempt >= MAX_SEARCH_ATTEMPTS) {
            searchesInProgress.remove(id);
            player.sendMessage("§c[Patrol] 安全な救済先を見つけられませんでした。少し待って再実行してください。");
            return;
        }

        WorldBorder border = world.getWorldBorder();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = (int) Math.floor(origin.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(origin.getZ() + Math.sin(angle) * distance);
        Location column = new Location(world, x + 0.5, world.getMinHeight(), z + 0.5);
        if (!border.isInside(column) || isNearSpawn(world, x, z) || isNearDungeon(world, x, z)) {
            findSafeDestination(player, world, origin, requestedAt, attempt + 1);
            return;
        }

        world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null || !player.isOnline()) {
                        findSafeDestination(player, world, origin, requestedAt, attempt + 1);
                        return;
                    }
                    int y = world.getHighestBlockYAt(x, z);
                    Material ground = world.getBlockAt(x, y, z).getType();
                    Material feet = world.getBlockAt(x, y + 1, z).getType();
                    Material head = world.getBlockAt(x, y + 2, z).getType();
                    if (!isSafeGround(ground) || !feet.isAir() || !head.isAir()) {
                        findSafeDestination(player, world, origin, requestedAt, attempt + 1);
                        return;
                    }
                    if (lastPlayerDamage.getOrDefault(id, 0L) > requestedAt) {
                        searchesInProgress.remove(id);
                        player.sendMessage("§c[Patrol] 探索中に対人攻撃を受けたため、救済を中止しました。");
                        return;
                    }
                    Location destination = new Location(world, x + 0.5, y + 1, z + 0.5, origin.getYaw(), 0f);
                    if (!player.teleport(destination)) {
                        searchesInProgress.remove(id);
                        player.sendMessage("§c[Patrol] 救済先への移動に失敗しました。少し待って再実行してください。");
                        return;
                    }
                    long now = System.currentTimeMillis();
                    searchesInProgress.remove(id);
                    cooldownUntil.put(id, now + COOLDOWN_MILLIS);
                    protectedUntil.put(id, now + PROTECTION_MILLIS);
                    lastDeath.remove(id);
                    player.setFallDistance(0);
                    player.sendMessage("§a[Patrol] リスキル地点から安全な場所へ避難しました。");
                    player.sendMessage("§e[Patrol] 30秒間保護されます。攻撃すると保護は解除されます。");
                    plugin.getLogger().info("[Rescue] " + player.getName() + " がリスキル救済を使用しました（座標は記録しません）。");
                }));
    }

    private boolean isNearDungeon(World world, int x, int z) {
        Location center = plugin.getDungeonManager() == null ? null : plugin.getDungeonManager().getCenter();
        if (center == null || center.getWorld() != world) return false;
        return horizontalDistanceSquared(x, z, center.getBlockX(), center.getBlockZ()) < 256L * 256L;
    }

    private static boolean isNearSpawn(World world, int x, int z) {
        Location spawn = world.getSpawnLocation();
        return horizontalDistanceSquared(x, z, spawn.getBlockX(), spawn.getBlockZ()) < 512L * 512L;
    }

    static boolean isSafeGround(Material material) {
        return material.isSolid() && material != Material.LAVA && material != Material.WATER
                && material != Material.MAGMA_BLOCK && material != Material.CACTUS
                && material != Material.FIRE && material != Material.SOUL_FIRE
                && material != Material.POWDER_SNOW && !material.name().endsWith("_LEAVES");
    }

    static Denial denialReason(long now, Long deathAt, Long damagedAt, Long cooldownEnd) {
        if (deathAt == null || now - deathAt > ELIGIBLE_MILLIS) return Denial.NOT_RECENTLY_DEAD;
        if (damagedAt != null && now - damagedAt < COMBAT_LOCK_MILLIS) return Denial.RECENTLY_DAMAGED;
        if (cooldownEnd != null && cooldownEnd > now) return Denial.COOLDOWN;
        return Denial.NONE;
    }

    private boolean isProtected(UUID id, long now) {
        Long until = protectedUntil.get(id);
        if (until == null) return false;
        if (until <= now) {
            protectedUntil.remove(id);
            return false;
        }
        return true;
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private static long horizontalDistanceSquared(int x1, int z1, int x2, int z2) {
        long dx = (long) x1 - x2;
        long dz = (long) z1 - z2;
        return dx * dx + dz * dz;
    }

    enum Denial {
        NONE(""),
        NOT_RECENTLY_DEAD("§c[Patrol] この救済は死亡から60秒以内のみ使用できます。"),
        RECENTLY_DAMAGED("§c[Patrol] 対人攻撃を受けた直後は使用できません。5秒間逃げ切ってください。"),
        COOLDOWN("§c[Patrol] 救済コマンドは30分に1回だけ使用できます。");

        final String message;
        Denial(String message) { this.message = message; }
    }
}
