package dev.gonjy.patrolspectator;

import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * ランキングイベントリスナー
 * <p>
 * プレイヤーキル（PK）とエンダードラゴン討伐を記録します。
 */
public class RankingEventListener implements Listener {

    private final PlayerStatsStorage statsStorage;

    public RankingEventListener(PlayerStatsStorage statsStorage) {
        this.statsStorage = statsStorage;
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
    }

    /**
     * エンティティ死亡時のイベント処理。
     * エンダードラゴンが倒された場合、キラーのエンドラ討伐数を記録します。
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // エンダードラゴンが倒された場合
        if (entity instanceof EnderDragon) {
            EnderDragon dragon = (EnderDragon) entity;
            Player killer = dragon.getKiller();
            if (killer != null) {
                statsStorage.addEnderDragonKill(killer.getUniqueId());
                statsStorage.ensureName(killer.getUniqueId(), killer.getName());
            }
        }
    }
}
