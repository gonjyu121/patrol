package dev.gonjy.patrolspectator;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 明白に通常プレイの範囲外である操作だけを無効化する、緩いサーバー保護。
 * BAN・キック・移動補正・装置制限は行わない。
 */
public final class SafetyGuard implements Listener {
    private static final long ONE_SECOND_NANOS = 1_000_000_000L;

    private final Logger logger;
    private final boolean reachEnabled;
    private final boolean fastBreakEnabled;
    private final boolean exemptOps;
    private final double maxReachDistance;
    private final int maxBreaksPerSecond;
    private final long logIntervalNanos;
    private final Map<UUID, BreakWindow> breakWindows = new HashMap<>();
    private final Map<String, Long> lastLogNanos = new HashMap<>();

    public SafetyGuard(PatrolSpectatorPlugin plugin) {
        this.logger = plugin.getLogger();
        this.reachEnabled = plugin.getConfig().getBoolean("safety_guard.reach.enabled", true);
        this.fastBreakEnabled = plugin.getConfig().getBoolean("safety_guard.fast_break.enabled", true);
        this.exemptOps = plugin.getConfig().getBoolean("safety_guard.exempt_ops", true);
        // 誤検知防止のため、設定値が小さくても安全側の下限を維持する。
        this.maxReachDistance = Math.max(8.0,
                plugin.getConfig().getDouble("safety_guard.reach.max_distance", 10.0));
        this.maxBreaksPerSecond = Math.max(40,
                plugin.getConfig().getInt("safety_guard.fast_break.max_breaks_per_second", 50));
        int logIntervalSeconds = Math.max(1,
                plugin.getConfig().getInt("safety_guard.log_interval_seconds", 10));
        this.logIntervalNanos = logIntervalSeconds * ONE_SECOND_NANOS;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDirectAttack(EntityDamageByEntityEvent event) {
        if (!reachEnabled || !(event.getDamager() instanceof Player player) || isExempt(player)) {
            return;
        }

        Entity target = event.getEntity();
        if (player.getWorld() != target.getWorld()) {
            return;
        }

        Location eye = player.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        if (!isClearlyOutOfReach(eye.getX(), eye.getY(), eye.getZ(), box, maxReachDistance)) {
            return;
        }

        event.setCancelled(true);
        logRateLimited(player, "reach",
                "[SafetyGuard] 極端な攻撃距離のため攻撃だけを無効化しました: player=" + player.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!fastBreakEnabled || isExempt(player)) {
            return;
        }

        BreakWindow window = breakWindows.computeIfAbsent(player.getUniqueId(), ignored -> new BreakWindow());
        if (!window.recordAndCheck(System.nanoTime(), maxBreaksPerSecond)) {
            return;
        }

        event.setCancelled(true);
        logRateLimited(player, "fast_break",
                "[SafetyGuard] 極端な破壊頻度のため上限超過分だけを無効化しました: player=" + player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        breakWindows.remove(uuid);
        lastLogNanos.keySet().removeIf(key -> key.startsWith(uuid + ":"));
    }

    private boolean isExempt(Player player) {
        GameMode mode = player.getGameMode();
        return (exemptOps && player.isOp())
                || player.hasPermission("patrolspectator.exempt")
                || mode == GameMode.CREATIVE
                || mode == GameMode.SPECTATOR;
    }

    private void logRateLimited(Player player, String type, String message) {
        long now = System.nanoTime();
        String key = player.getUniqueId() + ":" + type;
        Long last = lastLogNanos.get(key);
        if (last == null || now - last >= logIntervalNanos || now < last) {
            lastLogNanos.put(key, now);
            logger.warning(message);
        }
    }

    static boolean isClearlyOutOfReach(double x, double y, double z, BoundingBox box, double maxDistance) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(maxDistance) || maxDistance <= 0.0) {
            return false;
        }
        double distanceSquared = distanceSquaredToBox(x, y, z,
                box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ());
        return distanceSquared > maxDistance * maxDistance;
    }

    static double distanceSquaredToBox(double x, double y, double z,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        double dx = axisDistance(x, minX, maxX);
        double dy = axisDistance(y, minY, maxY);
        double dz = axisDistance(z, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0;
    }

    static final class BreakWindow {
        private long windowStartNanos = Long.MIN_VALUE;
        private int acceptedBreaks;

        boolean recordAndCheck(long nowNanos, int maxBreaksPerSecond) {
            if (windowStartNanos == Long.MIN_VALUE || nowNanos < windowStartNanos
                    || nowNanos - windowStartNanos >= ONE_SECOND_NANOS) {
                windowStartNanos = nowNanos;
                acceptedBreaks = 1;
                return false;
            }
            if (acceptedBreaks >= maxBreaksPerSecond) {
                return true;
            }
            acceptedBreaks++;
            return false;
        }
    }
}
