package dev.gonjy.patrolspectator;

import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * ランキングイベントリスナー
 * <p>
 * プレイヤーキル（PK）とエンダードラゴン討伐を記録します。
 */
public class RankingEventListener implements Listener {

    private final PlayerStatsStorage statsStorage;
    private final EngagementSystem engagementSystem;

    public RankingEventListener(PlayerStatsStorage statsStorage, EngagementSystem engagementSystem) {
        this.statsStorage = statsStorage;
        this.engagementSystem = engagementSystem;
    }

    /**
     * プレイヤー死亡時のイベント処理。
     * プレイヤーがプレイヤーにキルされた場合、キラーのPK数を記録します。
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // プレイヤーがプレイヤーをキルした場合のみ記録
        if (killer != null && killer instanceof Player) {
            statsStorage.addPlayerKill(killer.getUniqueId());
            statsStorage.ensureName(killer.getUniqueId(), killer.getName());
        }

        // 死亡したプレイヤーの連続生存時間をリセット
        statsStorage.resetContinuousSurvivalTime(victim.getUniqueId());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // エンダードラゴンの討伐記録は EndGameManager で集約して行うため、ここでは何もしない
        // (将来的に他のMob討伐ランキングを追加する場合はここに記述)
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        engagementSystem.checkEngagement(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        engagementSystem.checkEngagement(event.getPlayer());
    }
}
