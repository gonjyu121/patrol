package dev.gonjy.patrolspectator.dungeon;

import dev.gonjy.patrolspectator.PatrolSpectatorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

public class DungeonListener implements Listener {

    private final PatrolSpectatorPlugin plugin;
    private final DungeonManager manager;
    private final DungeonStatsStorage stats;
    private final TrapRunner trapRunner;

    public DungeonListener(PatrolSpectatorPlugin plugin, DungeonManager manager, DungeonStatsStorage stats,
            TrapRunner trapRunner) {
        this.plugin = plugin;
        this.manager = manager;
        this.stats = stats;
        this.trapRunner = trapRunner;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (manager.isInDungeon(event.getBlock().getLocation())) {
            if (!event.getPlayer().isOp()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "迷宮の壁を傷つけることはできません…");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (manager.isInDungeon(event.getBlock().getLocation())) {
            if (!event.getPlayer().isOp()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "迷宮を汚すことは許されません…");
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLoc = player.getLocation();

        // 早期リターン: 迷宮外なら何もしない
        if (!manager.isInDungeon(deathLoc))
            return;

        // 血のカウント加算
        stats.incrementGlobalDeathCount();
        long totalDeaths = stats.getGlobalDeathCount();

        // メッセージ表示 (Wizardry風)
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "======== [ 迷宮の供物 ] ========");
        Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " が死の迷宮の糧となりました…");
        Bukkit.broadcastMessage(
                ChatColor.GRAY + "この迷宮は既に " + ChatColor.RED + totalDeaths + ChatColor.GRAY + " 人の血を吸いました…");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "================================");

        // 死亡時の雷エフェクトと全体サウンドパニック演出
        deathLoc.getWorld().strikeLightningEffect(deathLoc);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);
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
            double distSq = loc.distanceSquared(manager.getCenter());
            if (distSq > 50 * 50)
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
                    p.sendMessage(ChatColor.RED + "迷宮の外へ逃げることはできません…");
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
                p.sendMessage(ChatColor.RED + "宝箱に仕掛けられた爆弾が爆発した！");
                break;
            case 1: // 毒・衰弱
                p.addPotionEffect(
                        new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 200, 1));
                p.addPotionEffect(
                        new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER, 200, 1));
                p.sendMessage(ChatColor.DARK_PURPLE + "宝箱から毒ガスが噴き出した！");
                break;
            case 2: // 奇襲 (MobTrapを流用)
                trapRunner.triggerTrap(p, loc); // Mobなどの既存Trapを発動
                p.sendMessage(ChatColor.DARK_RED + "宝箱を開ける音を聞きつけ、魔物が集まってきた！");
                break;
        }
    }

    @EventHandler
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        org.bukkit.entity.LivingEntity entity = event.getEntity();
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "is_dungeon_boss");
        if (entity.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
            // ボスが倒された！
            Player killer = entity.getKiller();
            String name = (killer != null) ? killer.getName() : "誰か";

            Bukkit.broadcastMessage(ChatColor.GOLD + "======== [ 迷宮踏破 ] ========");
            Bukkit.broadcastMessage(ChatColor.YELLOW + name + " が迷宮の守護者 " + ChatColor.RED + entity.getCustomName()
                    + ChatColor.YELLOW + " を討伐しました！");
            Bukkit.broadcastMessage(ChatColor.AQUA + "迷宮の魔力が霧散し、構造が再構築され始めます…");
            Bukkit.broadcastMessage(ChatColor.GOLD + "==============================");

            // 全体Titleアニメーションとファンファーレサウンド
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(ChatColor.GOLD + "迷宮踏破！",
                        ChatColor.YELLOW + name + " が " + entity.getCustomName() + " を討伐！",
                        10, 100, 20);
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

            // 報酬 (ボスの足元)
            entity.getLocation().getWorld().dropItemNaturally(entity.getLocation(),
                    plugin.getDungeonBossSystem().getBossLoot());

            // 迷宮内にいるプレイヤーを退避させる (SAO style extraction)
            Location center = manager.getCenter();
            if (center != null) {
                // 入口付近 (南側に少し離れた位置を暫定出口とする)
                Location exitLoc = center.clone().add(0, 0, -35); // 入口案内の近く
                exitLoc.setY(center.getWorld().getHighestBlockYAt(exitLoc) + 1.0);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (manager.isInDungeon(p.getLocation())) {
                        p.sendMessage(ChatColor.AQUA + "迷宮の崩壊が始まります！安全な場所へ転送されます...");
                        p.sendTitle(ChatColor.GOLD + "迷宮脱出", ChatColor.YELLOW + "入口へ転送中...", 10, 40, 10);
                        p.teleport(exitLoc);
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                }
            }

            // 統計更新 (暫定的にランクポイント付与)
            if (killer != null) {
                plugin.addEventPointsToRanking(killer.getUniqueId(), 100, "迷宮ボス討伐");
            }

            // 迷宮の再生成 (少しディレイを置く)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getDungeonBuilder().buildB1();
            }, 200L); // 10秒後
        }
    }
}
