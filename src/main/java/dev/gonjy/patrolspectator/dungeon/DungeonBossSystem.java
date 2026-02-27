package dev.gonjy.patrolspectator.dungeon;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Random;

public class DungeonBossSystem {
    private final Random random = new Random();

    /**
     * 指定された場所にランダムなボスをスポーンさせる
     */
    public void spawnBoss(Location loc) {
        int choice = random.nextInt(3);
        LivingEntity boss;
        String bossName;

        switch (choice) {
            case 0:
                boss = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.PIGLIN_BRUTE);
                bossName = ChatColor.GOLD + "迷宮の狂守護者 (Piglin Brute)";
                setupBossAttributes(boss, bossName, 100.0, 10.0);
                break;
            case 1:
                boss = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.WARDEN);
                bossName = ChatColor.DARK_AQUA + "深淵の這い寄る影 (Warden)";
                setupBossAttributes(boss, bossName, 500.0, 30.0);
                break;
            default:
                boss = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.WITHER);
                bossName = ChatColor.GRAY + "死の宣告者 (Wither)";
                setupBossAttributes(boss, bossName, 300.0, 15.0);
                break;
        }

        loc.getWorld().strikeLightningEffect(loc);
    }

    private void setupBossAttributes(LivingEntity boss, String name, double health, double damage) {
        boss.setCustomName(name);
        boss.setCustomNameVisible(true);

        // ボス識別タグの付与
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(DungeonBossSystem.class), "is_dungeon_boss");
        boss.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);

        if (boss.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
            boss.setHealth(health);
        }
        if (boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
        }
    }

    /**
     * ボス討伐時のレア報酬生成
     */
    public ItemStack getBossLoot() {
        ItemStack item = new ItemStack(Material.NETHERITE_INGOT, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "迷宮主の核");
            item.setItemMeta(meta);
        }
        return item;
    }
}
