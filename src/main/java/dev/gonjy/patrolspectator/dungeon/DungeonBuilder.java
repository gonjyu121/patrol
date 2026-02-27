package dev.gonjy.patrolspectator.dungeon;

import dev.gonjy.patrolspectator.PatrolSpectatorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class DungeonBuilder {
    private final PatrolSpectatorPlugin plugin;
    private final DungeonManager manager;

    public DungeonBuilder(PatrolSpectatorPlugin plugin, DungeonManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * B1階層の生成を開始 (分割設置)
     */
    public void buildB1() {
        Location center = manager.getCenter();
        if (center == null)
            return;

        World world = center.getWorld();
        int size = 60;
        int half = size / 2;
        int startX = center.getBlockX() - half;
        int startZ = center.getBlockZ() - half;
        int baseY = center.getBlockY();

        List<Location> wallLocs = new ArrayList<>();

        final World finalWorld = world;
        final int finalStartX = startX;
        final int finalBaseY = baseY;
        final int finalStartZ = startZ;
        final int finalSize = size;

        org.bukkit.scheduler.BukkitRunnable calcTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                // 床 (baseY-1)
                for (int x = 0; x <= finalSize; x++) {
                    for (int z = 0; z <= finalSize; z++) {
                        wallLocs.add(new Location(finalWorld, finalStartX + x, finalBaseY - 1, finalStartZ + z));
                    }
                }
                // 天井 (baseY+4)
                for (int x = 0; x <= finalSize; x++) {
                    for (int z = 0; z <= finalSize; z++) {
                        wallLocs.add(new Location(finalWorld, finalStartX + x, finalBaseY + 4, finalStartZ + z));
                    }
                }
                // 外壁
                for (int y = 0; y < 4; y++) {
                    for (int i = 0; i <= finalSize; i++) {
                        wallLocs.add(new Location(finalWorld, finalStartX + i, finalBaseY + y, finalStartZ)); // 北
                        wallLocs.add(
                                new Location(finalWorld, finalStartX + i, finalBaseY + y, finalStartZ + finalSize)); // 南
                        wallLocs.add(new Location(finalWorld, finalStartX, finalBaseY + y, finalStartZ + i)); // 西
                        wallLocs.add(
                                new Location(finalWorld, finalStartX + finalSize, finalBaseY + y, finalStartZ + i)); // 東
                    }
                }

                // 内部を一度すべて石で埋める
                for (int y = 0; y < 4; y++) {
                    for (int x = 1; x < finalSize; x++) {
                        for (int z = 1; z < finalSize; z++) {
                            wallLocs.add(new Location(finalWorld, finalStartX + x, finalBaseY + y, finalStartZ + z));
                        }
                    }
                }

                // 計算完了後、メインスレッドに戻して設置タスクを開始
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        // 分割設置タスクの開始 (1tick 500ブロック)
                        incrementalFill(wallLocs, Material.BEDROCK, "B1 外殻・充填生成", () -> {
                            // 岩盤設置が終わったら通路と部屋を掘る
                            digMaze(finalWorld, finalStartX, finalBaseY, finalStartZ, finalSize);
                        });
                    }
                }.runTask(plugin);
            }
        };

        calcTask.runTaskAsynchronously(plugin);
    }

    private void digMaze(World world, int startX, int baseY, int startZ, int size) {
        List<Location> airLocs = new ArrayList<>();
        final World finalWorld = world;
        final int finalStartX = startX;
        final int finalBaseY = baseY;
        final int finalStartZ = startZ;
        final int finalSize = size;

        org.bukkit.scheduler.BukkitRunnable digCalcTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                // 簡易的なグリッド通路 (10マスおき)
                for (int i = 10; i < finalSize; i += 10) {
                    for (int j = 1; j < finalSize; j++) {
                        airLocs.add(new Location(finalWorld, finalStartX + i, finalBaseY, finalStartZ + j));
                        airLocs.add(new Location(finalWorld, finalStartX + i, finalBaseY + 1, finalStartZ + j));
                        airLocs.add(new Location(finalWorld, finalStartX + j, finalBaseY, finalStartZ + i));
                        airLocs.add(new Location(finalWorld, finalStartX + j, finalBaseY + 1, finalStartZ + i));
                    }
                }
                // 部屋の配置 (適当な数カ所)
                addRoom(airLocs, finalWorld, finalStartX + 5, finalBaseY, finalStartZ + 5, 5, 5);
                addRoom(airLocs, finalWorld, finalStartX + 45, finalBaseY, finalStartZ + 10, 8, 8);
                addRoom(airLocs, finalWorld, finalStartX + 10, finalBaseY, finalStartZ + 40, 6, 6);
                addRoom(airLocs, finalWorld, finalStartX + 35, finalBaseY, finalStartZ + 40, 10, 10);

                // 最下層ボスルーム (中心付近)
                addRoom(airLocs, finalWorld, finalStartX + 25, finalBaseY, finalStartZ + 25, 12, 12);

                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        incrementalFill(airLocs, Material.AIR, "B1 通路・部屋掘削", () -> {
                            // 地下水脈の生成
                            generateWaterVeins(finalWorld, finalStartX, finalBaseY, finalStartZ);
                            // 部屋の中に宝箱を置く
                            placeChests(finalWorld, finalStartX, finalBaseY, finalStartZ);
                            // ボスの配置 (ボスルーム中心)
                            plugin.getDungeonBossSystem().spawnBoss(
                                    new Location(finalWorld, finalStartX + 31, finalBaseY + 1, finalStartZ + 31));
                            // 入口の案内掲示
                            placeEntranceSigns(finalWorld, manager.getCenter());
                        });
                    }
                }.runTask(plugin);
            }
        };

        digCalcTask.runTaskAsynchronously(plugin);
    }

    private void generateWaterVeins(World world, int startX, int baseY, int startZ) {
        java.util.Random rand = new java.util.Random();
        for (int i = 0; i < 8; i++) {
            int rx = startX + 5 + rand.nextInt(50);
            int rz = startZ + 5 + rand.nextInt(50);
            // 2x2 の小さなたまり
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    world.getBlockAt(rx + dx, baseY, rz + dz).setType(Material.WATER);
                }
            }

            // 演出：水辺に死体（ドクロ）と手記を置く (1箇所目だけ or 確率)
            if (i == 0) {
                Location skullLoc = new Location(world, rx - 1, baseY, rz);
                skullLoc.getBlock().setType(Material.SKELETON_SKULL);

                Location noteLoc = new Location(world, rx - 1, baseY, rz - 1);
                noteLoc.getBlock().setType(Material.CHEST);
                org.bukkit.block.Chest chest = (org.bukkit.block.Chest) noteLoc.getBlock().getState();
                chest.getInventory().addItem(plugin.getDungeonLootSystem().createDeadMansJournal());
            }
        }
    }

    private void placeChests(World world, int startX, int baseY, int startZ) {
        DungeonLootSystem lootSystem = plugin.getDungeonLootSystem();
        // 部屋の座標に合わせて宝箱を設置
        Location[] chestLocs = {
                new Location(world, startX + 7, baseY, startZ + 7),
                new Location(world, startX + 48, baseY, startZ + 13),
                new Location(world, startX + 12, baseY, startZ + 42),
                new Location(world, startX + 40, baseY, startZ + 45)
        };

        for (Location loc : chestLocs) {
            loc.getBlock().setType(Material.CHEST);
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) loc.getBlock().getState();
            for (org.bukkit.inventory.ItemStack item : lootSystem.generateLoot()) {
                chest.getInventory().addItem(item);
            }
        }
        plugin.getLogger().info("[Dungeon] 宝箱と地下水路が整備されました。");
    }

    private void addRoom(List<Location> airLocs, World world, int x, int y, int z, int w, int d) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                for (int h = 0; h < 3; h++) {
                    airLocs.add(new Location(world, x + i, y + h, z + j));
                }
            }
        }
    }

    private void placeEntranceSigns(World world, Location center) {
        Location signLoc = center.clone().add(0, 0, -2); // 入口の少し手前
        signLoc.getBlock().setType(Material.OAK_SIGN);
        org.bukkit.block.Sign sign = (org.bukkit.block.Sign) signLoc.getBlock().getState();
        sign.setLine(0, ChatColor.DARK_RED + "[ 死の迷宮 ]");
        sign.setLine(1, ChatColor.BLACK + "奥地に超レア報酬");
        sign.setLine(2, ChatColor.RED + "死はアイテム散布");
        sign.setLine(3, ChatColor.DARK_BLUE + "ボス討伐で新生");
        sign.update();

        Location ruleLoc = signLoc.clone().add(1, 0, 0);
        ruleLoc.getBlock().setType(Material.OAK_SIGN);
        org.bukkit.block.Sign ruleSign = (org.bukkit.block.Sign) ruleLoc.getBlock().getState();
        ruleSign.setLine(0, ChatColor.BOLD + "攻略の鍵");
        ruleSign.setLine(1, "水には近づくな");
        ruleSign.setLine(2, "宝箱には罠あり");
        ruleSign.setLine(3, "命を大事に");
        ruleSign.update();
    }

    private void incrementalFill(List<Location> locations, Material material, String taskName, Runnable onComplete) {
        final int blocksPerTick = 500; // 負荷軽減のため1tickあたり500ブロックに変更
        final int total = locations.size();

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                for (int i = 0; i < blocksPerTick && index < total; i++) {
                    Location loc = locations.get(index++);
                    loc.getBlock().setType(material, false);
                }

                if (index >= total) {
                    plugin.getLogger().info("[Dungeon] " + taskName + " 完了 (" + total + " blocks)");
                    if (onComplete != null)
                        onComplete.run();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
