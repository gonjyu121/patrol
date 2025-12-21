package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 賞金首システムを管理するクラス
 */
@SuppressWarnings("deprecation")
public class BountyManager implements Listener {

    private final PatrolSpectatorPlugin plugin;
    private final Map<UUID, BountyData> activeBounties = new ConcurrentHashMap<>();

    public BountyManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startExpirationTask();
    }

    /**
     * 賞金首を追加します
     */
    public void addBounty(Player target, double amount, String issuer) {
        if (target == null)
            return;

        // 期限は24時間
        long expiration = System.currentTimeMillis() + (24 * 60 * 60 * 1000L);
        BountyData bounty = new BountyData(target.getUniqueId(), target.getName(), amount, expiration, issuer);
        activeBounties.put(target.getUniqueId(), bounty);

        // ゲーム内通知
        Bukkit.broadcastMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "☠ 新たな賞金首が手配されました！ ☠");
        Bukkit.broadcastMessage(ChatColor.RED + "ターゲット: " + ChatColor.YELLOW + target.getName());
        Bukkit.broadcastMessage(ChatColor.RED + "賞金: " + ChatColor.GOLD + "ダイヤモンド " + (int) amount + "個");
        Bukkit.broadcastMessage(ChatColor.RED + "依頼者: " + ChatColor.GRAY + issuer);
        Bukkit.broadcastMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Discord通知
        sendDiscordAlert(target.getName(), (int) amount, issuer, true);
    }

    /**
     * 賞金首かどうか確認
     */
    public boolean isBounty(UUID uuid) {
        return activeBounties.containsKey(uuid) && !activeBounties.get(uuid).isExpired();
    }

    public BountyData getBounty(UUID uuid) {
        return activeBounties.get(uuid);
    }

    public Map<UUID, BountyData> getActiveBounties() {
        return activeBounties;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (activeBounties.containsKey(victim.getUniqueId())) {
            BountyData bounty = activeBounties.get(victim.getUniqueId());

            if (bounty.isExpired()) {
                activeBounties.remove(victim.getUniqueId());
                return;
            }

            // 賞金首が討ち取られた
            activeBounties.remove(victim.getUniqueId());

            String killerName = (killer != null) ? killer.getName() : "環境ダメージ/モブ";

            // ゲーム内通知
            Bukkit.broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Bukkit.broadcastMessage(ChatColor.GOLD + "⚔ 賞金首が討ち取られました！ ⚔");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "ターゲット: " + victim.getName());
            Bukkit.broadcastMessage(ChatColor.YELLOW + "討伐者: " + ChatColor.RED + killerName);
            Bukkit.broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 報酬付与（キラーがいる場合のみ）
            if (killer != null) {
                int diamondCount = (int) bounty.getAmount();
                killer.getInventory().addItem(new ItemStack(Material.DIAMOND, diamondCount));
                killer.sendMessage(ChatColor.GREEN + "💰 賞金としてダイヤモンド " + diamondCount + "個を獲得しました！");

                // Discord通知
                sendDiscordClaimAlert(victim.getName(), killerName, diamondCount);
            }
        }
    }

    private void startExpirationTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            activeBounties.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }, 1200L, 1200L); // 1分ごとにチェック
    }

    private void sendDiscordAlert(String target, int amount, String issuer, boolean isNew) {
        if (plugin.getDiscordWebhookClient() == null)
            return;

        String msg = String.format("☠ **賞金首指名** ☠\nターゲット: **%s**\n賞金: **ダイヤ%d個**\n依頼者: %s",
                target, amount, issuer);
        plugin.getDiscordWebhookClient().send(msg);
    }

    private void sendDiscordClaimAlert(String target, String killer, int amount) {
        if (plugin.getDiscordWebhookClient() == null)
            return;

        String msg = String.format(
                "⚔ **賞金獲得** ⚔\nターゲット: **%s** が **%s** に討伐されました！\n賞金: **ダイヤ%d個**",
                target, killer, amount);
        plugin.getDiscordWebhookClient().send(msg);
    }
}
