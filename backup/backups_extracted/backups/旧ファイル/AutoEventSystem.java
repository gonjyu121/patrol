package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Calendar;

public class AutoEventSystem implements Listener {
    
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> playerPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerKillStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLastKillTime = new ConcurrentHashMap<>();
    
    // 自動イベント設定
    private boolean autoEventsEnabled = true;
    private BukkitTask autoEventTask;
    private BukkitTask pointDisplayTask;
    
    // イベント間隔（夕方集中システム）
    private long eventInterval = 3600000L; // 基本1時間
    private long lastEventTime = 0L;
    private boolean eveningMode = false; // 夕方モード
    
    // 現在のイベント
    private String currentEvent = "";
    private long eventEndTime = 0L; // イベント終了予定時刻（ms）。開始時刻 = eventEndTime - eventDuration*1000
    private int eventDuration = 900; // 15分間
    // サバイバルチャレンジの重複加算防止
    private final java.util.Set<java.util.UUID> survivalAwarded = new java.util.HashSet<>();
    // ポイントのインフレ防止（1イベント・1プレイヤー上限）
    private static final int MAX_EVENT_POINTS_PER_PLAYER = 9999;
    
    public AutoEventSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    public void startAutoEvents() {
        if (autoEventTask != null) {
            autoEventTask.cancel();
        }
        
        // 自動イベント開始（夕方集中システム）
        autoEventTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            
            // 夕方モードの判定（15:00-19:00）
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            boolean isEvening = (hour >= 15 && hour < 19);
            
            // 夕方モードの切り替え
            if (isEvening != eveningMode) {
                eveningMode = isEvening;
                if (eveningMode) {
                    eventInterval = 1800000L; // 夕方は30分間隔
                    Bukkit.broadcastMessage(ChatColor.GOLD + "🌆 夕方モード開始！イベント頻度が上がりました！");
                } else {
                    eventInterval = 3600000L; // 通常は1時間間隔
                    Bukkit.broadcastMessage(ChatColor.GRAY + "🌙 通常モードに戻りました");
                }
            }
            
            if (currentTime - lastEventTime >= eventInterval) {
                startRandomEvent();
                lastEventTime = currentTime;
            }
        }, 20L * 60, 20L * 60); // 1分後に開始、1分間隔でチェック
        
        // ポイント表示タスク（3分ごと）
        pointDisplayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!currentEvent.isEmpty()) {
                displayEventProgress();
            }
        }, 20L * 180, 20L * 180); // 3分後に開始、3分間隔
        
        Bukkit.broadcastMessage(ChatColor.GREEN + "🎮 自動イベントシステムが開始されました！");
        Bukkit.broadcastMessage(ChatColor.GRAY + "1時間ごとにランダムイベントが開催されます");
    }
    
    private void startRandomEvent() {
        String[] eventTypes = {
            "mob_hunt", "mining_contest", "survival_challenge", "speed_contest"
        };
        
        String selectedEvent = eventTypes[new Random().nextInt(eventTypes.length)];
        startEvent(selectedEvent);
    }
    
    private void startEvent(String eventType) {
        if (!autoEventsEnabled || !currentEvent.isEmpty()) return;
        
        currentEvent = eventType;
        playerPoints.clear();
        playerKillStreaks.clear();
        playerLastKillTime.clear();
        survivalAwarded.clear();
        // 終了予定時刻を設定（開始時刻は eventEndTime - eventDuration*1000）
        eventEndTime = System.currentTimeMillis() + (eventDuration * 1000L);
        
        // イベント開始時にオンラインプレイヤーの名前を確実に保存（Unknownプレイヤー問題の修正）
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin instanceof PatrolSpectatorPlugin) {
                PatrolSpectatorPlugin mainPlugin = (PatrolSpectatorPlugin) plugin;
                mainPlugin.ensurePlayerNameSaved(player.getUniqueId(), player.getName());
            }
        }
        
        // 🎊 派手なイベント開始演出 🎊
        String eventName = getEventDisplayName(eventType);
        
        // Title表示で盛大に開始を告知
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                ChatColor.GOLD + "🎊 自動イベント開始！ 🎊",
                ChatColor.YELLOW + eventName,
                10, 60, 20
            );
        }
        
        // チャットでも派手に表示
        Bukkit.broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.broadcastMessage(ChatColor.GOLD + "🎊 自動イベント開始！ 🎊");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "🎮 " + eventName);
        Bukkit.broadcastMessage(ChatColor.GREEN + "⏰ 制限時間: 15分間");
        Bukkit.broadcastMessage(ChatColor.AQUA + "🏆 上位3位に特別報酬！");
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "💎 参加者全員に報酬配布！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // イベント開始時の報酬配布
        giveEventStartRewards(eventType);
        
        // 15分後に自動終了
        Bukkit.getScheduler().runTaskLater(plugin, this::endEvent, 15 * 60 * 20L);
        
        // 3分ごとに進捗表示
        pointDisplayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::displayEventProgress, 3 * 60 * 20L, 3 * 60 * 20L);
    }
    
    private String getEventMessage(String eventType) {
        switch (eventType) {
            case "mob_hunt":
                return "🏹 モブハント大会 - モンスターを倒してポイントを稼ごう！";
            case "mining_contest":
                return "⛏️ 採掘大会 - 貴重な鉱石を掘ってポイントを稼ごう！";
            case "survival_challenge":
                return "💀 サバイバルチャレンジ - 生き残ってポイントを稼ごう！";
            case "speed_contest":
                return "🏃 スピード大会 - 移動距離でポイントを稼ごう！";
            default:
                return "🎮 特別イベント - 楽しもう！";
        }
    }
    
    private void giveEventReward(Player player, String eventType) {
        ItemStack[] rewards = getEventRewards(eventType);
        for (ItemStack item : rewards) {
            player.getInventory().addItem(item);
        }
        
        // プレイヤー名を確実に保存（Unknownプレイヤー問題の修正）
        if (plugin instanceof PatrolSpectatorPlugin) {
            PatrolSpectatorPlugin mainPlugin = (PatrolSpectatorPlugin) plugin;
            mainPlugin.ensurePlayerNameSaved(player.getUniqueId(), player.getName());
        }
        
        player.sendMessage(ChatColor.GREEN + "📦 " + getEventDisplayName(eventType) + " 参加報酬を配布しました！");
    }
    
    private ItemStack[] getEventRewards(String eventType) {
        switch (eventType) {
            case "mob_hunt":
                return new ItemStack[]{
                    new ItemStack(Material.ARROW, 32),
                    new ItemStack(Material.BOW, 1),
                    new ItemStack(Material.COOKED_BEEF, 16)
                };
            case "mining_contest":
                return new ItemStack[]{
                    new ItemStack(Material.IRON_PICKAXE, 1),
                    new ItemStack(Material.TORCH, 64),
                    new ItemStack(Material.BREAD, 16)
                };
            case "survival_challenge":
                return new ItemStack[]{
                    new ItemStack(Material.GOLDEN_APPLE, 8),
                    new ItemStack(Material.IRON_CHESTPLATE, 1),
                    new ItemStack(Material.SHIELD, 1)
                };
            case "speed_contest":
                return new ItemStack[]{
                    new ItemStack(Material.POTION, 1),
                    new ItemStack(Material.LEATHER_BOOTS, 1),
                    new ItemStack(Material.COOKED_BEEF, 8)
                };
            default:
                return new ItemStack[]{
                    new ItemStack(Material.BREAD, 8)
                };
        }
    }
    
    private void giveEventStartRewards(String eventType) {
        ItemStack[] rewards = getEventRewards(eventType);
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : rewards) {
                player.getInventory().addItem(item);
            }
            player.sendMessage(ChatColor.GREEN + "📦 イベント参加報酬を配布しました！");
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!currentEvent.equals("mob_hunt") || event.getEntity().getKiller() == null) return;
        
        Player killer = event.getEntity().getKiller();
        UUID killerId = killer.getUniqueId();
        
        // モブハント専用のポイント（PK数ランキングには影響しない）
        int basePoints = getMobHuntPoints(event.getEntity().getType());
        
        // キルストリーク管理
        long currentTime = System.currentTimeMillis();
        Long lastKillTime = playerLastKillTime.get(killerId);
        
        if (lastKillTime != null && currentTime - lastKillTime < 30000) { // 30秒以内
            // キルストリーク継続
            int streak = playerKillStreaks.getOrDefault(killerId, 0) + 1;
            playerKillStreaks.put(killerId, streak);
            
            // ストリークボーナス
            int streakBonus = Math.min(streak * 5, 50); // 最大50ポイント
            addPoints(killerId, basePoints + streakBonus);
            
            killer.sendMessage(ChatColor.GREEN + "🎯 モブハントポイント +" + (basePoints + streakBonus) + " (ストリーク: " + streak + ")");
        } else {
            // 新しいキルストリーク開始
            playerKillStreaks.put(killerId, 1);
            addPoints(killerId, basePoints);
            killer.sendMessage(ChatColor.GREEN + "🎯 モブハントポイント +" + basePoints);
        }
        
        playerLastKillTime.put(killerId, currentTime);
    }
    
    // モブハント専用のポイント計算（PK数ランキングには影響しない）
    private int getMobHuntPoints(EntityType entityType) {
        switch (entityType) {
            case ZOMBIE:
            case SKELETON:
            case SPIDER:
            case CREEPER:
                return 10;
            case ENDERMAN:
            case WITCH:
            case SLIME:
                return 20;
            case BLAZE:
            case MAGMA_CUBE:
                return 30;
            case GHAST:
            case WITHER_SKELETON:
                return 40;
            case ENDER_DRAGON:
            case WITHER:
                return 100;
            default:
                return 5; // その他のモブ
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!currentEvent.equals("mining_contest")) return;
        
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        int points = getMiningPoints(blockType);
        if (points > 0) {
            addPoints(player.getUniqueId(), points);
            player.sendMessage(ChatColor.GREEN + "⛏️ 採掘ポイント +" + points);
        }
    }
    
    private int getMiningPoints(Material material) {
        switch (material) {
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
                return 50;
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                return 30;
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
                return 20;
            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
                return 15;
            case COAL_ORE:
            case DEEPSLATE_COAL_ORE:
                return 5;
            default:
                return 0;
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!currentEvent.equals("survival_challenge")) return;
        
        Player deadPlayer = event.getEntity();
        UUID deadPlayerId = deadPlayer.getUniqueId();
        // 同一プレイヤーの重複加算を防止（死亡ごとに複数回入らないように）
        if (survivalAwarded.contains(deadPlayerId)) return;
        survivalAwarded.add(deadPlayerId);

        // 生存時間（ms）= 現時点 - 開始時刻
        long eventStartMs = eventEndTime - (eventDuration * 1000L);
        long survivalTime = Math.max(0L, System.currentTimeMillis() - eventStartMs);
        // 10秒ごとに1ポイント（最大 eventDuration/10 = 90pt）
        int survivalPoints = (int) Math.min(eventDuration / 10, survivalTime / 10000L);

        if (survivalPoints > 0) {
            addPoints(deadPlayerId, survivalPoints);
            deadPlayer.sendMessage(ChatColor.YELLOW + "💀 サバイバルポイント +" + survivalPoints + " (生存時間: " + (survivalTime / 1000) + "秒)");
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!currentEvent.equals("speed_contest")) return;
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to != null && from.distance(to) > 0.1) { // 実際に移動した場合
            addPoints(player.getUniqueId(), 1);
        }
    }
    
    private void addPoints(UUID playerId, int points) {
        if (points <= 0) return;
        playerPoints.compute(playerId, (id, current) -> {
            int existing = current == null ? 0 : current;
            long sum = (long) existing + (long) points;
            int capped = (int) Math.min(MAX_EVENT_POINTS_PER_PLAYER, Math.max(0L, sum));
            return capped;
        });
        
        // プレイヤー名を保存（Unknownプレイヤー問題の修正）
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && plugin instanceof PatrolSpectatorPlugin) {
            PatrolSpectatorPlugin mainPlugin = (PatrolSpectatorPlugin) plugin;
            mainPlugin.ensurePlayerNameSaved(playerId, player.getName());
        }
    }
    
    private void displayEventProgress() {
        if (currentEvent.isEmpty()) return;
        
        // 上位5位を表示
        List<Map.Entry<UUID, Integer>> sortedPlayers = new ArrayList<>(playerPoints.entrySet());
        sortedPlayers.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        Bukkit.broadcastMessage(ChatColor.YELLOW + "📊 " + getEventDisplayName(currentEvent) + " 進捗:");
        
        for (int i = 0; i < Math.min(5, sortedPlayers.size()); i++) {
            Map.Entry<UUID, Integer> entry = sortedPlayers.get(i);
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                String rank = getRankString(i + 1);
                Bukkit.broadcastMessage(rank + " " + player.getName() + ": " + entry.getValue() + "ポイント (上限:" + MAX_EVENT_POINTS_PER_PLAYER + ")");
            }
        }
    }
    
    private String getEventDisplayName(String eventType) {
        switch (eventType) {
            case "mob_hunt": return "モブハント";
            case "mining_contest": return "採掘大会";
            case "survival_challenge": return "サバイバル";
            case "speed_contest": return "スピード大会";
            default: return "イベント";
        }
    }
    
    private void endEvent() {
        if (currentEvent.isEmpty()) return;
        
        Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 イベント終了！結果発表 🏆");
        
        // 上位プレイヤーを発表
        announceWinners();
        
        // 上位プレイヤーに特別報酬配布
        giveTopPlayerRewards();
        
        // リセット
        currentEvent = "";
        playerPoints.clear();
        playerKillStreaks.clear();
        playerLastKillTime.clear();
        survivalAwarded.clear();
        eventEndTime = 0L;
    }
    
    private void announceWinners() {
        List<Map.Entry<UUID, Integer>> sortedPlayers = new ArrayList<>(playerPoints.entrySet());
        sortedPlayers.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        Bukkit.broadcastMessage(ChatColor.YELLOW + "🏆 イベント結果:");
        
        for (int i = 0; i < Math.min(5, sortedPlayers.size()); i++) {
            Map.Entry<UUID, Integer> entry = sortedPlayers.get(i);
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                String rank = getRankString(i + 1);
                Bukkit.broadcastMessage(rank + " " + player.getName() + ": " + entry.getValue() + "ポイント");
                
                // プレイヤー名を確実に保存（Unknownプレイヤー問題の修正）
                if (plugin instanceof PatrolSpectatorPlugin) {
                    PatrolSpectatorPlugin mainPlugin = (PatrolSpectatorPlugin) plugin;
                    mainPlugin.ensurePlayerNameSaved(entry.getKey(), player.getName());
                }
            }
        }
    }
    
    private void giveTopPlayerRewards() {
        List<Map.Entry<UUID, Integer>> sortedPlayers = new ArrayList<>(playerPoints.entrySet());
        sortedPlayers.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        for (int i = 0; i < Math.min(3, sortedPlayers.size()); i++) {
            Player player = Bukkit.getPlayer(sortedPlayers.get(i).getKey());
            if (player != null) {
                giveTopPlayerReward(player, i + 1);
                
                // ランキングにイベント結果を反映
                int clampedEventPts = Math.min(MAX_EVENT_POINTS_PER_PLAYER, sortedPlayers.get(i).getValue());
                addEventPointsToRanking(player.getUniqueId(), i + 1, clampedEventPts);
            }
        }
    }
    
    private void giveTopPlayerReward(Player player, int rank) {
        ItemStack[] rewards = getTopPlayerRewards(rank);
        for (ItemStack item : rewards) {
            player.getInventory().addItem(item);
        }
        
        String rankMessage = getRankString(rank);
        player.sendMessage(ChatColor.GOLD + "🏆 " + rankMessage + " 特別報酬を配布しました！");
        
        // 保護時間延長報酬を追加
        giveProtectionReward(player, rank);
    }
    
    /**
     * イベント報酬として保護時間を延長
     */
    private void giveProtectionReward(Player player, int rank) {
        if (plugin instanceof PatrolSpectatorPlugin) {
            PatrolSpectatorPlugin mainPlugin = (PatrolSpectatorPlugin) plugin;
            UUID playerId = player.getUniqueId();
            
            long protectionTime = 0L;
            switch (rank) {
                case 1: // 1位: +2時間
                    protectionTime = 2 * 60 * 60 * 1000L;
                    player.sendMessage(ChatColor.GREEN + "🛡️ イベント報酬で保護時間を2時間延長しました！");
                    break;
                case 2: // 2位: +1時間
                    protectionTime = 60 * 60 * 1000L;
                    player.sendMessage(ChatColor.GREEN + "🛡️ イベント報酬で保護時間を1時間延長しました！");
                    break;
                case 3: // 3位: +30分
                    protectionTime = 30 * 60 * 1000L;
                    player.sendMessage(ChatColor.GREEN + "🛡️ イベント報酬で保護時間を30分延長しました！");
                    break;
            }
            
            if (protectionTime > 0) {
                mainPlugin.extendProtectionDuration(playerId, protectionTime);
            }
        }
    }
    
    private ItemStack[] getTopPlayerRewards(int rank) {
        switch (rank) {
            case 1: // 1位
                return new ItemStack[]{
                    new ItemStack(Material.DIAMOND, 2),
                    new ItemStack(Material.GOLDEN_APPLE, 4),
                    new ItemStack(Material.EXPERIENCE_BOTTLE, 16)
                };
            case 2: // 2位
                return new ItemStack[]{
                    new ItemStack(Material.DIAMOND, 1),
                    new ItemStack(Material.GOLDEN_APPLE, 2),
                    new ItemStack(Material.EXPERIENCE_BOTTLE, 8)
                };
            case 3: // 3位
                return new ItemStack[]{
                    new ItemStack(Material.IRON_INGOT, 3),
                    new ItemStack(Material.GOLDEN_APPLE, 1),
                    new ItemStack(Material.EXPERIENCE_BOTTLE, 4)
                };
            default:
                return new ItemStack[]{};
        }
    }
    
    private String getRankString(int rank) {
        switch (rank) {
            case 1: return "🥇";
            case 2: return "🥈";
            case 3: return "🥉";
            case 4: return "4位";
            case 5: return "5位";
            default: return rank + "位";
        }
    }
    
    public void setAutoEventsEnabled(boolean enabled) {
        this.autoEventsEnabled = enabled;
        if (!enabled) {
            if (autoEventTask != null) {
                autoEventTask.cancel();
            }
            if (pointDisplayTask != null) {
                pointDisplayTask.cancel();
            }
            currentEvent = "";
        }
    }
    
    public boolean isEventActive() {
        return !currentEvent.isEmpty();
    }
    
    public String getCurrentEvent() {
        return currentEvent;
    }
    
    public void stopAutoEvents() {
        if (autoEventTask != null) {
            autoEventTask.cancel();
        }
        if (pointDisplayTask != null) {
            pointDisplayTask.cancel();
        }
        currentEvent = "";
        playerPoints.clear();
    }
    
    // ランキングにイベント結果を反映するメソッド
    private void addEventPointsToRanking(UUID playerId, int rank, int eventPoints) {
        // メインプラグインのランキングシステムにアクセス
        if (plugin instanceof PatrolSpectatorPlugin) {
            PatrolSpectatorPlugin mainPlugin = (PatrolSpectatorPlugin) plugin;
            
            // イベント参加ポイントをランキングに加算
            int rankingPoints = getEventRankingPoints(rank, eventPoints);
            mainPlugin.addEventPointsToRanking(playerId, rankingPoints, currentEvent);
            
            // プレイヤーに通知
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(ChatColor.GOLD + "🏆 ランキングに " + rankingPoints + " ポイントが加算されました！");
            }
        }
    }
    
    // イベント結果をランキングポイントに変換
    private int getEventRankingPoints(int rank, int eventPoints) {
        int basePoints = eventPoints / 10; // イベントポイントの1/10を基本ポイント
        
        // 順位ボーナス
        int rankBonus = 0;
        switch (rank) {
            case 1: rankBonus = 100; break; // 1位: +100ポイント
            case 2: rankBonus = 50; break;  // 2位: +50ポイント
            case 3: rankBonus = 25; break;  // 3位: +25ポイント
        }
        
        return basePoints + rankBonus;
    }
}
