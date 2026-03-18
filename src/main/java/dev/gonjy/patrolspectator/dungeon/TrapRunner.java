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

        int range = 28; // 迷宮の端(30)ギリギリより少し内側
        double newX = center.getX() + (random.nextDouble() * range * 2) - range;
        double newZ = center.getZ() + (random.nextDouble() * range * 2) - range;

        Location target = new Location(center.getWorld(), newX, p.getLocation().getY(), newZ, p.getLocation().getYaw(),
                p.getLocation().getPitch());
        p.teleport(target);
        p.sendMessage(ChatColor.RED + "「壁の中にいる！！」");
    }

    private void runMobTrap(Player p, Location loc) {
        p.sendMessage(ChatColor.DARK_RED + "けたたましい警報音が鳴り響き、転送陣が開いた！【モンスターハウス】");

        EntityType[] mobTypes = { EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.ENDERMAN };
        int spawnCount = 4 + random.nextInt(3); // 4〜6体一気にスポーン

        for (int i = 0; i < spawnCount; i++) {
            Location spawnLoc = loc.clone().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            EntityType type = mobTypes[random.nextInt(mobTypes.length)];
            loc.getWorld().spawnEntity(spawnLoc, type);
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

        Location target = p.getLocation().clone().add(0, -5, 0);
        p.teleport(target);

        // 周囲にパーティクル
        loc.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, loc, 50, 0.5, 0.5, 0.5,
                Material.STONE.createBlockData());
    }
}
