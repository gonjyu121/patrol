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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Delayed, combat-safe escape for players trapped in blocks or protected spawn terrain. */
public final class EscapeManager implements Listener {
    static final long WAIT_TICKS = 10L * 20L;
    static final long COMBAT_LOCK_MILLIS = 30_000L;
    static final long COOLDOWN_MILLIS = 30L * 60L * 1_000L;
    static final double MOVE_TOLERANCE_SQUARED = 0.05 * 0.05;
    private static final int SEARCH_RADIUS = 24;
    private static final int SEARCH_ATTEMPTS = 96;

    private final PatrolSpectatorPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, PendingEscape> pending = new HashMap<>();
    private final Map<UUID, Long> lastPlayerDamage = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public EscapeManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Denial denial = denialReason(now, lastPlayerDamage.get(id), cooldownUntil.get(id));
        if (denial != Denial.NONE) {
            player.sendMessage(denial.message);
            return;
        }
        if (pending.containsKey(id)) {
            player.sendMessage("§e[Patrol] 脱出準備中です。その場でお待ちください。");
            return;
        }
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            player.sendMessage("§c[Patrol] サバイバルまたはアドベンチャーモードで使用してください。");
            return;
        }
        if (isInDungeon(player.getLocation())) {
            player.sendMessage("§c[Patrol] 死の迷宮内では脱出コマンドを使用できません。");
            return;
        }

        Location origin = player.getLocation().clone();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> complete(player, origin), WAIT_TICKS);
        pending.put(id, new PendingEscape(origin, task));
        player.sendMessage("§e[Patrol] 10秒後に付近の安全な地表へ脱出します。その場から動かず、戦闘しないでください。");
    }

    private void complete(Player player, Location origin) {
        UUID id = player.getUniqueId();
        PendingEscape current = pending.remove(id);
        if (current == null || !player.isOnline()) return;
        long now = System.currentTimeMillis();
        Denial denial = denialReason(now, lastPlayerDamage.get(id), cooldownUntil.get(id));
        if (denial != Denial.NONE) {
            player.sendMessage(denial.message);
            return;
        }
        if (hasMoved(origin, player.getLocation()) || isInDungeon(player.getLocation())) {
            player.sendMessage("§c[Patrol] 状態が変わったため脱出をキャンセルしました。");
            return;
        }

        Location destination = findSafeSurface(origin);
        if (destination == null || !player.teleport(destination)) {
            player.sendMessage("§c[Patrol] 付近に安全な脱出先を見つけられませんでした。管理者へ連絡してください。");
            return;
        }
        cooldownUntil.put(id, now + COOLDOWN_MILLIS);
        player.setFallDistance(0);
        player.sendMessage("§a[Patrol] 付近の安全な地表へ脱出しました。");
        plugin.getLogger().info("[Escape] " + player.getName() + " が閉じ込め脱出を使用しました（座標は記録しません）。");
    }

    private Location findSafeSurface(Location origin) {
        World world = origin.getWorld();
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) return null;
        WorldBorder border = world.getWorldBorder();
        for (int attempt = 0; attempt < SEARCH_ATTEMPTS; attempt++) {
            int dx = attempt == 0 ? 0 : random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            int dz = attempt == 0 ? 0 : random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            int x = origin.getBlockX() + dx;
            int z = origin.getBlockZ() + dz;
            Location column = new Location(world, x + 0.5, origin.getY(), z + 0.5);
            if (!border.isInside(column)) continue;
            int y = world.getHighestBlockYAt(x, z);
            Material ground = world.getBlockAt(x, y, z).getType();
            Material feet = world.getBlockAt(x, y + 1, z).getType();
            Material head = world.getBlockAt(x, y + 2, z).getType();
            Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5, origin.getYaw(), origin.getPitch());
            if (RescueManager.isSafeGround(ground) && feet.isAir() && head.isAir() && !isInDungeon(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        PendingEscape escape = pending.get(event.getPlayer().getUniqueId());
        if (escape != null && hasMoved(escape.origin(), event.getTo())) {
            cancel(event.getPlayer(), "§c[Patrol] 移動したため脱出をキャンセルしました。");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim) {
            if (event instanceof EntityDamageByEntityEvent combat && attackingPlayer(combat.getDamager()) != null) {
                lastPlayerDamage.put(victim.getUniqueId(), System.currentTimeMillis());
            }
            cancel(victim, "§c[Patrol] ダメージを受けたため脱出をキャンセルしました。");
        }
        if (event instanceof EntityDamageByEntityEvent combat) {
            Player attacker = attackingPlayer(combat.getDamager());
            if (attacker != null) cancel(attacker, "§c[Patrol] 攻撃したため脱出をキャンセルしました。");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), null);
    }

    private void cancel(Player player, String message) {
        PendingEscape escape = pending.remove(player.getUniqueId());
        if (escape == null) return;
        escape.task().cancel();
        if (message != null) player.sendMessage(message);
    }

    private boolean isInDungeon(Location location) {
        return plugin.getDungeonManager() != null && plugin.getDungeonManager().isInDungeon(location);
    }

    static boolean hasMoved(Location origin, Location current) {
        return origin.getWorld() != current.getWorld()
                || origin.distanceSquared(current) > MOVE_TOLERANCE_SQUARED;
    }

    static Denial denialReason(long now, Long damagedAt, Long cooldownEnd) {
        if (damagedAt != null && now - damagedAt < COMBAT_LOCK_MILLIS) return Denial.RECENTLY_DAMAGED;
        if (cooldownEnd != null && cooldownEnd > now) return Denial.COOLDOWN;
        return Denial.NONE;
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    enum Denial {
        NONE(""),
        RECENTLY_DAMAGED("§c[Patrol] 対人攻撃を受けた直後は使用できません。30秒間逃げ切ってください。"),
        COOLDOWN("§c[Patrol] 脱出コマンドは30分に1回だけ使用できます。");

        final String message;
        Denial(String message) { this.message = message; }
    }

    private record PendingEscape(Location origin, BukkitTask task) { }
}
