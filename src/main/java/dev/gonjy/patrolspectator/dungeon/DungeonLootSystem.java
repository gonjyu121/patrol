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
        return generateLoot(1, 1);
    }

    public List<ItemStack> generateLoot(int floor, int floorCount) {
        List<ItemStack> loot = new ArrayList<>();
        double progress = progression(floor, floorCount);

        // 深層ほど超レア・レア枠が伸びる（超レア2〜5%、レア10〜40%）。
        if (random.nextDouble() < 0.02 + progress * 0.03) {
            loot.add(new ItemStack(Material.ELYTRA));
        }

        if (random.nextDouble() < 0.10 + progress * 0.30) {
            Material[] rares = { Material.NETHERITE_INGOT, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                    Material.ENCHANTED_GOLDEN_APPLE };
            loot.add(new ItemStack(rares[random.nextInt(rares.length)], 1 + (progress >= 0.8 ? 1 : 0)));
        }

        if (random.nextDouble() < 0.50 + progress * 0.40) {
            Material[] commons = { Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT };
            loot.add(new ItemStack(commons[random.nextInt(commons.length)],
                    random.nextInt(3) + 1 + (int) Math.floor(progress * 5.0)));
        }

        // 消耗品枠 (80%)
        if (random.nextDouble() < 0.80) {
            loot.add(new ItemStack(Material.AMETHYST_SHARD,
                    random.nextInt(5) + 1 + (int) Math.floor(progress * 8.0))); // 魔石
        }

        if (floor >= floorCount) {
            loot.add(new ItemStack(Material.ANCIENT_DEBRIS, 2));
        }

        return loot;
    }

    static double progression(int floor, int floorCount) {
        if (floorCount <= 1)
            return 1.0;
        return Math.max(0.0, Math.min(1.0, (floor - 1.0) / (floorCount - 1.0)));
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
