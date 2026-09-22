package dev.gonjy.patrolspectator;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 相手の明示承認を必須とする、一般プレイヤー向けのTP招待管理。 */
public final class TeleportRequestManager implements Listener {
    static final long REQUEST_LIFETIME_MILLIS = 60_000L;
    static final long INVITE_COOLDOWN_MILLIS = 10_000L;
    static final long COMBAT_LOCK_MILLIS = 10_000L;

    private final PatrolSpectatorPlugin plugin;
    private final Map<UUID, Request> pendingByTarget = new HashMap<>();
    private final Map<UUID, Long> inviteCooldownUntil = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();

    public TeleportRequestManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
    }

    public void invite(Player requester, String targetName) {
        Player target = findOnlinePlayer(targetName);
        if (target == null) {
            requester.sendMessage("§c[Patrol] 指定したプレイヤーはオンラインではありません。");
            return;
        }
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            requester.sendMessage("§c[Patrol] 自分自身は招待できません。");
            return;
        }
        if (isCamera(requester) || isCamera(target)) {
            requester.sendMessage("§c[Patrol] 配信用カメラはTP招待に利用できません。");
            return;
        }
        long now = System.currentTimeMillis();
        if (inviteCooldownUntil.getOrDefault(requester.getUniqueId(), 0L) > now) {
            requester.sendMessage("§c[Patrol] TP招待は10秒に1回送れます。");
            return;
        }
        String requesterReason = unavailableReason(requester, now);
        if (requesterReason != null) {
            requester.sendMessage(requesterReason);
            return;
        }
        if (unavailableReason(target, now) != null) {
            requester.sendMessage("§c[Patrol] 相手は現在TP招待を利用できません。");
            return;
        }

        Request existing = activeRequest(target.getUniqueId(), now);
        if (existing != null) {
            requester.sendMessage("§c[Patrol] 相手は別のTP招待を確認中です。");
            return;
        }

        Request request = new Request(requester.getUniqueId(), target.getUniqueId(), now);
        pendingByTarget.put(target.getUniqueId(), request);
        inviteCooldownUntil.put(requester.getUniqueId(), now + INVITE_COOLDOWN_MILLIS);
        requester.sendMessage("§a[Patrol] " + target.getName() + " さんへTP招待を送りました。");
        target.sendMessage("§e[Patrol] " + requester.getName() + " さんから、その場所へのTP招待が届きました。");
        target.sendMessage("§e承認: /patrol accept  拒否: /patrol deny（60秒で失効）");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> expire(request), 20L * 60L);
    }

    public void accept(Player target) {
        long now = System.currentTimeMillis();
        Request request = activeRequest(target.getUniqueId(), now);
        if (request == null) {
            target.sendMessage("§c[Patrol] 有効なTP招待はありません。");
            return;
        }
        Player requester = plugin.getServer().getPlayer(request.requesterId());
        if (requester == null || !requester.isOnline()) {
            pendingByTarget.remove(target.getUniqueId());
            target.sendMessage("§c[Patrol] 招待したプレイヤーはオフラインです。");
            return;
        }
        String targetReason = unavailableReason(target, now);
        if (targetReason != null) {
            target.sendMessage(targetReason);
            return;
        }
        if (unavailableReason(requester, now) != null) {
            target.sendMessage("§c[Patrol] 招待元のプレイヤーは現在TP招待を利用できません。");
            return;
        }

        pendingByTarget.remove(target.getUniqueId());
        if (!target.teleport(requester.getLocation())) {
            target.sendMessage("§c[Patrol] TPに失敗しました。もう一度招待してもらってください。");
            requester.sendMessage("§c[Patrol] 相手のTPに失敗しました。");
            return;
        }
        target.setFallDistance(0);
        target.sendMessage("§a[Patrol] " + requester.getName() + " さんの場所へ移動しました。");
        requester.sendMessage("§a[Patrol] " + target.getName() + " さんがTP招待を承認しました。");
        plugin.getLogger().info("[PlayerTP] " + requester.getName() + " から " + target.getName()
                + " への承認制TPが完了しました（座標は記録しません）。");
    }

    public void deny(Player target) {
        Request request = pendingByTarget.remove(target.getUniqueId());
        if (request == null || isExpired(request, System.currentTimeMillis())) {
            target.sendMessage("§c[Patrol] 有効なTP招待はありません。");
            return;
        }
        Player requester = plugin.getServer().getPlayer(request.requesterId());
        target.sendMessage("§e[Patrol] TP招待を拒否しました。");
        if (requester != null) requester.sendMessage("§e[Patrol] TP招待は拒否されました。");
    }

    private String unavailableReason(Player player, long now) {
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return "§c[Patrol] 現在のゲームモードではTP招待を利用できません。";
        }
        if (isInCombat(player.getUniqueId(), now)) {
            return "§c[Patrol] 戦闘終了から10秒間はTP招待を利用できません。";
        }
        if (plugin.getDungeonManager() != null && plugin.getDungeonManager().isInDungeon(player.getLocation())) {
            return "§c[Patrol] 死の迷宮内ではTP招待を利用できません。";
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof Player victim)) return;
        long now = System.currentTimeMillis();
        lastCombat.put(attacker.getUniqueId(), now);
        lastCombat.put(victim.getUniqueId(), now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearRequestsFor(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearRequestsFor(event.getEntity().getUniqueId());
    }

    private void expire(Request request) {
        Request current = pendingByTarget.get(request.targetId());
        if (!request.equals(current)) return;
        pendingByTarget.remove(request.targetId());
        Player target = plugin.getServer().getPlayer(request.targetId());
        Player requester = plugin.getServer().getPlayer(request.requesterId());
        if (target != null) target.sendMessage("§7[Patrol] TP招待の有効期限が切れました。");
        if (requester != null) requester.sendMessage("§7[Patrol] TP招待の有効期限が切れました。");
    }

    private void clearRequestsFor(UUID playerId) {
        pendingByTarget.remove(playerId);
        pendingByTarget.entrySet().removeIf(entry -> entry.getValue().requesterId().equals(playerId));
        lastCombat.remove(playerId);
        inviteCooldownUntil.remove(playerId);
    }

    private Request activeRequest(UUID targetId, long now) {
        Request request = pendingByTarget.get(targetId);
        if (request != null && isExpired(request, now)) {
            pendingByTarget.remove(targetId);
            return null;
        }
        return request;
    }

    private Player findOnlinePlayer(String name) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) return player;
        }
        return null;
    }

    private boolean isCamera(Player player) {
        return CameraMessageRouter.isCameraName(plugin.getAutoStartConf().cameraPlayerName, player.getName());
    }

    static boolean isExpired(Request request, long now) {
        return now - request.createdAt() >= REQUEST_LIFETIME_MILLIS;
    }

    boolean isInCombat(UUID playerId, long now) {
        Long last = lastCombat.get(playerId);
        return isCombatRecent(now, last);
    }

    static boolean isCombatRecent(long now, Long lastCombatAt) {
        return lastCombatAt != null && now - lastCombatAt < COMBAT_LOCK_MILLIS;
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    record Request(UUID requesterId, UUID targetId, long createdAt) { }
}
