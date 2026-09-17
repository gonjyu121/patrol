package dev.gonjy.patrolspectator.dungeon;

import dev.gonjy.patrolspectator.PatrolSpectatorPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TrapRunner {
    private final PatrolSpectatorPlugin plugin;
    private final DungeonManager manager;
    private final Map<UUID, Long> cooldowns = new HashMap<>(); // 全体クールダウン
    private final Random random = new Random();

    public TrapRunner(PatrolSpectatorPlugin plugin, DungeonManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void triggerTrap(Player p, Location triggerLoc) {
        long now = System.currentTimeMillis();
        if (cooldowns.getOrDefault(p.getUniqueId(), 0L) > now)
            return;

        cooldowns.put(p.getUniqueId(), now + 30000); // 30秒クールダウン

        int choice = random.nextInt(16); // 分岐をさらに増やす
        if (choice < 3) {
            runTeleportTrap(p);
        } else if (choice < 6) {
            runMobTrap(p, triggerLoc);
        } else if (choice < 9) {
            runWaterTrap(p, triggerLoc);
        } else if (choice < 12) {
            runDebuffTrap(p);
        } else if (choice < 14) {
            runExplosionTrap(p, triggerLoc);
        } else {
            runPitfallTrap(p, triggerLoc);
        }
    }

    private void runTeleportTrap(Player p) {
        p.sendMessage(ChatColor.DARK_PURPLE + "足元の魔方陣が光り輝き、空間が歪む…！");
        Location center = manager.getCenter();
        if (center == null)
            return;

        Location target = findSafeLocation(center, p.getLocation(), 28, 2);
        if (target == null) {
            p.sendMessage(ChatColor.GRAY + "魔方陣は行き先を見失い、静かに消えた…。");
            return;
        }
        target.setYaw(p.getLocation().getYaw());
        target.setPitch(p.getLocation().getPitch());
        p.teleport(target);
        p.sendMessage(ChatColor.RED + "迷宮の別の通路へ飛ばされた！");
    }

    private void runMobTrap(Player p, Location loc) {
        p.sendMessage(ChatColor.DARK_RED + "けたたましい警報音が鳴り響き、転送陣が開いた！【モンスターハウス】");

        int floor = Math.max(1, manager.getFloor(loc));
        int floorCount = manager.getFloorCount();
        double progress = DungeonLootSystem.progression(floor, floorCount);
        EntityType[] mobTypes = progress < 0.34
                ? new EntityType[] { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER }
                : progress < 0.67
                        ? new EntityType[] { EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.ENDERMAN }
                        : new EntityType[] { EntityType.PIGLIN_BRUTE, EntityType.VINDICATOR, EntityType.EVOKER,
                                EntityType.WITHER_SKELETON };
        int spawnCount = 2 + (int) Math.floor(progress * 3.0) + random.nextInt(2);

        for (int i = 0; i < spawnCount; i++) {
            EntityType type = mobTypes[random.nextInt(mobTypes.length)];
            Location spawnLoc = findSafeLocation(manager.getCenter(), loc, 5, 3);
            if (spawnLoc == null)
                continue;
            LivingEntity mob = (LivingEntity) loc.getWorld().spawnEntity(spawnLoc, type);
            strengthenMob(mob, floor, floorCount);
            mob.setCollidable(false);
            mob.setRemoveWhenFarAway(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (mob.isValid())
                        mob.remove();
                }
            }.runTaskLater(plugin, 900L);
        }
    }

    public void runWaterTrap(Player p, Location loc) {
        p.sendMessage(ChatColor.BLUE + "水面が激しく揺れ、三叉槍を構えたドラウンドが這い出してきた！");
        loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_SPLASH, 1.0f, 0.5f);

        for (int i = 0; i < 4; i++) {
            Location spawnLoc = loc.clone().add(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            org.bukkit.entity.Drowned drowned = (org.bukkit.entity.Drowned) loc.getWorld().spawnEntity(spawnLoc,
                    EntityType.DROWNED);

            // 三叉槍を持たせる
            drowned.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.TRIDENT));
            drowned.getEquipment().setItemInMainHandDropChance(0.05f); // 5% でドロップ
            strengthenMob(drowned, Math.max(1, manager.getFloor(loc)), manager.getFloorCount());

            // 30秒後にデスポーン
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (drowned.isValid())
                        drowned.remove();
                }
            }.runTaskLater(plugin, 600L);
        }
    }

    private void runDebuffTrap(Player p) {
        p.sendMessage(ChatColor.GRAY + "どこからともなく不気味な笑い声が聞こえる…");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.8f);

        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 100, 0));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 200, 1));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 200, 1));
    }

    private void runExplosionTrap(Player p, Location loc) {
        p.sendMessage(ChatColor.RED + "導火線の燃える音が聞こえる…！");
        loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);

        new BukkitRunnable() {
            @Override
            public void run() {
                loc.getWorld().createExplosion(loc, 3.0f, false, false);
                p.sendMessage(ChatColor.DARK_RED + "ドカンッ！！");
            }
        }.runTaskLater(plugin, 40L); // 2秒後
    }

    private void runPitfallTrap(Player p, Location loc) {
        p.sendMessage(ChatColor.GOLD + "足元の床が崩れ落ちた！");
        loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);

        // 岩盤で充填された床下へ転送すると窒息して行動不能になるため、落下演出とダメージだけにする。
        p.damage(4.0);
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, 60, 2));

        // 周囲にパーティクル
        loc.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, loc, 50, 0.5, 0.5, 0.5,
                Material.STONE.createBlockData());
    }

    public void resetState() {
        cooldowns.clear();
    }

    private void strengthenMob(LivingEntity mob, int floor, int floorCount) {
        double progress = DungeonLootSystem.progression(floor, floorCount);
        double healthMultiplier = 1.0 + progress * 2.0;
        double damageMultiplier = 1.0 + progress * 1.5;
        org.bukkit.attribute.AttributeInstance health = getSafeAttribute(mob,
                new String[] { "MAX_HEALTH", "GENERIC_MAX_HEALTH" });
        if (health != null) {
            double value = Math.min(2048.0, health.getBaseValue() * healthMultiplier);
            health.setBaseValue(value);
            mob.setHealth(value);
        }
        org.bukkit.attribute.AttributeInstance damage = getSafeAttribute(mob,
                new String[] { "ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE" });
        if (damage != null) {
            damage.setBaseValue(Math.min(100.0, damage.getBaseValue() * damageMultiplier));
        }
        mob.setCustomName(ChatColor.DARK_RED + "地下" + floor + "階の魔物");
        mob.setCustomNameVisible(progress >= 0.67);
    }

    private org.bukkit.attribute.AttributeInstance getSafeAttribute(org.bukkit.attribute.Attributable entity,
            String[] names) {
        for (String name : names) {
            try {
                org.bukkit.attribute.Attribute attribute = org.bukkit.attribute.Attribute.valueOf(name);
                org.bukkit.attribute.AttributeInstance instance = entity.getAttribute(attribute);
                if (instance != null)
                    return instance;
            } catch (IllegalArgumentException ignored) {
                // Paperの世代による列挙名差を吸収する。
            }
        }
        return null;
    }

    Location findSafeLocation(Location center, Location origin, int radius, int requiredHeadroom) {
        if (center == null || center.getWorld() == null || origin == null
                || origin.getWorld() == null || !center.getWorld().equals(origin.getWorld())) {
            return null;
        }

        int attempts = Math.max(32, radius * 4);
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = origin.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
            int z = origin.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
            Location candidate = new Location(origin.getWorld(), x + 0.5,
                    manager.getFloorBaseY(Math.max(1, manager.getFloor(origin))), z + 0.5);
            if (isSafeStandingLocation(candidate, requiredHeadroom) && manager.isInDungeon(candidate)) {
                return candidate;
            }
        }

        // 乱数で見つからない場合は近傍を確実に走査する。
        for (int distance = 0; distance <= radius; distance++) {
            for (int x = origin.getBlockX() - distance; x <= origin.getBlockX() + distance; x++) {
                for (int z = origin.getBlockZ() - distance; z <= origin.getBlockZ() + distance; z++) {
                    if (Math.max(Math.abs(x - origin.getBlockX()), Math.abs(z - origin.getBlockZ())) != distance)
                        continue;
                    Location candidate = new Location(origin.getWorld(), x + 0.5,
                            manager.getFloorBaseY(Math.max(1, manager.getFloor(origin))), z + 0.5);
                    if (isSafeStandingLocation(candidate, requiredHeadroom) && manager.isInDungeon(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    static boolean isSafeStandingLocation(Location location, int requiredHeadroom) {
        if (location == null || location.getWorld() == null || requiredHeadroom < 2)
            return false;
        if (!location.clone().add(0, -1, 0).getBlock().getType().isSolid())
            return false;
        for (int y = 0; y < requiredHeadroom; y++) {
            if (!location.clone().add(0, y, 0).getBlock().getType().isAir())
                return false;
        }
        return true;
    }
}
