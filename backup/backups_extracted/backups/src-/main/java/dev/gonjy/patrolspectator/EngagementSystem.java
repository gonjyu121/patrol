package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EngagementSystem {
    
    private final JavaPlugin plugin;
    private final Map<UUID, PlayerEngagementData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerJoinTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerSessionCount = new ConcurrentHashMap<>();
    
    // 参加者数に応じた報酬設定
    private final Map<Integer, EngagementReward> participationRewards = new HashMap<>();
    
    // イベント関連
    private BukkitTask engagementTask;
    private boolean isEventActive = false;
    private String currentEvent = "";
    
    public EngagementSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        initializeRewards();
    }
    
    private void initializeRewards() {
        // 参加者数に応じた報酬設定（一人でもコツコツ貯められるように調整）
        participationRewards.put(1, new EngagementReward("参加者1人達成", Arrays.asList(Material.BREAD, Material.TORCH), 10));
        participationRewards.put(3, new EngagementReward("参加者3人達成", Arrays.asList(Material.IRON_INGOT, Material.COOKED_BEEF), 50));
        participationRewards.put(5, new EngagementReward("参加者5人達成", Arrays.asList(Material.GOLD_INGOT, Material.BREAD), 100));
        participationRewards.put(10, new EngagementReward("参加者10人達成", Arrays.asList(Material.DIAMOND, Material.GOLDEN_APPLE), 200));
        participationRewards.put(15, new EngagementReward("参加者15人達成", Arrays.asList(Material.DIAMOND, Material.GOLDEN_APPLE), 500));
        participationRewards.put(20, new EngagementReward("参加者20人達成", Arrays.asList(Material.NETHERITE_INGOT, Material.ENCHANTED_GOLDEN_APPLE), 1000));
    }
    
    public void onPlayerJoin(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // 参加時間記録
        playerJoinTimes.put(playerId, currentTime);
        
        // セッション数更新
        playerSessionCount.merge(playerId, 1, Integer::sum);
        
        // 新規参加者かチェック
        if (!playerData.containsKey(playerId)) {
            // 新規参加者ウェルカムボーナス
            giveWelcomeBonus(player);
            broadcastNewPlayer(player);
        }
        
        // 参加者数チェックと報酬配布
        checkParticipationRewards();
        
        // エンゲージメントデータ初期化
        playerData.put(playerId, new PlayerEngagementData(player.getName()));
    }
    
    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        
        // 参加時間を記録
        Long joinTime = playerJoinTimes.get(playerId);
        if (joinTime != null) {
            long sessionTime = System.currentTimeMillis() - joinTime;
            PlayerEngagementData data = playerData.get(playerId);
            if (data != null) {
                data.addTotalPlayTime(sessionTime);
                data.addSession();
            }
        }
        
        playerJoinTimes.remove(playerId);
    }
    
    private void giveWelcomeBonus(Player player) {
        // 新規参加者へのウェルカムボーナス
        ItemStack[] welcomeItems = {
            createItem(Material.STONE_SWORD, "§a新規参加者記念剣", "§7初回参加記念品"),
            createItem(Material.BREAD, "§aウェルカムパン", "§7お腹を満たそう"),
            createItem(Material.TORCH, "§a安全の松明", "§7暗闇を照らそう")
        };
        
        for (ItemStack item : welcomeItems) {
            player.getInventory().addItem(item);
        }
        
        player.sendMessage(ChatColor.GREEN + "🎉 新規参加者ボーナスを配布しました！");
        player.sendMessage(ChatColor.YELLOW + "📦 記念品をインベントリに追加しました");
    }
    
    private void broadcastNewPlayer(Player player) {
        String message = ChatColor.AQUA + "🎉 " + player.getName() + " が初回参加しました！";
        Bukkit.broadcastMessage(message);
        Bukkit.broadcastMessage(ChatColor.GRAY + "👋 みんなで歓迎しましょう！");
    }
    
    private void checkParticipationRewards() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        
        for (Map.Entry<Integer, EngagementReward> entry : participationRewards.entrySet()) {
            int threshold = entry.getKey();
            EngagementReward reward = entry.getValue();
            
            if (onlinePlayers >= threshold && !reward.isDistributed()) {
                distributeParticipationReward(reward, onlinePlayers);
                reward.setDistributed(true);
            }
        }
    }
    
    private void distributeParticipationReward(EngagementReward reward, int playerCount) {
        String message = ChatColor.GOLD + "🎊 " + reward.getName() + " 🎊";
        Bukkit.broadcastMessage(message);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "参加者数: " + playerCount + "人");
        Bukkit.broadcastMessage(ChatColor.GREEN + "全員に報酬を配布します！");
        
        // 全プレイヤーに報酬配布
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Material material : reward.getItems()) {
                ItemStack item = new ItemStack(material, 1);
                player.getInventory().addItem(item);
            }
            player.sendMessage(ChatColor.GREEN + "📦 報酬を受け取りました！");
        }
    }
    
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    public void startEngagementTask() {
        if (engagementTask != null) {
            engagementTask.cancel();
        }
        
        engagementTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // 定期的なエンゲージメントチェック
            checkLongTimePlayers();
            updateEngagementStats();
            checkIndividualRewards(); // 個人報酬チェックを追加
        }, 20L * 60, 20L * 300); // 1分後に開始、5分間隔
    }
    
    private void checkLongTimePlayers() {
        long currentTime = System.currentTimeMillis();
        long oneHour = 60 * 60 * 1000; // 1時間
        
        for (Map.Entry<UUID, Long> entry : playerJoinTimes.entrySet()) {
            UUID playerId = entry.getKey();
            Long joinTime = entry.getValue();
            
            if (joinTime != null) {
                long playTime = currentTime - joinTime;
                
                // 1時間参加者への特別報酬
                if (playTime >= oneHour && playTime < oneHour + 60000) { // 1時間ちょうど
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        giveLongTimeReward(player);
                    }
                }
            }
        }
    }
    
    private void giveLongTimeReward(Player player) {
        ItemStack[] longTimeItems = {
            createItem(Material.EXPERIENCE_BOTTLE, "§6長時間参加報酬", "§7継続は力なり！"),
            createItem(Material.GOLDEN_APPLE, "§6持久力の証", "§7頑張りました！")
        };
        
        for (ItemStack item : longTimeItems) {
            player.getInventory().addItem(item);
        }
        
        player.sendMessage(ChatColor.GOLD + "⏰ 1時間参加報酬を配布しました！");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "🎉 " + player.getName() + " が1時間参加しました！");
    }
    
    private void updateEngagementStats() {
        // 統計情報の更新
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerEngagementData data = playerData.get(player.getUniqueId());
            if (data != null) {
                data.updateLastSeen();
            }
        }
    }
    
    // 一人でもコツコツ貯められる個人報酬システム
    private void checkIndividualRewards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerEngagementData data = playerData.get(player.getUniqueId());
            if (data != null) {
                // 30分参加報酬
                if (data.getTotalPlayTime() >= 30 * 60 * 1000 && !data.hasReceived30MinReward()) {
                    giveIndividualReward(player, "30分参加", Material.IRON_INGOT, 30);
                    data.setReceived30MinReward(true);
                }
                
                // 1時間参加報酬
                if (data.getTotalPlayTime() >= 60 * 60 * 1000 && !data.hasReceived1HourReward()) {
                    giveIndividualReward(player, "1時間参加", Material.GOLD_INGOT, 50);
                    data.setReceived1HourReward(true);
                }
                
                // 3時間参加報酬
                if (data.getTotalPlayTime() >= 3 * 60 * 60 * 1000 && !data.hasReceived3HourReward()) {
                    giveIndividualReward(player, "3時間参加", Material.DIAMOND, 100);
                    data.setReceived3HourReward(true);
                }
                
                // 5時間参加報酬
                if (data.getTotalPlayTime() >= 5 * 60 * 60 * 1000 && !data.hasReceived5HourReward()) {
                    giveIndividualReward(player, "5時間参加", Material.NETHERITE_INGOT, 200);
                    data.setReceived5HourReward(true);
                }
                
                // 10時間参加報酬（累計生存時間ランキング用）
                if (data.getTotalPlayTime() >= 10 * 60 * 60 * 1000 && !data.hasReceived10HourReward()) {
                    giveSurvivalReward(player, "10時間生存", Material.NETHERITE_SWORD, 1);
                    data.setReceived10HourReward(true);
                }
                
                // 24時間参加報酬（累計生存時間ランキング用）
                if (data.getTotalPlayTime() >= 24 * 60 * 60 * 1000 && !data.hasReceived24HourReward()) {
                    giveSurvivalReward(player, "24時間生存", Material.ENCHANTED_GOLDEN_APPLE, 64);
                    data.setReceived24HourReward(true);
                }
                
                // 50時間参加報酬（累計生存時間ランキング用）
                if (data.getTotalPlayTime() >= 50 * 60 * 60 * 1000 && !data.hasReceived50HourReward()) {
                    giveSurvivalReward(player, "50時間生存", Material.DIAMOND, 8);
                    data.setReceived50HourReward(true);
                }
                
                // 100時間参加報酬（累計生存時間ランキング用）
                if (data.getTotalPlayTime() >= 100 * 60 * 60 * 1000 && !data.hasReceived100HourReward()) {
                    giveSurvivalReward(player, "100時間生存", Material.NETHERITE_INGOT, 4);
                    data.setReceived100HourReward(true);
                }
                
                // 10回参加報酬
                if (data.getSessionCount() >= 10 && !data.hasReceived10SessionReward()) {
                    giveIndividualReward(player, "10回参加", Material.EXPERIENCE_BOTTLE, 50);
                    data.setReceived10SessionReward(true);
                }
                
                // 50回参加報酬
                if (data.getSessionCount() >= 50 && !data.hasReceived50SessionReward()) {
                    giveIndividualReward(player, "50回参加", Material.GOLDEN_APPLE, 16);
                    data.setReceived50SessionReward(true);
                }
            }
        }
    }
    
    private void giveIndividualReward(Player player, String achievement, Material material, int amount) {
        ItemStack rewardItem = createItem(material, "§6" + achievement + "達成", "§7コツコツ頑張りました！");
        rewardItem.setAmount(amount);
        player.getInventory().addItem(rewardItem);
        
        player.sendMessage(ChatColor.GOLD + "🏆 " + achievement + "達成報酬を配布しました！");
        player.sendMessage(ChatColor.YELLOW + "📦 " + material.name() + " x" + amount + " を獲得しました");
        
        // ランキングに個人報酬ポイントを加算
        if (plugin instanceof PatrolSpectatorPlugin) {
            PatrolSpectatorPlugin mainPlugin = (PatrolSpectatorPlugin) plugin;
            mainPlugin.addEventPointsToRanking(player.getUniqueId(), 5, "個人報酬_" + achievement);
        }
    }
    
    // 累計生存時間ランキング専用の特別報酬
    private void giveSurvivalReward(Player player, String achievement, Material material, int amount) {
        ItemStack rewardItem = createItem(material, "§c§l" + achievement + "達成", "§7累計生存時間ランキング専用報酬！");
        rewardItem.setAmount(amount);
        player.getInventory().addItem(rewardItem);
        
        // 特別なメッセージで全サーバーに通知
        Bukkit.broadcastMessage(ChatColor.RED + "🔥 " + ChatColor.GOLD + "累計生存時間ランキング達成！ 🔥");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "🏆 " + player.getName() + " が " + achievement + " を達成しました！");
        Bukkit.broadcastMessage(ChatColor.AQUA + "📦 特別報酬: " + material.name() + " x" + amount);
        
        player.sendMessage(ChatColor.GOLD + "🏆 " + achievement + "達成報酬を配布しました！");
        player.sendMessage(ChatColor.YELLOW + "📦 " + material.name() + " x" + amount + " を獲得しました");
        
        // ランキングに生存時間報酬ポイントを加算（より高額）
        if (plugin instanceof PatrolSpectatorPlugin) {
            PatrolSpectatorPlugin mainPlugin = (PatrolSpectatorPlugin) plugin;
            int points = getSurvivalRewardPoints(achievement);
            mainPlugin.addEventPointsToRanking(player.getUniqueId(), points, "生存時間報酬_" + achievement);
        }
    }
    
    // 生存時間報酬のランキングポイント計算
    private int getSurvivalRewardPoints(String achievement) {
        switch (achievement) {
            case "10時間生存": return 50;
            case "24時間生存": return 100;
            case "50時間生存": return 200;
            case "100時間生存": return 500;
            default: return 25;
        }
    }
    
    public void showEngagementStats(Player player) {
        player.sendMessage(ChatColor.GREEN + "📊 エンゲージメント統計");
        player.sendMessage(ChatColor.GRAY + "現在の参加者数: " + ChatColor.YELLOW + Bukkit.getOnlinePlayers().size() + "人");
        
        PlayerEngagementData data = playerData.get(player.getUniqueId());
        if (data != null) {
            player.sendMessage(ChatColor.GRAY + "総参加時間: " + ChatColor.YELLOW + formatTime(data.getTotalPlayTime()));
            player.sendMessage(ChatColor.GRAY + "参加回数: " + ChatColor.YELLOW + data.getSessionCount() + "回");
        }
    }
    
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return hours + "時間" + (minutes % 60) + "分";
        } else if (minutes > 0) {
            return minutes + "分" + (seconds % 60) + "秒";
        } else {
            return seconds + "秒";
        }
    }
    
    public void resetRewards() {
        for (EngagementReward reward : participationRewards.values()) {
            reward.setDistributed(false);
        }
    }
    
    // 内部クラス
    private static class PlayerEngagementData {
        private final String playerName;
        private long totalPlayTime = 0;
        private int sessionCount = 0;
        private long lastSeen = System.currentTimeMillis();
        
        // 個人報酬受取フラグ
        private boolean received30MinReward = false;
        private boolean received1HourReward = false;
        private boolean received3HourReward = false;
        private boolean received5HourReward = false;
        private boolean received10SessionReward = false;
        private boolean received50SessionReward = false;
        
        // 累計生存時間報酬受取フラグ
        private boolean received10HourReward = false;
        private boolean received24HourReward = false;
        private boolean received50HourReward = false;
        private boolean received100HourReward = false;
        
        public PlayerEngagementData(String playerName) {
            this.playerName = playerName;
        }
        
        public void addTotalPlayTime(long time) {
            this.totalPlayTime += time;
        }
        
        public void addSession() {
            this.sessionCount++;
        }
        
        public void updateLastSeen() {
            this.lastSeen = System.currentTimeMillis();
        }
        
        // Getters
        public String getPlayerName() { return playerName; }
        public long getTotalPlayTime() { return totalPlayTime; }
        public int getSessionCount() { return sessionCount; }
        public long getLastSeen() { return lastSeen; }
        
        // 個人報酬フラグのGetters and Setters
        public boolean hasReceived30MinReward() { return received30MinReward; }
        public void setReceived30MinReward(boolean received) { this.received30MinReward = received; }
        
        public boolean hasReceived1HourReward() { return received1HourReward; }
        public void setReceived1HourReward(boolean received) { this.received1HourReward = received; }
        
        public boolean hasReceived3HourReward() { return received3HourReward; }
        public void setReceived3HourReward(boolean received) { this.received3HourReward = received; }
        
        public boolean hasReceived5HourReward() { return received5HourReward; }
        public void setReceived5HourReward(boolean received) { this.received5HourReward = received; }
        
        public boolean hasReceived10SessionReward() { return received10SessionReward; }
        public void setReceived10SessionReward(boolean received) { this.received10SessionReward = received; }
        
        public boolean hasReceived50SessionReward() { return received50SessionReward; }
        public void setReceived50SessionReward(boolean received) { this.received50SessionReward = received; }
        
        // 累計生存時間報酬フラグのGetters and Setters
        public boolean hasReceived10HourReward() { return received10HourReward; }
        public void setReceived10HourReward(boolean received) { this.received10HourReward = received; }
        
        public boolean hasReceived24HourReward() { return received24HourReward; }
        public void setReceived24HourReward(boolean received) { this.received24HourReward = received; }
        
        public boolean hasReceived50HourReward() { return received50HourReward; }
        public void setReceived50HourReward(boolean received) { this.received50HourReward = received; }
        
        public boolean hasReceived100HourReward() { return received100HourReward; }
        public void setReceived100HourReward(boolean received) { this.received100HourReward = received; }
    }
    
    private static class EngagementReward {
        private final String name;
        private final List<Material> items;
        private final int experiencePoints;
        private boolean distributed = false;
        
        public EngagementReward(String name, List<Material> items, int experiencePoints) {
            this.name = name;
            this.items = items;
            this.experiencePoints = experiencePoints;
        }
        
        // Getters and Setters
        public String getName() { return name; }
        public List<Material> getItems() { return items; }
        public int getExperiencePoints() { return experiencePoints; }
        public boolean isDistributed() { return distributed; }
        public void setDistributed(boolean distributed) { this.distributed = distributed; }
    }
}
