package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
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
    private boolean active = false;
    private final Map<UUID, Long> worldChangeCooldowns = new HashMap<>();

    public GameModeEnforcer(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        this.active = true;
        if (task != null)
            return;
        
        long interval = 20L;
        if (plugin instanceof PatrolSpectatorPlugin) {
            if (((PatrolSpectatorPlugin) plugin).getPerformanceConf().lowSpecMode) {
                interval = 60L; // 低スペックモード時は3秒おき
            }
        }
        
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
        }, interval, interval);
    }

    public void stop() {
        this.active = false;
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
        
        // ワールド移動直後はクライアント側の読み込みを待つため、数秒間は判定をスキップする
        long now = System.currentTimeMillis();
        if (worldChangeCooldowns.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        
        // 監視が有効かつカメラ役（配信アカウント等）の場合のみ、スペクテイターに強制する
        if (active && isCameraPlayer(p)) {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                try {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.setFlying(true);
                    p.setInvulnerable(true);
                } catch (Throwable ignored) {}
            }
            return;
        }

        // それ以外（パトロール停止中、または一般プレイヤー）はサバイバルに強制する
        if (p.getGameMode() != GameMode.SURVIVAL) {
            try {
                p.setGameMode(GameMode.SURVIVAL);
                // パトロール停止時は無敵も解除
                if (!active && isCameraPlayer(p)) {
                    p.setInvulnerable(false);
                }
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

    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        // ワールド移動後3秒間は、ゲームモード強制を保留する（タイムアウト/キック防止）
        worldChangeCooldowns.put(e.getPlayer().getUniqueId(), System.currentTimeMillis() + 3000);
    }

    @EventHandler(priority = EventPriority.MONITOR)
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
