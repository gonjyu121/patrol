package dev.gonjy.patrolspectator.dungeon;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DungeonLootSystem {
    private final Random random = new Random();

    public List<ItemStack> generateLoot() {
        List<ItemStack> loot = new ArrayList<>();

        // 超レア枠 (2%)
        if (random.nextDouble() < 0.02) {
            loot.add(new ItemStack(Material.ELYTRA));
        }

        // レア枠 (10%)
        if (random.nextDouble() < 0.10) {
            Material[] rares = { Material.NETHERITE_INGOT, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                    Material.ENCHANTED_GOLDEN_APPLE };
            loot.add(new ItemStack(rares[random.nextInt(rares.length)]));
        }

        // 普通枠 (50%)
        if (random.nextDouble() < 0.50) {
            Material[] commons = { Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT };
            loot.add(new ItemStack(commons[random.nextInt(commons.length)], random.nextInt(3) + 1));
        }

        // 消耗品枠 (80%)
        if (random.nextDouble() < 0.80) {
            loot.add(new ItemStack(Material.AMETHYST_SHARD, random.nextInt(5) + 1)); // 魔石
        }

        return loot;
    }

    /**
     * 「死者の手記」を生成します。
     */
    public ItemStack createDeadMansJournal() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle(org.bukkit.ChatColor.DARK_RED + "震える手で書かれた日記");
            meta.setAuthor("名もなき冒険者");
            meta.addPage(
                    org.bukkit.ChatColor.DARK_GRAY + "この泉は危険だ…\n\n" +
                            org.bukkit.ChatColor.BLACK + "水面が揺れるたび、あの鋭い槍を持った影が現れる。\n" +
                            "仲間は皆、底へと引きずり込まれた。\n\n" +
                            "もし誰かがこれを読んでいるなら、今すぐ引き返せ。");
            book.setItemMeta(meta);
        }
        return book;
    }

    /**
     * 宝箱の罠判定 (30%)
     */
    public boolean shouldTriggerTrap() {
        return random.nextDouble() < 0.30;
    }
}
