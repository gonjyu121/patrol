package dev.gonjy.patrolspectator.dungeon;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import dev.gonjy.patrolspectator.PatrolSpectatorPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;

public class DungeonListener implements Listener {

    private final PatrolSpectatorPlugin plugin;
    private final DungeonManager manager;
    private final DungeonStatsStorage stats;
    private final TrapRunner trapRunner;
    private final int emptyResetDelaySeconds;
    private BukkitTask emptyResetTask;
    private boolean wasOccupied;
    private boolean completionResetPending;
    private boolean observedCompletionBuild;

    public DungeonListener(PatrolSpectatorPlugin plugin, DungeonManager manager, DungeonStatsStorage stats,
            TrapRunner trapRunner) {
        this.plugin = plugin;
        this.manager = manager;
        this.stats = stats;
        this.trapRunner = trapRunner;
        this.emptyResetDelaySeconds = Math.max(10,
                plugin.getConfig().getInt("dungeon.resetWhenEmptyDelaySeconds", 60));
        startOccupancyMonitor();
    }

    private void startOccupancyMonitor() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!manager.isEnabled()) {
                cancelEmptyReset();
                wasOccupied = false;
                return;
            }

            if (completionResetPending) {
                if (plugin.getDungeonBuilder().isBuilding()) {
                    observedCompletionBuild = true;
                } else if (observedCompletionBuild) {
                    completionResetPending = false;
                    observedCompletionBuild = false;
                    wasOccupied = false;
                }
                return;
            }

            boolean occupied = hasChallengePlayers();
            if (occupied) {
                cancelEmptyReset();
            } else if (shouldScheduleEmptyReset(wasOccupied, occupied, completionResetPending)) {
                scheduleEmptyReset();
            }
            wasOccupied = occupied;
        }, 20L, 20L);
    }

    private boolean hasChallengePlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOnline)
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .anyMatch(this::isInsidePlayableDungeonFloor);
    }

    private boolean isInsidePlayableDungeonFloor(Player player) {
        Location center = manager.getCenter();
        Location location = player.getLocation();
        if (center == null || !manager.isInDungeon(location))
            return false;
        return manager.getFloor(location) > 0;
    }

    static boolean shouldScheduleEmptyReset(boolean wasOccupied, boolean occupied, boolean resetPending) {
        return wasOccupied && !occupied && !resetPending;
    }

    private void scheduleEmptyReset() {
        if (emptyResetTask != null || plugin.getDungeonBuilder().isBuilding())
            return;

        plugin.getLogger().info("[Dungeon] 挑戦者がいなくなったため、" + emptyResetDelaySeconds
                + "秒後に迷宮を初期化します。");
        emptyResetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            emptyResetTask = null;
            if (hasChallengePlayers() || plugin.getDungeonBuilder().isBuilding())
                return;

            trapRunner.resetState();
            manager.setBuilt(false);
            if (plugin.getDungeonBuilder().buildB1()) {
                plugin.getLogger().info("[Dungeon] 空室リセットを開始しました。宝箱・敵・ボス・罠状態を復活させます。");
            } else {
                plugin.getLogger().warning("[Dungeon] 空室リセットを開始できませんでした。次回起動時に再試行します。");
            }
        }, emptyResetDelaySeconds * 20L);
    }

    private void cancelEmptyReset() {
        if (emptyResetTask != null) {
            emptyResetTask.cancel();
            emptyResetTask = null;
            plugin.getLogger().info("[Dungeon] 新しい挑戦者が入ったため、空室リセットを取り消しました。");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (manager.isInDungeon(event.getBlock().getLocation())) {
            if (!event.getPlayer().isOp()) {
                event.setCancelled(true);
                send(event.getPlayer(), "迷宮の壁を傷つけることはできません…", NamedTextColor.RED);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (manager.isInDungeon(event.getBlock().getLocation())) {
            if (!event.getPlayer().isOp()) {
                event.setCancelled(true);
                send(event.getPlayer(), "迷宮を汚すことは許されません…", NamedTextColor.RED);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (stats.hasPermanentKeepInventory(player.getUniqueId())) {
            applyPermanentKeepInventory(event);
        }
        Location deathLoc = player.getLocation();

        // 早期リターン: 迷宮外なら何もしない
        if (!manager.isInDungeon(deathLoc))
            return;

        // 血のカウント加算
        stats.incrementGlobalDeathCount();
        long totalDeaths = stats.getGlobalDeathCount();

        // メッセージ表示 (Wizardry風)
        dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin,
                Component.text("======== [ 迷宮の供物 ] ========", NamedTextColor.DARK_RED));
        dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin,
                Component.text(player.getName() + " が死の迷宮の糧となりました…", NamedTextColor.RED));
        dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin, Component.text("この迷宮は既に ", NamedTextColor.GRAY)
                .append(Component.text(totalDeaths, NamedTextColor.RED))
                .append(Component.text(" 人の血を吸いました…", NamedTextColor.GRAY)));
        dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin,
                Component.text("================================", NamedTextColor.DARK_RED));

        // 死亡時の雷エフェクトと全体サウンドパニック演出
        deathLoc.getWorld().strikeLightningEffect(deathLoc);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnChange(PlayerSetSpawnEvent event) {
        Location newSpawn = event.getLocation();
        if (shouldBlockSpawnChange(newSpawn != null && manager.isInDungeon(newSpawn))) {
            event.setCancelled(true);
            send(event.getPlayer(), "死の迷宮内をリスポーン地点に登録することはできません。", NamedTextColor.RED);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // 重くない程度の頻度で判定 (blockが変わった時だけ)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ())
            return;

        Player p = event.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return;

        Location loc = p.getLocation();

        // 早期リターン: 迷宮内でも外でもない（遠い）なら無視
        if (loc.getWorld().equals(manager.getCenter().getWorld())) {
            double dxFar = loc.getX() - manager.getCenter().getX();
            double dzFar = loc.getZ() - manager.getCenter().getZ();
            if ((dxFar * dxFar) + (dzFar * dzFar) > 50 * 50)
                return; // 迷宮のさらに外側
        }

        boolean inDungeon = manager.isInDungeon(loc);
        if (!inDungeon && manager.isEnabled()) {
            // 迷宮のすぐ外側（壁抜けチェック）
            Location center = manager.getCenter();
            double dx = Math.abs(loc.getX() - center.getX());
            double dz = Math.abs(loc.getZ() - center.getZ());
            double dy = Math.abs(loc.getY() - center.getY());

            // 迷宮の外壁（30マス）のすぐ外側（31〜35マス）にいる場合
            if ((dx > 30.5 && dx < 36) || (dz > 30.5 && dz < 36) || (dy > 10.5 && dy < 15)) {
                Material type = loc.getBlock().getType();
                if (type == Material.STONE || type == Material.DEEPSLATE || type == Material.DIRT) {
                    send(p, "迷宮の外へ逃げることはできません…", NamedTextColor.RED);
                    Location exit = center.clone().add(0, 0, -35);
                    exit.setY(center.getWorld().getHighestBlockYAt(exit) + 1.0);
                    p.teleport(exit);
                    return;
                }
            }
        }

        if (!inDungeon)
            return;

        // 水中なら高確率でドラウンド襲来 (15%)
        if (loc.getBlock().getType() == Material.WATER
                || loc.clone().add(0, 1, 0).getBlock().getType() == Material.WATER) {
            if (new java.util.Random().nextDouble() < 0.15) {
                trapRunner.runWaterTrap(p, loc);
            }
            return;
        }

        // 踏んでいるブロックが空気（通路）なら低確率で発動
        if (loc.getBlock().getType() == Material.AIR) {
            if (new java.util.Random().nextDouble() < 0.05) { // 5% の確率で移動時に何かが起きる
                trapRunner.triggerTrap(p, loc);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null)
            return;
        if (!manager.isInDungeon(event.getClickedBlock().getLocation()))
            return;

        Player p = event.getPlayer();

        // 感圧版（自動トラップ）
        if (event.getAction() == Action.PHYSICAL) {
            trapRunner.triggerTrap(p, event.getClickedBlock().getLocation());
            return;
        }

        // 宝箱の右クリック（報酬と罠）
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().getType() == Material.CHEST) {
            DungeonLootSystem lootSystem = plugin.getDungeonLootSystem();
            if (lootSystem.shouldTriggerTrap()) {
                triggerChestTrap(p, event.getClickedBlock().getLocation());
            }
        }
    }

    private void triggerChestTrap(Player p, Location loc) {
        int choice = new java.util.Random().nextInt(3);
        switch (choice) {
            case 0: // 爆発
                loc.getWorld().createExplosion(loc, 2.0f, false, false);
                send(p, "宝箱に仕掛けられた爆弾が爆発した！", NamedTextColor.RED);
                break;
            case 1: // 毒・衰弱
                p.addPotionEffect(
                        new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 200, 1));
                p.addPotionEffect(
                        new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER, 200, 1));
                send(p, "宝箱から毒ガスが噴き出した！", NamedTextColor.DARK_PURPLE);
                break;
            case 2: // 奇襲 (MobTrapを流用)
                trapRunner.triggerTrap(p, loc); // Mobなどの既存Trapを発動
                send(p, "宝箱を開ける音を聞きつけ、魔物が集まってきた！", NamedTextColor.DARK_RED);
                break;
        }
    }

    @EventHandler
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        org.bukkit.entity.LivingEntity entity = event.getEntity();
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "is_dungeon_boss");
        if (entity.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
            // Vanilla dropも含め、迷宮ボスの報酬はこのリスナーだけで管理する。
            event.getDrops().clear();

            Player killer = entity.getKiller();
            boolean killerInDungeon = killer != null && manager.isInDungeon(killer.getLocation());
            boolean bossInDungeon = manager.isInDungeon(entity.getLocation());
            if (!isValidBossDefeat(killer != null, killerInDungeon, bossInDungeon)) {
                plugin.getLogger().warning("迷宮外またはプレイヤー以外の原因でボスが死亡したため、報酬と踏破処理を無効化しました。");
                return;
            }

            org.bukkit.NamespacedKey floorKey = new org.bukkit.NamespacedKey(plugin, "dungeon_floor");
            int floor = entity.getPersistentDataContainer().getOrDefault(
                    floorKey, org.bukkit.persistence.PersistentDataType.INTEGER, 1);
            int deepestFloor = manager.getFloorCount();
            boolean dungeonCompleted = floor >= deepestFloor;
            cancelEmptyReset();
            if (dungeonCompleted) {
                completionResetPending = true;
                observedCompletionBuild = false;
            }
            // ボスが倒された！
            String name = killer.getName();

            Component bossName = entity.customName() != null
                    ? entity.customName()
                    : Component.text(entity.getName());
            dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin,
                    Component.text("======== [ 地下" + floor + "階 踏破 ] ========", NamedTextColor.GOLD));
            dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin,
                    Component.text(name + " が ", NamedTextColor.YELLOW)
                            .append(bossName.colorIfAbsent(NamedTextColor.RED))
                            .append(Component.text(" を討伐しました！", NamedTextColor.YELLOW)));
            dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin, Component.text(dungeonCompleted
                    ? "最下層が攻略され、迷宮の再構築が始まります…"
                    : "地下" + (floor + 1) + "階への道が開かれました。梯子を探してください。", NamedTextColor.AQUA));
            dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin,
                    Component.text("==============================", NamedTextColor.GOLD));

            // 全体Titleアニメーションとファンファーレサウンド
            for (Player p : Bukkit.getOnlinePlayers()) {
                showTitle(p,
                        Component.text("地下" + floor + "階 踏破！", NamedTextColor.GOLD),
                        Component.text(name + " が階層ボスを討伐！", NamedTextColor.YELLOW),
                        10, 100, 20);
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

            // 報酬 (ボスの足元)
            for (org.bukkit.inventory.ItemStack reward :
                    plugin.getDungeonBossSystem().getBossLoot(floor, deepestFloor)) {
                entity.getLocation().getWorld().dropItemNaturally(entity.getLocation(), reward);
            }

            if (!dungeonCompleted) {
                plugin.getDungeonBuilder().unlockNextFloor(floor);
                int nextFloor = floor + 1;
                Location dungeonCenter = manager.getCenter();
                Location nextBossLocation = new Location(dungeonCenter.getWorld(),
                        dungeonCenter.getBlockX() + 1, manager.getFloorBaseY(nextFloor) + 1,
                        dungeonCenter.getBlockZ() + 1);
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> plugin.getDungeonBossSystem().spawnBoss(nextBossLocation, nextFloor), 40L);
            }

            // 最下層攻略時だけ、迷宮内にいるプレイヤーを退避させる
            Location center = manager.getCenter();
            if (dungeonCompleted && center != null) {
                // 入口付近 (南側に少し離れた位置を暫定出口とする)
                Location exitLoc = center.clone().add(0, 0, -35); // 入口案内の近く
                exitLoc.setY(center.getWorld().getHighestBlockYAt(exitLoc) + 1.0);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (manager.isInDungeon(p.getLocation())) {
                        send(p, "迷宮の崩壊が始まります！安全な場所へ転送されます...", NamedTextColor.AQUA);
                        showTitle(p,
                                Component.text("迷宮脱出", NamedTextColor.GOLD),
                                Component.text("入口へ転送中...", NamedTextColor.YELLOW),
                                10, 40, 10);
                        p.teleport(exitLoc);
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                }
            }

            // 統計更新 (暫定的にランクポイント付与)
            stats.updateMaxLevel(killer.getUniqueId(), floor, killer.getName());
            plugin.addEventPointsToRanking(killer.getUniqueId(), 25 + floor * 5, "迷宮 地下" + floor + "階踏破");

            if (!dungeonCompleted)
                return;

            boolean newlyGranted = stats.grantPermanentKeepInventory(killer.getUniqueId(), killer.getName());
            if (newlyGranted) {
                dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin,
                        Component.text("======== [ 迷宮完全踏破特典 ] ========", NamedTextColor.LIGHT_PURPLE));
                dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin,
                        Component.text(killer.getName(), NamedTextColor.GOLD)
                                .append(Component.text(" は死の迷宮を制し、永続キープインベントリを獲得しました！",
                                        NamedTextColor.YELLOW)));
                dev.gonjy.patrolspectator.CameraMessageRouter.send(plugin,
                        Component.text("====================================", NamedTextColor.LIGHT_PURPLE));
                showTitle(killer,
                        Component.text("永続特典を獲得！", NamedTextColor.GOLD),
                        Component.text("今後は死亡してもアイテムと経験値を失いません", NamedTextColor.YELLOW),
                        10, 120, 20);
            }

            // 迷宮の再生成 (少しディレイを置く)
            manager.setBuilt(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                trapRunner.resetState();
                if (!plugin.getDungeonBuilder().buildB1()) {
                    plugin.getLogger().warning("[Dungeon] 迷宮の自動再構築を開始できませんでした。次回起動時に再試行します。");
                    completionResetPending = false;
                }
            }, 200L); // 10秒後
        }
    }

    static boolean isValidBossDefeat(boolean hasPlayerKiller, boolean killerInDungeon, boolean bossInDungeon) {
        return hasPlayerKiller && killerInDungeon && bossInDungeon;
    }

    static boolean shouldBlockSpawnChange(boolean newSpawnInDungeon) {
        return newSpawnInDungeon;
    }

    private static void send(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message, color));
    }

    private static void showTitle(Player player, Component title, Component subtitle,
            int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
                ticksToDuration(fadeInTicks), ticksToDuration(stayTicks), ticksToDuration(fadeOutTicks))));
    }

    private static Duration ticksToDuration(int ticks) {
        return Duration.ofMillis(Math.max(0, ticks) * 50L);
    }

    static void applyPermanentKeepInventory(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();
    }
}
