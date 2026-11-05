package dev.gonjy.patrolspectator.dungeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class DungeonBossSystem {
    private final Random random = new Random();

    /**
     * 指定された場所にランダムなボスをスポーンさせる
     */
    public void spawnBoss(Location loc) {
        spawnBoss(loc, 1);
    }

    public void spawnBoss(Location loc, int floor) {
        int choice = random.nextInt(3);
        LivingEntity boss;
        Component bossName;

        switch (choice) {
            case 0:
                boss = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.PIGLIN_BRUTE);
                bossName = Component.text("迷宮の狂守護者 (Piglin Brute)", NamedTextColor.GOLD);
                setupBossAttributes(boss, bossName, 100.0 + floor * 8.0, 10.0 + floor * 0.5, floor);
                break;
            case 1:
                boss = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.WARDEN);
                bossName = Component.text("深淵の這い寄る影 (Warden)", NamedTextColor.DARK_AQUA);
                setupBossAttributes(boss, bossName, 500.0 + floor * 12.0, 30.0 + floor * 0.5, floor);
                break;
            default:
                boss = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.WITHER);
                bossName = Component.text("死の宣告者 (Wither)", NamedTextColor.GRAY);
                setupBossAttributes(boss, bossName, 300.0 + floor * 10.0, 15.0 + floor * 0.5, floor);
                break;
        }

        loc.getWorld().strikeLightningEffect(loc);
    }

    private void setupBossAttributes(LivingEntity boss, Component name, double health, double damage, int floor) {
        boss.customName(Component.text("地下" + floor + "階 ", NamedTextColor.DARK_RED).append(name));
        boss.setCustomNameVisible(true);

        // ボス識別タグの付与
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(DungeonBossSystem.class), "is_dungeon_boss");
        boss.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        org.bukkit.NamespacedKey floorKey = new org.bukkit.NamespacedKey(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(DungeonBossSystem.class), "dungeon_floor");
        boss.getPersistentDataContainer().set(floorKey, org.bukkit.persistence.PersistentDataType.INTEGER, floor);

        org.bukkit.attribute.AttributeInstance healthAttr = getSafeAttribute(boss, new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"});
        if (healthAttr != null) {
            healthAttr.setBaseValue(health);
            boss.setHealth(health);
        }
        org.bukkit.attribute.AttributeInstance damageAttr = getSafeAttribute(boss, new String[]{"ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE"});
        if (damageAttr != null) {
            damageAttr.setBaseValue(damage);
        }
    }

    /**
     * ボス討伐時のレア報酬生成
     */
    public ItemStack getBossLoot() {
        return getBossLoot(1, 1).get(0);
    }

    public List<ItemStack> getBossLoot(int floor, int floorCount) {
        List<ItemStack> loot = new ArrayList<>();
        int coreCount = Math.min(3, 1 + Math.max(0, floor - 1) / 10);
        ItemStack item = new ItemStack(Material.NETHERITE_INGOT, coreCount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("迷宮主の核", NamedTextColor.DARK_PURPLE));
            item.setItemMeta(meta);
        }
        loot.add(item);
        loot.add(new ItemStack(Material.DIAMOND, Math.min(12, 1 + floor / 2)));
        if (floor >= Math.max(2, floorCount / 2)) {
            loot.add(new ItemStack(Material.ANCIENT_DEBRIS, 1 + floor / 10));
        }
        if (floor >= floorCount) {
            loot.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2));
            loot.add(new ItemStack(Material.NETHER_STAR, 1));
        }
        return loot;
    }

    private org.bukkit.attribute.AttributeInstance getSafeAttribute(org.bukkit.attribute.Attributable entity, String[] names) {
        for (String name : names) {
            try {
                Attribute attr = dev.gonjy.patrolspectator.BukkitRegistryResolver.attribute(name);

                if (attr != null) {
                    return entity.getAttribute(attr);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
