package dev.gonjy.patrolspectator.dungeon;

import dev.gonjy.patrolspectator.PatrolSpectatorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DungeonBuilder {
    static final int ENTRANCE_APPROACH_START_OFFSET = -8;
    static final int ENTRANCE_INTERIOR_END_OFFSET = 1;
    static final double ENTRANCE_CAMERA_Z_OFFSET = -6.5;
    static final double ENTRANCE_CAMERA_Y_OFFSET = 0.2;

    private final PatrolSpectatorPlugin plugin;
    private final DungeonManager manager;
    private final AtomicBoolean building = new AtomicBoolean(false);

    public DungeonBuilder(PatrolSpectatorPlugin plugin, DungeonManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** 現在地の最低高度まで、収まる階層をすべて分割生成します。 */
    public boolean buildB1() {
        if (!building.compareAndSet(false, true)) {
            plugin.getLogger().warning("[Dungeon] 迷宮は既に再構築中です。重複した生成要求を無視します。");
            return false;
        }

        Location center = manager.getCenter();
        if (center == null || center.getWorld() == null) {
            building.set(false);
            return false;
        }

        if (manager.overlapsWorldSpawn()) {
            plugin.getLogger().warning("[Dungeon] 初期リスポーン地点に近いため生成を中止しました。/dungeon setcenter で離れた場所を指定してください。");
            building.set(false);
            return false;
        }

        cleanupEntities(center);

        World world = center.getWorld();
        int size = 60;
        int half = size / 2;
        int startX = center.getBlockX() - half;
        int startZ = center.getBlockZ() - half;
        int baseY = center.getBlockY();
        int floorCount = manager.getFloorCount();

        List<Location> wallLocs = new ArrayList<>();

        final World finalWorld = world;
        final int finalStartX = startX;
        final int finalBaseY = baseY;
        final int finalStartZ = startZ;
        final int finalSize = size;

        org.bukkit.scheduler.BukkitRunnable calcTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (int floor = 1; floor <= floorCount; floor++) {
                    int floorY = finalBaseY - ((floor - 1) * DungeonManager.FLOOR_HEIGHT);
                    for (int x = 0; x <= finalSize; x++) {
                        for (int z = 0; z <= finalSize; z++) {
                            wallLocs.add(new Location(finalWorld, finalStartX + x, floorY - 1, finalStartZ + z));
                            wallLocs.add(new Location(finalWorld, finalStartX + x, floorY + 4, finalStartZ + z));
                        }
                    }
                    for (int y = 0; y < 4; y++) {
                        for (int x = 0; x <= finalSize; x++) {
                            for (int z = 0; z <= finalSize; z++) {
                                wallLocs.add(new Location(finalWorld, finalStartX + x, floorY + y, finalStartZ + z));
                            }
                        }
                    }
                }

                // 計算完了後、メインスレッドに戻して設置タスクを開始
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        // 分割設置タスクの開始 (1tick 500ブロック)
                        incrementalFill(wallLocs, Material.BEDROCK, floorCount + "階層 外殻・充填生成", () -> {
                            // 岩盤設置が終わったら通路と部屋を掘る
                            digMaze(finalWorld, finalStartX, finalBaseY, finalStartZ, finalSize, floorCount);
                        });
                    }
                }.runTask(plugin);
            }
        };

        calcTask.runTaskAsynchronously(plugin);
        return true;
    }

    private void digMaze(World world, int startX, int baseY, int startZ, int size, int floorCount) {
        List<Location> airLocs = new ArrayList<>();
        final World finalWorld = world;
        final int finalStartX = startX;
        final int finalBaseY = baseY;
        final int finalStartZ = startZ;
        final int finalSize = size;

        org.bukkit.scheduler.BukkitRunnable digCalcTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (int floor = 1; floor <= floorCount; floor++) {
                    int floorY = finalBaseY - ((floor - 1) * DungeonManager.FLOOR_HEIGHT);
                    for (int i = 10; i < finalSize; i += 10) {
                        for (int j = 1; j < finalSize; j++) {
                            for (int h = 0; h < 3; h++) {
                                airLocs.add(new Location(finalWorld, finalStartX + i, floorY + h, finalStartZ + j));
                                airLocs.add(new Location(finalWorld, finalStartX + j, floorY + h, finalStartZ + i));
                            }
                        }
                    }
                    addRoom(airLocs, finalWorld, finalStartX + 5, floorY, finalStartZ + 5, 5, 5);
                    addRoom(airLocs, finalWorld, finalStartX + 45, floorY, finalStartZ + 10, 8, 8);
                    addRoom(airLocs, finalWorld, finalStartX + 10, floorY, finalStartZ + 40, 6, 6);
                    addRoom(airLocs, finalWorld, finalStartX + 35, floorY, finalStartZ + 40, 10, 10);
                    addRoom(airLocs, finalWorld, finalStartX + 25, floorY, finalStartZ + 25, 12, 12);
                    if (floor < floorCount) {
                        addDescentShaft(airLocs, finalWorld, finalStartX + 10, floorY, finalStartZ + 10);
                    }
                }

                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        incrementalFill(airLocs, Material.AIR, floorCount + "階層 通路・部屋掘削", () -> {
                            for (int floor = 1; floor <= floorCount; floor++) {
                                int floorY = finalBaseY - ((floor - 1) * DungeonManager.FLOOR_HEIGHT);
                                generateWaterVeins(finalWorld, finalStartX, floorY, finalStartZ);
                                placeChests(finalWorld, finalStartX, floorY, finalStartZ, floor, floorCount);
                                // 同じチャンクに全階ボスを常駐させない。最初はB1のみ生成し、以降は攻略時に出現させる。
                                if (floor == 1) {
                                    plugin.getDungeonBossSystem().spawnBoss(
                                            new Location(finalWorld, finalStartX + 31, floorY + 1, finalStartZ + 31), floor);
                                }
                                if (floor < floorCount) {
                                    placeDescentLadder(finalWorld, finalStartX + 10, floorY, finalStartZ + 10);
                                }
                            }
                            // 入口のくり抜きと門・看板の整備
                            buildEntranceGate(finalWorld, finalStartX, finalBaseY, finalStartZ);
                            
                            // 観光案内へダンジョン最下層を動的に登録
                            int deepestY = finalBaseY - ((floorCount - 1) * DungeonManager.FLOOR_HEIGHT);
                            Location bossLoc = new Location(finalWorld, finalStartX + 31, deepestY + 1, finalStartZ + 31);
                            dev.gonjy.patrolspectator.TouristLocation bossTourLoc = new dev.gonjy.patrolspectator.TouristLocation(
                                    "auto_dungeon_boss",
                                    "§4死の迷宮 - 最下層(B" + floorCount + ")",
                                    finalWorld.getName(),
                                    bossLoc.getX() - 5.0, bossLoc.getY() + 3.0, bossLoc.getZ() - 5.0,
                                    -45f, 20f,
                                    "Death Dungeon Boss Room",
                                    "overworld",
                                    null, null
                            );
                            plugin.getPatrolManager().addTouristLocation(bossTourLoc);

                            // 生成完了フラグを保存（次回起動時の重複生成を防ぐ）
                            manager.setBuilt(true);
                            building.set(false);
                            plugin.getLogger().info("[Dungeon] B1〜B" + floorCount + " の生成が完了しました。built=true を保存しました。");
                        });
                    }
                }.runTask(plugin);
            }
        };

        digCalcTask.runTaskAsynchronously(plugin);
    }

    private void addDescentShaft(List<Location> airLocs, World world, int x, int upperBaseY, int z) {
        int lowerBaseY = upperBaseY - DungeonManager.FLOOR_HEIGHT;
        for (int y = lowerBaseY; y <= upperBaseY + 2; y++) {
            airLocs.add(new Location(world, x, y, z));
            airLocs.add(new Location(world, x, y, z + 1));
        }
    }

    private void placeDescentLadder(World world, int x, int upperBaseY, int z) {
        int lowerBaseY = upperBaseY - DungeonManager.FLOOR_HEIGHT;
        for (int y = lowerBaseY; y <= upperBaseY; y++) {
            org.bukkit.block.Block support = world.getBlockAt(x - 1, y, z);
            support.setType(Material.BEDROCK, false);
            org.bukkit.block.Block ladder = world.getBlockAt(x, y, z);
            ladder.setType(Material.LADDER, false);
            if (ladder.getBlockData() instanceof org.bukkit.block.data.type.Ladder data) {
                data.setFacing(org.bukkit.block.BlockFace.EAST);
                ladder.setBlockData(data, false);
            }
        }
        // 階層ボスを倒すまでは入口を不可視壁で封鎖する。
        world.getBlockAt(x, upperBaseY, z).setType(Material.BARRIER, false);
        world.getBlockAt(x, upperBaseY, z + 1).setType(Material.BARRIER, false);
    }

    public void unlockNextFloor(int clearedFloor) {
        Location center = manager.getCenter();
        if (center == null || center.getWorld() == null || clearedFloor >= manager.getFloorCount())
            return;
        int x = center.getBlockX() - 20;
        int z = center.getBlockZ() - 20;
        int y = manager.getFloorBaseY(clearedFloor);
        org.bukkit.block.Block ladder = center.getWorld().getBlockAt(x, y, z);
        ladder.setType(Material.LADDER, false);
        if (ladder.getBlockData() instanceof org.bukkit.block.data.type.Ladder data) {
            data.setFacing(org.bukkit.block.BlockFace.EAST);
            ladder.setBlockData(data, false);
        }
        center.getWorld().strikeLightningEffect(new Location(center.getWorld(), x, y, z));
    }

    public boolean isBuilding() {
        return building.get();
    }

    /**
     * 北側正面にダンジョンの出入口（アーチ門・看板・壁くり抜き）を構築します。
     */
    public void buildEntranceGate(World world, int startX, int baseY, int startZ) {
        int centerX = startX + 30; // 60x60 の中央（グリッド通路 X=startX+30 と直結）
        int entranceZ = startZ;    // 北側の外壁 (Z=startZ)

        // 1. 観光カメラ位置から内部まで、3ブロック高の進入路を確実に開通させる。
        carveEntranceApproach(world, centerX, baseY, entranceZ);

        // 2. アーチ状の門枠を装飾 (Z = entranceZ - 2)
        int gateZ = entranceZ - 2;
        for (int y = baseY; y <= baseY + 3; y++) {
            world.getBlockAt(centerX - 2, y, gateZ).setType(Material.CHISELED_DEEPSLATE);
            world.getBlockAt(centerX + 2, y, gateZ).setType(Material.CHISELED_DEEPSLATE);
        }
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            world.getBlockAt(x, baseY + 3, gateZ).setType(Material.CHISELED_DEEPSLATE);
        }
        world.getBlockAt(centerX - 2, baseY + 4, gateZ).setType(Material.SOUL_LANTERN);
        world.getBlockAt(centerX + 2, baseY + 4, gateZ).setType(Material.SOUL_LANTERN);

        // 3. 看板の設置 (左右の柱)
        Location leftSignLoc = new Location(world, centerX - 2, baseY + 1, gateZ - 1);
        leftSignLoc.getBlock().setType(Material.OAK_WALL_SIGN);
        if (leftSignLoc.getBlock().getState() instanceof org.bukkit.block.Sign) {
            org.bukkit.block.Sign sign = (org.bukkit.block.Sign) leftSignLoc.getBlock().getState();
            if (sign.getBlockData() instanceof org.bukkit.block.data.type.WallSign) {
                org.bukkit.block.data.type.WallSign wallSign = (org.bukkit.block.data.type.WallSign) sign.getBlockData();
                wallSign.setFacing(org.bukkit.block.BlockFace.NORTH);
                sign.setBlockData(wallSign);
            }
            sign.setLine(0, ChatColor.DARK_RED + "☠ [死の迷宮] ☠");
            sign.setLine(1, ChatColor.RED + "死はアイテム散布");
            sign.setLine(2, ChatColor.GOLD + "最深部に超レア宝箱");
            sign.setLine(3, ChatColor.DARK_PURPLE + "ボス討伐でクリア");
            sign.update();
        }

        Location rightSignLoc = new Location(world, centerX + 2, baseY + 1, gateZ - 1);
        rightSignLoc.getBlock().setType(Material.OAK_WALL_SIGN);
        if (rightSignLoc.getBlock().getState() instanceof org.bukkit.block.Sign) {
            org.bukkit.block.Sign sign = (org.bukkit.block.Sign) rightSignLoc.getBlock().getState();
            if (sign.getBlockData() instanceof org.bukkit.block.data.type.WallSign) {
                org.bukkit.block.data.type.WallSign wallSign = (org.bukkit.block.data.type.WallSign) sign.getBlockData();
                wallSign.setFacing(org.bukkit.block.BlockFace.NORTH);
                sign.setBlockData(wallSign);
            }
            sign.setLine(0, ChatColor.GOLD + "【 攻略心得 】");
            sign.setLine(1, ChatColor.DARK_GRAY + "・ベッド設置不可");
            sign.setLine(2, ChatColor.DARK_GRAY + "・水辺の罠に注意");
            sign.setLine(3, ChatColor.DARK_GRAY + "・奪い合い自由");
            sign.update();
        }

        // 4. 観光案内 (PatrolManager) へ正面入口座標を登録
        Location entranceLoc = createEntranceCameraLocation(world, centerX, baseY, entranceZ);
        dev.gonjy.patrolspectator.TouristLocation entranceTourLoc = new dev.gonjy.patrolspectator.TouristLocation(
                "auto_dungeon_entrance",
                "§4死の迷宮 - 正面入口",
                world.getName(),
                entranceLoc.getX(), entranceLoc.getY(), entranceLoc.getZ(),
                0f, 15f,
                "Death Dungeon North Entrance Gate",
                "overworld",
                null, null
        );
        plugin.getPatrolManager().addTouristLocation(entranceTourLoc);

        plugin.getLogger().info("[Dungeon] 北側正面入口門（アーチ・看板・壁くり抜き）を生成しました。");
    }

    static void carveEntranceApproach(World world, int centerX, int baseY, int entranceZ) {
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int zOffset = ENTRANCE_APPROACH_START_OFFSET;
                    zOffset <= ENTRANCE_INTERIOR_END_OFFSET; zOffset++) {
                int z = entranceZ + zOffset;
                world.getBlockAt(x, baseY - 1, z).setType(Material.POLISHED_BLACKSTONE, false);
                for (int y = baseY; y <= baseY + 2; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    static Location createEntranceCameraLocation(World world, int centerX, int baseY, int entranceZ) {
        return new Location(world, centerX + 0.5, baseY + ENTRANCE_CAMERA_Y_OFFSET,
                entranceZ + ENTRANCE_CAMERA_Z_OFFSET, 0f, 15f);
    }

    public static Location createEntranceCameraLocation(Location center) {
        if (center == null || center.getWorld() == null)
            return null;
        return createEntranceCameraLocation(center.getWorld(), center.getBlockX(), center.getBlockY(),
                center.getBlockZ() - 30);
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

    private void placeChests(World world, int startX, int baseY, int startZ, int floor, int floorCount) {
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
            for (org.bukkit.inventory.ItemStack item : lootSystem.generateLoot(floor, floorCount)) {
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

    private void cleanupEntities(Location center) {
        int half = 30; // DUNGEON_SIZE / 2
        double downwardRange = Math.max(10, (manager.getFloorCount() - 1) * DungeonManager.FLOOR_HEIGHT + 5);
        center.getWorld().getNearbyEntities(center, half, downwardRange, half).forEach(entity -> {
            if (!(entity instanceof org.bukkit.entity.Player)) {
                entity.remove();
            }
        });
        plugin.getLogger().info("[Dungeon] 迷宮内のエンティティをクリーニングしました。");
    }
}
