package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.GameRule;
import org.bukkit.StructureType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class PatrolSpectatorPlugin extends JavaPlugin implements Listener {

    private final List<UUID> patrolOrder = new ArrayList<>();
    private int patrolIndex = -1;

    private BukkitTask autoTask;
    private BukkitTask keepAliveTask; // NEW
    private long intervalTicks;
    private boolean useSpectatorCamera;
    private Set<String> allowedWorlds;
    private String exemptPermission;
    private boolean announceToPlayers;
    private boolean useTitle;
    private String announceFormat;
    private boolean idleSpectator;

    // Anti-jitter
    private UUID currentTargetId = null;
    private long lockUntilMs = 0L;
    private long backoffUntilMs = 0L;
    private long combatLockMs = 8000L;
    private long switchBackoffMs = 1500L;

    // Anti-AFK
    private boolean keepAliveEnabled;
    private long keepAliveTicks;
    
    // 無操作検知回避用
    private BukkitTask antiAfkTask;
    private long lastActivityTime;

    // Patroller binding
    private UUID patrollerId = null;
    private Location patrolStartLocation = null;

    // Excluded player names (lowercase)
    private Set<String> excludedPlayers = new HashSet<>();

    // 観光地巡回システム用の変数
    private List<TouristLocation> touristLocations = new ArrayList<>();
    private int currentTouristLocationIndex = 0;
    private boolean isInTouristMode = false;
    private long nextTouristLocationSwitch = 0L;
    private long touristModeDuration = 300000L; // 5分間観光地モード
    private long touristLocationDuration = 30000L; // 30秒間各観光地に滞在

    // ランキングシステム用の変数
    private Map<UUID, Long> playerJoinTimes = new HashMap<>();
    private Map<UUID, Long> playerTotalSurvivalTime = new HashMap<>(); // 累計生存時間
    private Map<UUID, Integer> playerKillCounts = new HashMap<>();
    private Map<UUID, Integer> playerDeathCounts = new HashMap<>();
    private Map<UUID, Integer> playerEnderDragonKills = new HashMap<>(); // エンダードラゴン討伐数
    private Map<UUID, Integer> playerEventPoints = new HashMap<>(); // イベントポイント
    private Map<UUID, String> playerNames = new HashMap<>();
    
    // ランキング表示間隔（5分）
    private BukkitTask rankingTask;
    private static final long RANKING_INTERVAL = 300L; // 5分 = 300秒
    
    // ルール表示間隔（30分）
    private BukkitTask ruleDisplayTask;
    private static final long RULE_DISPLAY_INTERVAL = 1800L; // 30分 = 1800秒
    
    // ランキング発表中の視点移動停止フラグ
    private boolean isRankingAnnouncement = false;
    
    // エンドワールドリセット用
    private BukkitTask endWorldResetTask;
    private static final long END_RESET_DELAY = 300L; // 5分後にリセット
    
    // データ保存用
    private File rankingDataFile;
    private BukkitTask autoSaveTask;
    private static final long AUTO_SAVE_INTERVAL = 600L; // 10分 = 600秒
    
    // 多言語対応用
    private boolean enableEnglishMessages = true;
    
    // 参加率向上システム
    private EngagementSystem engagementSystem;
    private AutoEventSystem autoEventSystem;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocalConfig();
        getLogger().info("PatrolSpectatorPlugin enabled");
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // ランキングデータファイルの初期化
        initializeRankingData();
        
        // サーバー設定の自動修正
        configureServerSettings();
        
        rebuildOrder();
        autoTask = Bukkit.getScheduler().runTaskTimer(this, this::tickAuto, 10L, 10L);
        startKeepAlive();
        startAntiAfk(); // 無操作検知回避を開始
        startSettingsMonitor(); // 設定監視を開始（最重要）
        startRankingDisplay(); // ランキング表示を開始
        startRuleDisplay(); // ルール表示を開始
        startAutoSave(); // 自動保存を開始
        // 特別場所の初期化は遅延実行（起動時の負荷を軽減）
        Bukkit.getScheduler().runTaskLater(this, this::initializeTouristLocations, 100L);
        
        // 参加率向上システムの初期化
        engagementSystem = new EngagementSystem(this);
        autoEventSystem = new AutoEventSystem(this);
        engagementSystem.startEngagementTask();
        autoEventSystem.startAutoEvents();
    }

    @Override
    public void onDisable() {
        stopAutoPatrol();
        stopKeepAlive();
        stopAntiAfk();
        stopSettingsMonitor();
        stopRankingDisplay();
        stopRuleDisplay();
        stopAutoSave();
        
        // エンドワールドリセットタスクを停止
        if (endWorldResetTask != null) {
            endWorldResetTask.cancel();
        }
        
        // 最終データ保存
        saveRankingData();
        
        getLogger().info("PatrolSpectatorPlugin disabled");
    }

    private void reloadLocalConfig() {
        reloadConfig();
        intervalTicks = 20L * Math.max(1, getConfig().getInt("intervalSeconds", 10));
        useSpectatorCamera = getConfig().getBoolean("useSpectatorCamera", true);
        exemptPermission = getConfig().getString("exemptPermission", "patrolspectator.exempt");
        List<String> worlds = getConfig().getStringList("allowedWorlds");
        allowedWorlds = new HashSet<>(worlds == null ? List.of() : worlds);
        announceToPlayers = getConfig().getBoolean("announceToPlayers", false);
        useTitle = getConfig().getBoolean("useTitle", true);
        announceFormat = ChatColor.translateAlternateColorCodes('&', getConfig().getString("announceFormat", "&7[配信]&f 現在の視点: &a%target%"));
        combatLockMs = 1000L * Math.max(1, getConfig().getInt("combatLockSeconds", 8));
        switchBackoffMs = 1000L * Math.max(1, getConfig().getInt("retargetBackoffSeconds", 2));
        idleSpectator = getConfig().getBoolean("idleSpectator", true);
        keepAliveEnabled = getConfig().getBoolean("keepAliveEnabled", true);
        keepAliveTicks = 20L * Math.max(10, getConfig().getInt("keepAliveSeconds", 30));
        // excluded players
        excludedPlayers.clear();
        List<String> ex = getConfig().getStringList("excludedPlayers");
        if (ex != null) {
            for (String s : ex) if (s != null) excludedPlayers.add(s.toLowerCase());
        }
        // 起動時に必ずコマンド実行者と関連アカウントを除外に含める
        excludedPlayers.add("kayaobana");
        excludedPlayers.add("otougame");
        excludedPlayers.add("otou_game");
        excludedPlayers.add("otou");
        getLogger().info("reloadLocalConfig: 除外リスト = " + excludedPlayers);
    }

    private void startKeepAlive() {
        stopKeepAlive();
        if (!keepAliveEnabled) return;
        keepAliveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            Player p = getPatroller();
            if (p == null || !p.isOnline()) return;
            
            // 静かなAFK防止（画面の揺れを避ける）
            if (p.getGameMode() != GameMode.SPECTATOR) {
                try { 
                    // スイングアクションのみ（テレポートは削除）
                    p.swingMainHand(); 
                } catch (Throwable ignored) {}
            }
            
            // 空のメッセージ送信を削除（無記入テキストの原因）
            // try { p.sendMessage(""); } catch (Throwable ignored) {}
            
            // 定期的な参加促進メッセージ（5分ごと）
            if (Bukkit.getCurrentTick() % 6000 == 0) { // 5分 = 6000 ticks
                if (patrolOrder.size() < 3) { // 参加者が少ない時のみ
                    if (announceToPlayers) {
                                broadcastMultilingualMessage(
            ChatColor.GOLD + "🎥 無人配信中！参加者募集中です",
            ChatColor.GOLD + "🎥 Live streaming! Looking for participants"
        );
        broadcastMultilingualMessage(
            ChatColor.AQUA + "💫 参加して配信に出演しませんか？",
            ChatColor.AQUA + "💫 Join us and appear on stream!"
        );
        broadcastMultilingualMessage(
            ChatColor.GRAY + "📝 現在の参加者: " + patrolOrder.size() + "人",
            ChatColor.GRAY + "📝 Current participants: " + patrolOrder.size() + " players"
        );
                    }
                }
            }
        }, keepAliveTicks, keepAliveTicks);
    }

    private void stopKeepAlive() {
        if (keepAliveTask != null) { keepAliveTask.cancel(); keepAliveTask = null; }
    }

    // 無操作検知回避システム（強化版）
    private void startAntiAfk() {
        try {
            lastActivityTime = System.currentTimeMillis();
            antiAfkTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    Player patroller = getPatroller();
                    if (patroller != null && patroller.isOnline()) {
                        // 定期的にアクティビティを記録
                        lastActivityTime = System.currentTimeMillis();
                        
                        // より確実なAFK防止アクション
                        performAntiAfkActions(patroller);
                        
                                // 定期的にログにアクティビティを記録（30分ごと）
        if (Bukkit.getCurrentTick() % 36000 == 0) { // 30分ごと
            getLogger().info("無操作検知回避: アクティビティを記録中");
        }
                    }
                } catch (Exception e) {
                    getLogger().warning("無操作検知回避でエラー: " + e.getMessage());
                }
            }, 50L, 50L); // 2.5秒ごとに実行（AFK防止を確実に）
        } catch (Exception e) {
            getLogger().warning("無操作検知回避システムの開始でエラー: " + e.getMessage());
        }
    }

    // AFK防止アクションの実行（参加者に見えない最小限版）
    private void performAntiAfkActions(Player player) {
        try {
            // 参加者に見えない最小限のAFK防止
            if (player.getGameMode() != GameMode.SPECTATOR) {
                // 1. スイングアクション（参加者には見えない）
                player.swingMainHand();
                
                // 2. スニーク状態変更を削除（画面の揺れの原因）
                // player.setSneaking(!player.isSneaking());
            }
            
            // 3. 空のメッセージ送信（削除 - 無記入テキストの原因）
            // try { 
            //     player.sendMessage(""); 
            // } catch (Exception ignored) {}
            
            // 4. テレポートは削除（画面の揺れの原因）
            // Location loc = player.getLocation();
            // loc.setYaw(loc.getYaw() + 0.1f);
            // player.teleport(loc);
            
            // 5. ブロック操作は削除（不要な処理）
            // if (player.getGameMode() != GameMode.SPECTATOR) {
            //     Location blockLoc = player.getLocation().add(0, -1, 0);
            //     if (blockLoc.getBlock().getType().isAir()) {
            //         blockLoc.getBlock().setType(org.bukkit.Material.GLASS);
            //         blockLoc.getBlock().setType(org.bukkit.Material.AIR);
            //     }
            // }
            
            // 6. アクティビティ記録
            lastActivityTime = System.currentTimeMillis();
            
            // 7. インベントリ操作は削除（不要）
            // if (player.getGameMode() != GameMode.SPECTATOR) {
            //     player.openInventory(player.getInventory());
            //     player.closeInventory();
            // }
            
            // 8. ワールド変更は削除（画面の揺れの原因）
            // World currentWorld = player.getWorld();
            // if (currentWorld != null) {
            //     Location originalLoc = player.getLocation();
            //     Location tempLoc = new Location(currentWorld, originalLoc.getX(), originalLoc.getY(), originalLoc.getZ());
            //     tempLoc.setYaw(originalLoc.getYaw() + 0.01f);
            //     player.teleport(tempLoc);
            // }
            
            // 9. 静かな状態更新（削除 - 不要な処理）
            // try {
            //     // プレイヤーの状態を静かに更新
            //     player.setWalkSpeed(player.getWalkSpeed());
            //     player.setFlySpeed(player.getFlySpeed());
            // } catch (Exception ignored) {}
            
        } catch (Exception e) {
            getLogger().warning("AFK防止アクションでエラー: " + e.getMessage());
        }
    }

    private void stopAntiAfk() {
        if (antiAfkTask != null) {
            antiAfkTask.cancel();
            antiAfkTask = null;
        }
    }

    // 観光地の初期化
    private void initializeTouristLocations() {
        try {
            touristLocations.clear();
            
            // 各ワールドの観光地を追加
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    // オーバーワールドの観光地（高い位置から眺める）
                    addTouristLocation(world, 0, world.getHighestBlockYAt(0, 0) + 30, 0, "スポーン地点", "サーバーの中心地を上空から", "overworld");
                    addTouristLocation(world, 100, world.getHighestBlockYAt(100, 100) + 40, 100, "美しい丘", "緑豊かな丘を上空から", "overworld");
                    addTouristLocation(world, -100, world.getHighestBlockYAt(-100, -100) + 35, -100, "静かな湖", "美しい湖を上空から", "overworld");
                    addTouristLocation(world, 200, world.getHighestBlockYAt(200, 200) + 45, 200, "森の奥地", "神秘的な森を上空から", "overworld");
                    addTouristLocation(world, -200, world.getHighestBlockYAt(-200, -200) + 50, -200, "山の頂上", "壮大な山を上空から", "overworld");
                    addTouristLocation(world, 300, world.getHighestBlockYAt(300, 300) + 35, 300, "草原", "広大な草原を上空から", "overworld");
                    addTouristLocation(world, -300, world.getHighestBlockYAt(-300, -300) + 45, -300, "海底神殿", "古代の海底遺跡を上空から", "overworld");
                    addTouristLocation(world, 500, world.getHighestBlockYAt(500, 500) + 40, 500, "砂漠", "広大な砂漠を上空から", "overworld");
                    addTouristLocation(world, -500, world.getHighestBlockYAt(-500, -500) + 50, -500, "雪山", "美しい雪山を上空から", "overworld");
                    
                    // 建造物の正確な位置を動的に取得
                    addStructureLocations(world);
                } else if (world.getEnvironment() == World.Environment.NETHER) {
                    // ネザーの観光地
                    addTouristLocation(world, 0, 100, 0, "ネザー中央", "溶岩の世界を上空から", "nether");
                    addTouristLocation(world, 50, 95, 50, "溶岩の海", "溶岩の海を上空から", "nether");
                    addTouristLocation(world, -50, 95, -50, "溶岩の海", "溶岩の海を上空から", "nether");
                    addTouristLocation(world, 100, 90, 100, "ネザー要塞", "ネザーの要塞を上空から", "nether");
                } else if (world.getEnvironment() == World.Environment.THE_END) {
                    // エンドの観光地
                    addTouristLocation(world, 0, 120, 0, "エンダードラゴンの巣", "エンダードラゴンの巣を上空から", "end");
                    addTouristLocation(world, 100, 115, 100, "エンドシティ", "古代のエンドシティを上空から", "end");
                    addTouristLocation(world, -100, 115, -100, "エンドシティ", "古代のエンドシティを上空から", "end");
                    addTouristLocation(world, 300, 110, 300, "エンド船", "エンド船の残骸を上空から", "end");
                    addTouristLocation(world, -300, 110, -300, "エンド船", "エンド船の残骸を上空から", "end");
                    addTouristLocation(world, 500, 105, 500, "エンドゲートウェイ", "エンドゲートウェイを上空から", "end");
                    addTouristLocation(world, -500, 105, -500, "エンドゲートウェイ", "エンドゲートウェイを上空から", "end");
                }
            }
            
            getLogger().info("観光地を " + touristLocations.size() + " 箇所初期化しました");
        } catch (Exception e) {
            getLogger().warning("観光地の初期化でエラーが発生しました: " + e.getMessage());
            // エラーが発生した場合は基本的な場所のみ追加
            addBasicTouristLocations();
        }
    }

    // 観光地追加
    private void addTouristLocation(World world, int x, int y, int z, String name, String description, String worldType) {
        try {
            Location loc = new Location(world, x, y, z);
            TouristLocation touristLoc = new TouristLocation(name, loc, description, worldType);
            touristLocations.add(touristLoc);
            getLogger().info("観光地を追加: " + name + " (" + world.getName() + ") - " + description);
        } catch (Exception e) {
            getLogger().warning("観光地の追加でエラー: " + name + " - " + e.getMessage());
        }
    }

    // 建造物の位置を動的に取得して観光地に追加（改良版）
    private void addStructureLocations(World world) {
        try {
            getLogger().info("建造物の観光地を動的に取得します...");
            
            // 非同期で建造物検索を実行（サーバー起動を遅延させないため）
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
                try {
                    // 実際の座標を取得して観光地に追加
                    findAndAddStructure(world, "monument", "海底神殿", "古代の海底遺跡を上空から", 30);
                    findAndAddStructure(world, "village", "村", "賑やかな村を上空から", 25);
                    findAndAddStructure(world, "pillager_outpost", "略奪者の前哨基地", "危険な前哨基地を上空から", 35);
                    findAndAddStructure(world, "mansion", "森の洋館", "神秘的な森の洋館を上空から", 40);
                    
                    // ネザーワールドの場合
                    if (world.getEnvironment() == World.Environment.NETHER) {
                        findAndAddStructure(world, "nether_fortress", "ネザー要塞", "ネザーの要塞を上空から", 20);
                        findAndAddStructure(world, "bastion_remnant", "砦の遺跡", "古代の砦の遺跡を上空から", 25);
                    }
                    
                    // エンドワールドの場合
                    if (world.getEnvironment() == World.Environment.THE_END) {
                        findAndAddStructure(world, "end_city", "エンドシティ", "古代のエンドシティを上空から", 30);
                    }
                    
                    // 建造物検索完了後、結果をログに出力
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        getLogger().info("✅ 建造物検索完了: " + world.getName() + " - 観光地総数: " + touristLocations.size());
                    }, 20L); // 1秒後に結果を表示
                    
                } catch (Exception e) {
                    getLogger().warning("建造物の位置取得でエラー: " + e.getMessage());
                }
            }, 60L); // 3秒後に建造物検索を開始
            
        } catch (Exception e) {
            getLogger().warning("建造物の位置取得でエラー: " + e.getMessage());
        }
    }
    
    // 建造物を実際に検索して観光地に追加（改良版）
    private void findAndAddStructure(World world, String structureType, String displayName, String description, int heightOffset) {
        try {
            // 非同期で建造物を検索（メインスレッドをブロックしないため）
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    StructureType structure = getStructureType(structureType);
                    
                    if (structure != null) {
                        // 複数の検索ポイントから建造物を検索
                        Location structureLoc = findStructureFromMultiplePoints(world, structure);
                        
                        if (structureLoc != null) {
                            // メインスレッドで観光地に追加
                            Bukkit.getScheduler().runTask(this, () -> {
                                int x = structureLoc.getBlockX();
                                int y = Math.max(structureLoc.getBlockY() + heightOffset, world.getMinHeight() + 10);
                                int z = structureLoc.getBlockZ();
                                
                                // 高度制限チェック
                                if (y > world.getMaxHeight() - 10) {
                                    y = world.getMaxHeight() - 10;
                                }
                                
                                addTouristLocation(world, x, y, z, displayName, description, getWorldTypeString(world));
                                getLogger().info("✅ 建造物発見・追加: " + displayName + " (" + x + ", " + y + ", " + z + ")");
                            });
                        } else {
                            getLogger().info("⚠️ 建造物が見つかりませんでした: " + displayName);
                        }
                    } else {
                        getLogger().warning("❌ 未対応の建造物タイプ: " + structureType);
                    }
                } catch (Exception e) {
                    getLogger().warning("❌ 建造物検索エラー (" + displayName + "): " + e.getMessage());
                }
            });
            
        } catch (Exception e) {
            getLogger().warning("建造物検索の開始でエラー (" + displayName + "): " + e.getMessage());
        }
    }
    
    // 複数の検索ポイントから建造物を検索（改良版）
    private Location findStructureFromMultiplePoints(World world, StructureType structure) {
        try {
            // 検索ポイントのリスト（より広範囲をカバー）
            Location[] searchPoints = {
                world.getSpawnLocation(),
                new Location(world, 0, world.getMaxHeight(), 0),
                new Location(world, 5000, world.getMaxHeight(), 5000),
                new Location(world, -5000, world.getMaxHeight(), -5000),
                new Location(world, 5000, world.getMaxHeight(), -5000),
                new Location(world, -5000, world.getMaxHeight(), 5000)
            };
            
            // 各検索ポイントから建造物を検索
            for (Location searchPoint : searchPoints) {
                try {
                    // より広い範囲で検索（20km）
                    Location structureLoc = world.locateNearestStructure(searchPoint, structure, 20000, false);
                    
                    if (structureLoc != null) {
                        // 建造物が見つかった場合、その座標が有効かチェック
                        if (isValidStructureLocation(world, structureLoc, structure)) {
                            getLogger().info("✅ 建造物発見: " + structure.toString() + " at (" + 
                                structureLoc.getBlockX() + ", " + structureLoc.getBlockY() + ", " + structureLoc.getBlockZ() + ")");
                            return structureLoc;
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning("検索ポイントでの建造物検索エラー: " + e.getMessage());
                }
            }
            
            return null;
        } catch (Exception e) {
            getLogger().warning("複数ポイント検索エラー: " + e.getMessage());
            return null;
        }
    }
    
    // 建造物の座標が有効かチェック
    private boolean isValidStructureLocation(World world, Location location, StructureType structure) {
        try {
            // 座標がnullでないかチェック
            if (location == null) return false;
            
            // 座標がワールドの範囲内かチェック
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            
            if (x < -30000000 || x > 30000000 || z < -30000000 || z > 30000000) {
                return false;
            }
            
            // 高度が適切かチェック
            if (y < world.getMinHeight() || y > world.getMaxHeight()) {
                return false;
            }
            
            // 建造物の種類に応じた追加チェック
            if (structure == StructureType.OCEAN_MONUMENT) {
                // 海底神殿は水中にあるべき
                return world.getBlockAt(location).getType().name().contains("WATER");
            } else if (structure == StructureType.VILLAGE) {
                // 村は地上にあるべき
                return !world.getBlockAt(location).getType().name().contains("WATER");
            } else if (structure == StructureType.PILLAGER_OUTPOST) {
                // 前哨基地は地上にあるべき
                return !world.getBlockAt(location).getType().name().contains("WATER");
            } else if (structure == StructureType.WOODLAND_MANSION) {
                // 洋館は森にあるべき
                return world.getBlockAt(location).getType().name().contains("LOG") || 
                       world.getBlockAt(location).getType().name().contains("LEAVES");
            } else {
                return true;
            }
        } catch (Exception e) {
            getLogger().warning("建造物座標検証エラー: " + e.getMessage());
            return false;
        }
    }
    
    // 建造物タイプを取得（安全版）
    private StructureType getStructureType(String structureType) {
        try {
            switch (structureType.toLowerCase()) {
                case "monument": return StructureType.OCEAN_MONUMENT;
                case "village": return StructureType.VILLAGE;
                case "pillager_outpost": return StructureType.PILLAGER_OUTPOST;
                case "mansion": return StructureType.WOODLAND_MANSION;
                case "nether_fortress": return StructureType.NETHER_FORTRESS;
                case "bastion_remnant": return StructureType.BASTION_REMNANT;
                case "end_city": return StructureType.END_CITY;
                // ancient_cityとtrial_chambersは新しい構造物なので、存在しない場合はスキップ
                case "ancient_city":
                    getLogger().info("Ancient Cityは未対応のバージョンです");
                    return null;
                case "trial_chambers":
                    getLogger().info("Trial Chambersは未対応のバージョンです");
                    return null;
                default:
                    getLogger().warning("未知の建造物タイプ: " + structureType);
                    return null;
            }
        } catch (Exception e) {
            getLogger().warning("建造物タイプ取得エラー: " + e.getMessage());
            return null;
        }
    }
    
    // ワールドタイプの文字列を取得
    private String getWorldTypeString(World world) {
        switch (world.getEnvironment()) {
            case NETHER: return "nether";
            case THE_END: return "end";
            default: return "overworld";
        }
    }

    // 基本的な観光地を追加（フォールバック）
    private void addBasicTouristLocations() {
        try {
            World world = Bukkit.getWorlds().get(0);
            if (world != null) {
                // スポーン地点
                addTouristLocation(world, 0, world.getHighestBlockYAt(0, 0) + 20, 0, "スポーン", "サーバーの中心地を上空から", "overworld");
                
                // ランダムな観光地を複数追加
                for (int i = 1; i <= 5; i++) {
                    int x = (int) ((Math.random() - 0.5) * 1000);
                    int z = (int) ((Math.random() - 0.5) * 1000);
                    int y = world.getHighestBlockYAt(x, z) + 15;
                    
                    String[] names = {"美しい丘", "森の奥地", "湖のほとり", "山の頂上", "平原の中心"};
                    String[] descriptions = {"美しい丘の絶景を", "森の奥地の神秘的な景色を", "湖の美しい景色を", "山の頂上からの眺めを", "平原の広大な景色を"};
                    
                    addTouristLocation(world, x, y, z, names[i-1], descriptions[i-1] + "上空から", "overworld");
                }
                
                getLogger().info("基本的な観光地を5箇所追加しました");
            }
        } catch (Exception e) {
            getLogger().warning("基本的な観光地の追加でエラー: " + e.getMessage());
        }
    }

    // 観光地の巡回
    private void cycleTouristLocation(Player patroller) {
        try {
            if (touristLocations.isEmpty()) {
                // 観光地がない場合は基本的な観光地を追加
                getLogger().info("観光地が設定されていないため、基本的な観光地を追加します");
                addBasicTouristLocations();
                
                // 追加後も空の場合は、強制的にランダムな観光地を追加
                if (touristLocations.isEmpty()) {
                    getLogger().warning("観光地の追加に失敗しました。強制的にランダムな観光地を追加します");
                    World world = Bukkit.getWorlds().get(0);
                    if (world != null) {
                        for (int i = 0; i < 3; i++) {
                            int x = (int) ((Math.random() - 0.5) * 500);
                            int z = (int) ((Math.random() - 0.5) * 500);
                            int y = world.getHighestBlockYAt(x, z) + 20;
                            addTouristLocation(world, x, y, z, "観光地" + (i+1), "美しい景色を上空から", "overworld");
                        }
                    }
                }
            }
            
            if (touristLocations.isEmpty()) {
                // それでもない場合は、ランダムな場所を巡回
                World world = Bukkit.getWorlds().get(0);
                if (world != null) {
                    Location randomLoc = new Location(world, 
                        (Math.random() - 0.5) * 1000, 
                        world.getHighestBlockYAt(0, 0) + 10, 
                        (Math.random() - 0.5) * 1000);
                    patroller.teleport(randomLoc);
                    patroller.sendActionBar(ChatColor.GOLD + "🌍 美しい景色を探索中...");
                }
                return;
            }
            
            TouristLocation touristLoc = touristLocations.get(currentTouristLocationIndex);
            patroller.teleport(touristLoc.getLocation());
            
            // 観光地に応じたメッセージ
            String locationName = touristLoc.getName();
            String description = touristLoc.getDescription();
            String worldType = touristLoc.getWorldType();
            
            // ワールドタイプに応じたアイコン
            String icon = getWorldIcon(worldType);
            
                    sendMultilingualActionBar(patroller, 
            ChatColor.AQUA + icon + " " + locationName,
            ChatColor.AQUA + icon + " " + locationName
        );
        sendMultilingualMessage(patroller, 
            ChatColor.GOLD + "📺 " + description,
            ChatColor.GOLD + "📺 " + description
        );
        
        // 全プレイヤーに観光地案内を送信
        broadcastMultilingualMessage(
            ChatColor.YELLOW + "🗺️ 観光地案内: " + locationName + " - " + description,
            ChatColor.YELLOW + "🗺️ Tourist Guide: " + locationName + " - " + description
        );
            
            // 次の観光地に移動
            currentTouristLocationIndex = (currentTouristLocationIndex + 1) % touristLocations.size();
            
        } catch (Exception e) {
            getLogger().warning("観光地巡回でエラーが発生しました: " + e.getMessage());
            // エラーが発生した場合は基本的な動作のみ
            patroller.sendActionBar(ChatColor.GOLD + "🌍 探索中...");
        }
    }
    
    // ワールドタイプに応じたアイコンを取得
    private String getWorldIcon(String worldType) {
        switch (worldType) {
            case "overworld": return "🌍";
            case "nether": return "🔥";
            case "end": return "🌌";
            default: return "🏰";
        }
    }
    
    // ワールドタイプに応じた表示名を取得
    private String getWorldDisplayName(String worldType) {
        switch (worldType) {
            case "overworld": return "オーバーワールド";
            case "nether": return "ネザー";
            case "end": return "エンド";
            default: return "その他";
        }
    }

    // 場所名を取得（安全版）
    private String getLocationName(Location location) {
        try {
            World.Environment env = location.getWorld().getEnvironment();
            if (env == World.Environment.NETHER) {
                return "ネザー";
            } else if (env == World.Environment.THE_END) {
                return "エンド";
            } else {
                return "オーバーワールド";
            }
        } catch (Exception e) {
            return "神秘的な場所";
        }
    }

    // 場所に応じたメッセージを取得（安全版）
    private String getLocationMessage(Location location) {
        try {
            World.Environment env = location.getWorld().getEnvironment();
            if (env == World.Environment.NETHER) {
                return "ネザーを探索中！危険なモンスターが待ち構えています";
            } else if (env == World.Environment.THE_END) {
                return "エンドを探索中！伝説のドラゴンとの決戦の場です";
            } else {
                return "オーバーワールドを探索中！参加して一緒に冒険しましょう";
            }
        } catch (Exception e) {
            return "神秘的な場所を探索中！参加して一緒に冒険しましょう";
        }
    }

    // インベントリ保存用（既存のメソッドを使用）

    // サーバー設定の自動修正
    private void configureServerSettings() {
        try {
            // 1. player-idle-timeout を 0 に設定
            // player-idle-timeout設定を無効化しました
            
            // 2. ゲームルールの設定
            configureGameRules();
            
            // 3. 定期的な設定監視を開始
            startSettingsMonitor();
            
            getLogger().info("サーバー設定を自動修正しました");
        } catch (Exception e) {
            getLogger().warning("サーバー設定の修正でエラー: " + e.getMessage());
        }
    }

    // 定期的な設定監視
    private BukkitTask settingsMonitorTask;

    private void startSettingsMonitor() {
        try {
            if (settingsMonitorTask != null) {
                settingsMonitorTask.cancel();
            }
            
            settingsMonitorTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    // 10秒ごとに設定をチェック・修正（より頻繁に）
                    checkAndFixSettings();
                } catch (Exception e) {
                    getLogger().warning("設定監視でエラー: " + e.getMessage());
                }
            }, 200L, 200L); // 10秒ごと
            
            getLogger().info("設定監視を開始しました（10秒間隔）");
        } catch (Exception e) {
            getLogger().warning("設定監視の開始でエラー: " + e.getMessage());
        }
    }

    private void stopSettingsMonitor() {
        if (settingsMonitorTask != null) {
            settingsMonitorTask.cancel();
            settingsMonitorTask = null;
        }
    }

    private void checkAndFixSettings() {
        try {
            // player-idle-timeout の確認・修正（強化版）
            File serverProperties = new File("server.properties");
            if (serverProperties.exists()) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(serverProperties)) {
                    props.load(fis);
                }
                
                String currentTimeout = props.getProperty("player-idle-timeout", "10");
                if (!"0".equals(currentTimeout)) {
                    getLogger().warning("設定監視: player-idle-timeout が " + currentTimeout + " に戻っています。0 に修正します。");
                    props.setProperty("player-idle-timeout", "0");
                    
                    try (FileOutputStream fos = new FileOutputStream(serverProperties)) {
                        props.store(fos, "Fixed by PatrolSpectatorPlugin Settings Monitor - " + System.currentTimeMillis());
                    }
                    
                    getLogger().info("設定監視: player-idle-timeout を 0 に修正しました");
                    
                    // 設定変更をログに記録
                    getLogger().info("設定監視: 設定ファイルを更新しました - " + System.currentTimeMillis());
                } else {
                    // 正常な場合も定期的にログ出力（5分ごと）
                    if (Bukkit.getCurrentTick() % 6000 == 0) {
                        getLogger().info("設定監視: player-idle-timeout は正常に 0 に設定されています");
                    }
                }
            } else {
                getLogger().warning("設定監視: server.properties が見つかりません");
            }
            
            // ゲームルールの確認・修正（削除）
            // ゲームルール設定を無効化しました
            
            // 設定監視の動作確認ログ（1分ごと）
            if (Bukkit.getCurrentTick() % 1200 == 0) {
                // 正常動作ログを削除（頻繁すぎるため）
            }
            
        } catch (Exception e) {
            getLogger().warning("設定監視でエラー: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // player-idle-timeout の設定（Patrol中のみ有効）
    private void configurePlayerIdleTimeout() {
        // Patrolが実行中の場合のみplayer-idle-timeoutを0に設定
        if (isPatrolling()) {
            try {
                File serverProperties = new File("server.properties");
                if (!serverProperties.exists()) {
                    getLogger().warning("server.properties が見つかりません");
                    return;
                }

                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(serverProperties)) {
                    props.load(fis);
                }

                String currentTimeout = props.getProperty("player-idle-timeout", "10");
                if (!"0".equals(currentTimeout)) {
                    props.setProperty("player-idle-timeout", "0");
                    
                    try (FileOutputStream fos = new FileOutputStream(serverProperties)) {
                        props.store(fos, "Modified by PatrolSpectatorPlugin (Patrol mode)");
                    }
                    
                    getLogger().info("Patrol中: player-idle-timeout を 0 に設定しました");
                }
            } catch (Exception e) {
                getLogger().warning("player-idle-timeout の設定でエラー: " + e.getMessage());
            }
        } else {
            // Patrolが停止中は何もしない
            // Patrol停止中のログを削除（頻繁すぎるため）
        }
    }

    // ゲームルールの設定（削除）
    private void configureGameRules() {
        // ゲームルール設定を無効化
        getLogger().info("ゲームルール設定を無効化しました");
    }

    // Patrolが実行中かどうかを確認
    private boolean isPatrolling() {
        return patrollerId != null;
    }
    
    // ランキングデータの初期化
    private void initializeRankingData() {
        rankingDataFile = new File(getDataFolder(), "ranking_data.json");
        if (rankingDataFile.exists()) {
            loadRankingData();
            getLogger().info("ランキングデータを読み込みました");
            
            // パトローラーのデータを自動的に削除（既存データのクリーンアップ）
            if (patrollerId != null) {
                cleanupPatrollerData(patrollerId, "Patroller");
            }
            
                    // パトローラーのデータのみ削除（除外リストは視点制御用なので削除しない）
        // cleanupExcludedPlayersData();
        } else {
            getLogger().info("新しいランキングデータファイルを作成します");
        }
    }
    
    /**
     * パトローラー・除外対象のデータを完全削除する（3段階対策）
     */
    private void cleanupPatrollerData(UUID playerId, String playerName) {
        boolean dataRemoved = false;
        
        // 【第1段階】現在のメモリ上データを削除
        if (playerJoinTimes.remove(playerId) != null) {
            dataRemoved = true;
        }
        if (playerKillCounts.remove(playerId) != null) {
            dataRemoved = true;
        }
        if (playerDeathCounts.remove(playerId) != null) {
            dataRemoved = true;
        }
        if (playerEnderDragonKills.remove(playerId) != null) {
            dataRemoved = true;
        }
        if (playerNames.remove(playerId) != null) {
            dataRemoved = true;
        }
        if (playerTotalSurvivalTime.remove(playerId) != null) {
            dataRemoved = true;
        }
        
        if (dataRemoved) {
            getLogger().info("【第1段階】メモリ上データ削除完了: " + playerName);
            
            // 【第2段階】データファイルに即座に保存（削除を永続化）
            saveRankingData();
            getLogger().info("【第2段階】データファイル更新完了: " + playerName);
        }
    }
    
    /**
     * 除外リストのプレイヤーデータを一括削除
     */
    private void cleanupExcludedPlayersData() {
        int removedCount = 0;
        
        // 除外リストの各プレイヤーをチェック
        for (String excludedName : excludedPlayers) {
            // プレイヤー名からUUIDを逆引き
            UUID excludedId = null;
            for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
                if (entry.getValue().toLowerCase().equals(excludedName.toLowerCase())) {
                    excludedId = entry.getKey();
                    break;
                }
            }
            
            if (excludedId != null) {
                cleanupPatrollerData(excludedId, excludedName);
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            getLogger().info("【第3段階】除外リストプレイヤーのデータ削除完了: " + removedCount + "人");
        }
        
        // 追加の安全策：特定のプレイヤー名を強制削除
        forceCleanupSpecificPlayers();
    }
    
    /**
     * パトローラーのデータを強制的に削除（安全策）
     */
    private void forceCleanupSpecificPlayers() {
        // パトローラーのみ削除（除外リストは視点制御用なので削除しない）
        if (patrollerId != null) {
            String patrollerName = playerNames.getOrDefault(patrollerId, "Patroller");
            cleanupPatrollerData(patrollerId, patrollerName);
            getLogger().info("パトローラーのデータを強制削除: " + patrollerName);
        }
    }
    
    /**
     * エンドワールドリセットのスケジュール
     */
    private void scheduleEndWorldReset() {
        // 既存のタスクをキャンセル
        if (endWorldResetTask != null) {
            endWorldResetTask.cancel();
        }
        
        // 全プレイヤーにリセット予告
        Bukkit.getServer().broadcastMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.getServer().broadcastMessage(ChatColor.RED + "⚠️ エンドワールドが5分後にリセットされます！");
        Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "📋 リセット内容:");
        Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "   • エンドワールドの全エンティティ削除");
        Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "   • エンドワールドにいるプレイヤーはオーバーワールドにテレポート");
        Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "   • エンドラが再出現可能になります");
        Bukkit.getServer().broadcastMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // 5分後にリセット実行
        endWorldResetTask = Bukkit.getScheduler().runTaskLater(this, this::executeEndWorldReset, END_RESET_DELAY * 20L);
        
        getLogger().info("エンドワールドリセットをスケジュールしました（5分後）");
    }
    
    /**
     * エンドワールドリセットの実行
     */
    private void executeEndWorldReset() {
        try {
            World endWorld = Bukkit.getWorld("world_the_end");
            if (endWorld == null) {
                getLogger().warning("エンドワールドが見つかりませんでした");
                return;
            }
            
            World overworld = Bukkit.getWorld("world");
            if (overworld == null) {
                getLogger().warning("オーバーワールドが見つかりませんでした");
                return;
            }
            
            // エンドワールドにいるプレイヤーをオーバーワールドにテレポート
            int teleportedCount = 0;
            for (Player player : endWorld.getPlayers()) {
                Location spawnLocation = overworld.getSpawnLocation();
                player.teleport(spawnLocation);
                player.sendMessage(ChatColor.YELLOW + "🏠 エンドワールドリセットのため、オーバーワールドにテレポートしました");
                teleportedCount++;
            }
            
            // エンドワールドの全エンティティを削除
            int removedCount = 0;
            for (Entity entity : endWorld.getEntities()) {
                if (entity instanceof Player) continue; // プレイヤーは既にテレポート済み
                entity.remove();
                removedCount++;
            }
            
            // 結果を通知
            Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "✅ エンドワールドリセット完了！");
            Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "📊 リセット結果:");
            Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "   • テレポートしたプレイヤー: " + teleportedCount + "人");
            Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "   • 削除したエンティティ: " + removedCount + "個");
            Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "🐉 エンドラが再出現可能になりました！");
            Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            getLogger().info("エンドワールドリセット完了: テレポート=" + teleportedCount + "人, 削除=" + removedCount + "エンティティ");
            
        } catch (Exception e) {
            getLogger().severe("エンドワールドリセットでエラーが発生しました: " + e.getMessage());
            Bukkit.getServer().broadcastMessage(ChatColor.RED + "❌ エンドワールドリセットでエラーが発生しました");
        }
    }
    
    /**
     * プレイヤーがランキング除外対象かどうかをチェック（パトローラーのみ）
     */
    private boolean isPlayerExcluded(Player player) {
        if (player == null) return true;
        
        UUID playerId = player.getUniqueId();
        
        // 実行者（パトローラー）のみランキングから除外
        if (patrollerId != null && playerId.equals(patrollerId)) {
            return true;
        }
        
        return false;
    }
    
    // ランキングデータの保存
    private void saveRankingData() {
        try {
            if (!rankingDataFile.getParentFile().exists()) {
                rankingDataFile.getParentFile().mkdirs();
            }
            
            Map<String, Object> data = new HashMap<>();
            
            // プレイヤー名の保存
            Map<String, String> names = new HashMap<>();
            for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
                names.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("playerNames", names);
            
            // 累計生存時間の保存
            Map<String, Long> survivalTimes = new HashMap<>();
            for (Map.Entry<UUID, Long> entry : playerTotalSurvivalTime.entrySet()) {
                survivalTimes.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("totalSurvivalTime", survivalTimes);
            
            // キル数の保存
            Map<String, Integer> kills = new HashMap<>();
            for (Map.Entry<UUID, Integer> entry : playerKillCounts.entrySet()) {
                kills.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("killCounts", kills);
            
            // デス数の保存
            Map<String, Integer> deaths = new HashMap<>();
            for (Map.Entry<UUID, Integer> entry : playerDeathCounts.entrySet()) {
                deaths.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("deathCounts", deaths);
            
            // エンダードラゴン討伐数の保存
            Map<String, Integer> dragonKills = new HashMap<>();
            for (Map.Entry<UUID, Integer> entry : playerEnderDragonKills.entrySet()) {
                dragonKills.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("enderDragonKills", dragonKills);
            
            // イベントポイントの保存
            Map<String, Integer> eventPoints = new HashMap<>();
            for (Map.Entry<UUID, Integer> entry : playerEventPoints.entrySet()) {
                eventPoints.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("eventPoints", eventPoints);
            
            // JSONとして保存
            String json = new com.google.gson.Gson().toJson(data);
            java.nio.file.Files.write(rankingDataFile.toPath(), json.getBytes());
            
            getLogger().info("ランキングデータを保存しました");
        } catch (Exception e) {
            getLogger().warning("ランキングデータの保存に失敗: " + e.getMessage());
        }
    }
    
    // ランキングデータの読み込み
    private void loadRankingData() {
        try {
            String json = new String(java.nio.file.Files.readAllBytes(rankingDataFile.toPath()));
            Map<String, Object> data = new com.google.gson.Gson().fromJson(json, Map.class);
            
            // プレイヤー名の読み込み
            if (data.containsKey("playerNames")) {
                Map<String, String> names = (Map<String, String>) data.get("playerNames");
                for (Map.Entry<String, String> entry : names.entrySet()) {
                    playerNames.put(UUID.fromString(entry.getKey()), entry.getValue());
                }
            }
            
            // 累計生存時間の読み込み
            if (data.containsKey("totalSurvivalTime")) {
                Map<String, Double> survivalTimes = (Map<String, Double>) data.get("totalSurvivalTime");
                for (Map.Entry<String, Double> entry : survivalTimes.entrySet()) {
                    playerTotalSurvivalTime.put(UUID.fromString(entry.getKey()), entry.getValue().longValue());
                }
            }
            
            // キル数の読み込み
            if (data.containsKey("killCounts")) {
                Map<String, Double> kills = (Map<String, Double>) data.get("killCounts");
                for (Map.Entry<String, Double> entry : kills.entrySet()) {
                    playerKillCounts.put(UUID.fromString(entry.getKey()), entry.getValue().intValue());
                }
            }
            
            // デス数の読み込み
            if (data.containsKey("deathCounts")) {
                Map<String, Double> deaths = (Map<String, Double>) data.get("deathCounts");
                for (Map.Entry<String, Double> entry : deaths.entrySet()) {
                    playerDeathCounts.put(UUID.fromString(entry.getKey()), entry.getValue().intValue());
                }
            }
            
            // エンダードラゴン討伐数の読み込み
            if (data.containsKey("enderDragonKills")) {
                Map<String, Double> dragonKills = (Map<String, Double>) data.get("enderDragonKills");
                for (Map.Entry<String, Double> entry : dragonKills.entrySet()) {
                    playerEnderDragonKills.put(UUID.fromString(entry.getKey()), entry.getValue().intValue());
                }
            }
            
            // イベントポイントの読み込み
            if (data.containsKey("eventPoints")) {
                Map<String, Double> eventPoints = (Map<String, Double>) data.get("eventPoints");
                for (Map.Entry<String, Double> entry : eventPoints.entrySet()) {
                    playerEventPoints.put(UUID.fromString(entry.getKey()), entry.getValue().intValue());
                }
            }
            
        } catch (Exception e) {
            getLogger().warning("ランキングデータの読み込みに失敗: " + e.getMessage());
        }
    }
    
    // 自動保存の開始
    private void startAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveRankingData, AUTO_SAVE_INTERVAL * 20L, AUTO_SAVE_INTERVAL * 20L);
        getLogger().info("ランキングデータの自動保存を開始しました（" + AUTO_SAVE_INTERVAL + "秒間隔）");
    }
    
    // 自動保存の停止
    private void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
            getLogger().info("ランキングデータの自動保存を停止しました");
        }
    }
    
    // ランキング表示機能
    private void startRankingDisplay() {
        if (rankingTask != null) {
            rankingTask.cancel();
        }
        
        rankingTask = Bukkit.getScheduler().runTaskTimer(this, this::displayRankings, RANKING_INTERVAL * 20L, RANKING_INTERVAL * 20L);
        getLogger().info("ランキング表示を開始しました（" + RANKING_INTERVAL + "秒間隔）");
    }
    
    private void stopRankingDisplay() {
        if (rankingTask != null) {
            rankingTask.cancel();
            rankingTask = null;
            getLogger().info("ランキング表示を停止しました");
        }
    }
    
    // ルール表示機能
    private void startRuleDisplay() {
        if (ruleDisplayTask != null) {
            ruleDisplayTask.cancel();
        }
        
        ruleDisplayTask = Bukkit.getScheduler().runTaskTimer(this, this::displayRules, RULE_DISPLAY_INTERVAL * 20L, RULE_DISPLAY_INTERVAL * 20L);
        getLogger().info("ルール表示を開始しました（" + RULE_DISPLAY_INTERVAL + "秒間隔）");
    }
    
    private void stopRuleDisplay() {
        if (ruleDisplayTask != null) {
            ruleDisplayTask.cancel();
            ruleDisplayTask = null;
            getLogger().info("ルール表示を停止しました");
        }
    }
    
    private void displayRules() {
        if (!autoRunning) return;
        
        // Title表示でルール開始を通知
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                ChatColor.AQUA + "📋 サーバールール 📋",
                ChatColor.YELLOW + "5秒後に詳細を表示します",
                10, 60, 20
            );
        }
        
        // 5秒後に詳細ルールを表示
        Bukkit.getScheduler().runTaskLater(this, () -> {
            broadcastMultilingualMessage(
                ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            );
            broadcastMultilingualMessage(
                ChatColor.AQUA + "📋 サーバールール & システム説明 📋",
                ChatColor.AQUA + "📋 Server Rules & System Guide 📋"
            );
            broadcastMultilingualMessage(
                ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            );
            
            // 基本ルール
            broadcastMultilingualMessage(
                ChatColor.GOLD + "🎮 基本ルール",
                ChatColor.GOLD + "🎮 Basic Rules"
            );
            broadcastMultilingualMessage(
                ChatColor.WHITE + "  • 自由にサバイバルを楽しもう！",
                ChatColor.WHITE + "  • Enjoy survival freely!"
            );
            broadcastMultilingualMessage(
                ChatColor.WHITE + "  • 他のプレイヤーを尊重しよう",
                ChatColor.WHITE + "  • Respect other players"
            );
            broadcastMultilingualMessage(
                ChatColor.WHITE + "  • 建築・採掘・戦闘など何でもOK！",
                ChatColor.WHITE + "  • Building, mining, combat - everything is OK!"
            );
            
            // 2秒後に参加率向上システム説明
            Bukkit.getScheduler().runTaskLater(this, () -> {
                broadcastMultilingualMessage(
                    ChatColor.GREEN + "🎯 参加率向上システム",
                    ChatColor.GREEN + "🎯 Participation Enhancement System"
                );
                broadcastMultilingualMessage(
                    ChatColor.WHITE + "  • 新規参加者：記念品配布",
                    ChatColor.WHITE + "  • New players: Welcome items"
                );
                broadcastMultilingualMessage(
                    ChatColor.WHITE + "  • 参加者数報酬：1人・3人・5人・10人・15人・20人達成",
                    ChatColor.WHITE + "  • Player count rewards: 1, 3, 5, 10, 15, 20 players"
                );
                broadcastMultilingualMessage(
                    ChatColor.WHITE + "  • 個人報酬：30分・1時間・3時間・5時間参加",
                    ChatColor.WHITE + "  • Individual rewards: 30min, 1h, 3h, 5h participation"
                );
                broadcastMultilingualMessage(
                    ChatColor.WHITE + "  • 累計生存時間報酬：10時間・24時間・50時間・100時間",
                    ChatColor.WHITE + "  • Total survival time rewards: 10h, 24h, 50h, 100h"
                );
                
                // 2秒後に自動イベントシステム説明
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    broadcastMultilingualMessage(
                        ChatColor.PURPLE + "🎮 自動イベントシステム",
                        ChatColor.PURPLE + "🎮 Auto Event System"
                    );
                    broadcastMultilingualMessage(
                        ChatColor.WHITE + "  • 1時間ごとにランダムイベント開催",
                        ChatColor.WHITE + "  • Random events every hour"
                    );
                    broadcastMultilingualMessage(
                        ChatColor.WHITE + "  • モブハント・採掘大会・サバイバル・スピード大会",
                        ChatColor.WHITE + "  • Mob Hunt, Mining Contest, Survival, Speed Contest"
                    );
                    broadcastMultilingualMessage(
                        ChatColor.WHITE + "  • 制限時間15分、上位3位に特別報酬",
                        ChatColor.WHITE + "  • 15min time limit, special rewards for top 3"
                    );
                    broadcastMultilingualMessage(
                        ChatColor.WHITE + "  • イベント結果はランキングに反映",
                        ChatColor.WHITE + "  • Event results affect rankings"
                    );
                    
                    // 2秒後にランキングシステム説明
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        broadcastMultilingualMessage(
                            ChatColor.YELLOW + "🏆 ランキングシステム",
                            ChatColor.YELLOW + "🏆 Ranking System"
                        );
                        broadcastMultilingualMessage(
                            ChatColor.WHITE + "  • 累計生存時間ランキング",
                            ChatColor.WHITE + "  • Total survival time ranking"
                        );
                        broadcastMultilingualMessage(
                            ChatColor.WHITE + "  • PK数ランキング（プレイヤーキルのみ）",
                            ChatColor.WHITE + "  • PK count ranking (player kills only)"
                        );
                        broadcastMultilingualMessage(
                            ChatColor.WHITE + "  • エンダードラゴン討伐数ランキング",
                            ChatColor.WHITE + "  • Ender Dragon kills ranking"
                        );
                        broadcastMultilingualMessage(
                            ChatColor.WHITE + "  • イベントポイントランキング",
                            ChatColor.WHITE + "  • Event points ranking"
                        );
                        broadcastMultilingualMessage(
                            ChatColor.WHITE + "  • 5分ごとにランキング表示",
                            ChatColor.WHITE + "  • Rankings displayed every 5 minutes"
                        );
                        
                        // 2秒後にコマンド説明
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            broadcastMultilingualMessage(
                                ChatColor.BLUE + "⌨️ コマンド一覧",
                                ChatColor.BLUE + "⌨️ Commands"
                            );
                            broadcastMultilingualMessage(
                                ChatColor.WHITE + "  • /patrol start - 自動パトロール開始",
                                ChatColor.WHITE + "  • /patrol start - Start auto patrol"
                            );
                            broadcastMultilingualMessage(
                                ChatColor.WHITE + "  • /patrol stop - 自動パトロール停止",
                                ChatColor.WHITE + "  • /patrol stop - Stop auto patrol"
                            );
                            broadcastMultilingualMessage(
                                ChatColor.WHITE + "  • /patrol engagement - 個人統計表示",
                                ChatColor.WHITE + "  • /patrol engagement - Show personal stats"
                            );
                            broadcastMultilingualMessage(
                                ChatColor.WHITE + "  • /patrol autoevent status - イベント状況確認",
                                ChatColor.WHITE + "  • /patrol autoevent status - Check event status"
                            );
                            
                            // 最後に区切り線
                            broadcastMultilingualMessage(
                                ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                                ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                            );
                            broadcastMultilingualMessage(
                                ChatColor.GRAY + "💡 ルールは30分ごとに表示されます",
                                ChatColor.GRAY + "💡 Rules are displayed every 30 minutes"
                            );
                        }, 40L);
                    }, 40L);
                }, 40L);
            }, 40L);
        }, 100L);
    }
    
    private void displayRankings() {
        if (!autoRunning) return;
        
        // ランキング発表開始
        isRankingAnnouncement = true;
        
        // Title表示でランキング開始を通知（大きな文字で表示）
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                ChatColor.GOLD + "🏆 ランキング発表 🏆",
                ChatColor.YELLOW + "5秒後に詳細を表示します",
                10, 60, 20
            );
        }
        
        // 5秒後に詳細ランキングを表示
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // 累計生存時間ランキング（過去参加者も含む）
            List<Map.Entry<UUID, Long>> survivalRanking = getTotalSurvivalTimeRanking();
            
            // Title表示でランキングタイトルを大きく表示
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendTitle(
                    ChatColor.GOLD + "🏆 累計生存時間ランキング",
                    "",
                    10, 40, 10
                );
            }
            
            // チャットでも表示
                    broadcastMultilingualMessage(
            ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
        broadcastMultilingualMessage(
            ChatColor.GOLD + "🏆 累計生存時間ランキング 🏆",
            ChatColor.GOLD + "🏆 Total Survival Time Ranking 🏆"
        );
        broadcastMultilingualMessage(
            ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
            
            if (!survivalRanking.isEmpty()) {
                for (int i = 0; i < Math.min(3, survivalRanking.size()); i++) {
                    Map.Entry<UUID, Long> entry = survivalRanking.get(i);
                    String playerName = playerNames.getOrDefault(entry.getKey(), "Unknown");
                    long totalMinutes = entry.getValue() / (1000 * 60);
                    long totalHours = totalMinutes / 60;
                    String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                    String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";
                    
                    // 時間表示を改善
                    String timeDisplay;
                    if (totalHours > 0) {
                        timeDisplay = totalHours + "時間" + (totalMinutes % 60) + "分";
                    } else {
                        timeDisplay = totalMinutes + "分";
                    }
                    
                    Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " " + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.GOLD + timeDisplay);
                }
                
                // 累計生存時間ランキングの特別メッセージ
                broadcastMultilingualMessage(
                    ChatColor.AQUA + "  💡 累計生存時間ランキングに挑戦しよう！",
                    ChatColor.AQUA + "  💡 Challenge the Total Survival Time Ranking!"
                );
                broadcastMultilingualMessage(
                    ChatColor.GRAY + "  🏆 10時間・24時間・50時間・100時間で特別報酬！",
                    ChatColor.GRAY + "  🏆 Special rewards at 10h, 24h, 50h, 100h!"
                );
            } else {
                broadcastMultilingualMessage(
                    ChatColor.GRAY + "  📊 まだ記録がありません。参加して記録を作りましょう！",
                    ChatColor.GRAY + "  📊 No records yet. Join and create records!"
                );
            }
            
            // 2秒後にPK数ランキング
            Bukkit.getScheduler().runTaskLater(this, () -> {
                List<Map.Entry<UUID, Integer>> killRanking = getKillCountRanking();
                
                // Title表示でランキングタイトルを大きく表示
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendTitle(
                        ChatColor.RED + "⚔️ PK数ランキング ⚔️",
                        "",
                        10, 40, 10
                    );
                }
                
                // チャットでも表示
                        broadcastMultilingualMessage(
            ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
        broadcastMultilingualMessage(
            ChatColor.RED + "⚔️ PK数ランキング ⚔️",
            ChatColor.RED + "⚔️ PK Count Ranking ⚔️"
        );
        broadcastMultilingualMessage(
            ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
                
                if (!killRanking.isEmpty()) {
                    for (int i = 0; i < Math.min(3, killRanking.size()); i++) {
                        Map.Entry<UUID, Integer> entry = killRanking.get(i);
                        String playerName = playerNames.getOrDefault(entry.getKey(), "Unknown");
                        int kills = entry.getValue();
                        String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                        String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";
                        Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " " + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.RED + kills + "キル");
                    }
                } else {
                    broadcastMultilingualMessage(
                    ChatColor.GRAY + "  ⚔️ まだPK記録がありません。戦闘で記録を作りましょう！",
                    ChatColor.GRAY + "  ⚔️ No PK records yet. Create records in battle!"
                );
                }
                
                // 2秒後にエンダードラゴン討伐数ランキング
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    List<Map.Entry<UUID, Integer>> dragonRanking = getEnderDragonKillRanking();
                    
                    // Title表示でランキングタイトルを大きく表示
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle(
                            ChatColor.LIGHT_PURPLE + "🐉 エンドラ討伐数ランキング 🐉",
                            "",
                            10, 40, 10
                        );
                    }
                    
                    // チャットでも表示
                            broadcastMultilingualMessage(
            ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
        broadcastMultilingualMessage(
            ChatColor.LIGHT_PURPLE + "🐉 エンダードラゴン討伐数ランキング 🐉",
            ChatColor.LIGHT_PURPLE + "🐉 Ender Dragon Kills Ranking 🐉"
        );
        broadcastMultilingualMessage(
            ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
                    
                    if (!dragonRanking.isEmpty()) {
                        getLogger().info("エンダードラゴン討伐ランキング: " + dragonRanking.size() + "件の記録を表示");
                        for (int i = 0; i < Math.min(3, dragonRanking.size()); i++) {
                            Map.Entry<UUID, Integer> entry = dragonRanking.get(i);
                            String playerName = playerNames.getOrDefault(entry.getKey(), "Unknown");
                            int dragonKills = entry.getValue();
                            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                            String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";
                            Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " " + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.LIGHT_PURPLE + dragonKills + "討伐");
                        }
                    } else {
                                getLogger().info("エンダードラゴン討伐ランキング: 記録なし - 案内メッセージを表示");
        broadcastMultilingualMessage(
            ChatColor.GRAY + "  🐉 まだエンダードラゴン討伐記録がありません。エンダードラゴンに挑戦しましょう！",
            ChatColor.GRAY + "  🐉 No Ender Dragon kill records yet. Challenge the Ender Dragon!"
        );
                    }
                    
                    // 2秒後にイベントポイントランキング
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        List<Map.Entry<UUID, Integer>> eventRanking = getEventPointsRanking();
                        
                        // Title表示でランキングタイトルを大きく表示
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.sendTitle(
                                ChatColor.AQUA + "🎮 イベントポイントランキング 🎮",
                                "",
                                10, 40, 10
                            );
                        }
                        
                        // チャットでも表示
                        broadcastMultilingualMessage(
                            ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                            ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                        );
                        broadcastMultilingualMessage(
                            ChatColor.AQUA + "🎮 イベントポイントランキング 🎮",
                            ChatColor.AQUA + "🎮 Event Points Ranking 🎮"
                        );
                        broadcastMultilingualMessage(
                            ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                            ChatColor.AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                        );
                        
                        if (!eventRanking.isEmpty()) {
                            for (int i = 0; i < Math.min(3, eventRanking.size()); i++) {
                                Map.Entry<UUID, Integer> entry = eventRanking.get(i);
                                String playerName = playerNames.getOrDefault(entry.getKey(), "Unknown");
                                int eventPoints = entry.getValue();
                                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                                String status = Bukkit.getPlayer(entry.getKey()) != null ? "🟢" : "⚫";
                                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "  " + medal + " " + status + " " + ChatColor.WHITE + playerName + ChatColor.YELLOW + ": " + ChatColor.AQUA + eventPoints + "ポイント");
                            }
                        } else {
                            broadcastMultilingualMessage(
                                ChatColor.GRAY + "  🎮 まだイベント記録がありません。イベントに参加しましょう！",
                                ChatColor.GRAY + "  🎮 No event records yet. Join events!"
                            );
                        }
                        
                        // 2秒後に参加促進メッセージ
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                        // Title表示で参加促進を大きく表示
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            player.sendTitle(
                                ChatColor.GREEN + "🎮 あなたも参加しよう！ 🎮",
                                ChatColor.AQUA + "サーバー: otougame.falixsrv.me",
                                10, 60, 20
                            );
                        }
                        
                        // チャットでも表示
                                broadcastMultilingualMessage(
            ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
        broadcastMultilingualMessage(
            ChatColor.GREEN + "🎮 あなたも参加してランキングに挑戦しよう！ 🎮",
            ChatColor.GREEN + "🎮 Join us and challenge the rankings! 🎮"
        );
        broadcastMultilingualMessage(
            ChatColor.AQUA + "📺 サーバー: otougame.falixsrv.me",
            ChatColor.AQUA + "📺 Server: otougame.falixsrv.me"
        );
        broadcastMultilingualMessage(
            ChatColor.GOLD + "💡 配信でリアルタイムランキングをチェック！",
            ChatColor.GOLD + "💡 Check real-time rankings on stream!"
        );
        broadcastMultilingualMessage(
            ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
                        
                        // ランキング発表終了
                        isRankingAnnouncement = false;
                    }, 40L);
                    
                }, 40L);
                
            }, 40L);
            
        }, 100L);
    }
    
    private List<Map.Entry<UUID, Long>> getTotalSurvivalTimeRanking() {
        List<Map.Entry<UUID, Long>> ranking = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<UUID, Long> entry : playerJoinTimes.entrySet()) {
            UUID playerId = entry.getKey();
            long joinTime = entry.getValue();
            long currentSurvivalTime = currentTime - joinTime;
            long totalTime = playerTotalSurvivalTime.getOrDefault(playerId, 0L) + currentSurvivalTime;
            
            // 累計生存時間が1分以上ある場合のみ
            if (totalTime > 60000) {
                ranking.add(new AbstractMap.SimpleEntry<>(playerId, totalTime));
            }
        }
        
        // 累計生存時間の長い順にソート
        ranking.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return ranking;
    }
    
    private List<Map.Entry<UUID, Integer>> getEnderDragonKillRanking() {
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>();
        
        for (Map.Entry<UUID, Integer> entry : playerEnderDragonKills.entrySet()) {
            UUID playerId = entry.getKey();
            int dragonKills = entry.getValue();
            
            // エンダードラゴン討伐数が1以上ある場合のみ
            if (dragonKills > 0) {
                ranking.add(new AbstractMap.SimpleEntry<>(playerId, dragonKills));
            }
        }
        
        // 討伐数の多い順にソート
        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return ranking;
    }
    
    private List<Map.Entry<UUID, Integer>> getKillCountRanking() {
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>();
        
        for (Map.Entry<UUID, Integer> entry : playerKillCounts.entrySet()) {
            UUID playerId = entry.getKey();
            int kills = entry.getValue();
            
            // キル数が1以上ある場合のみ（過去記録も含む）
            if (kills > 0) {
                ranking.add(new AbstractMap.SimpleEntry<>(playerId, kills));
            }
        }
        
        // キル数の多い順にソート
        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return ranking;
    }
    
    private List<Map.Entry<UUID, Integer>> getEventPointsRanking() {
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>();
        
        for (Map.Entry<UUID, Integer> entry : playerEventPoints.entrySet()) {
            UUID playerId = entry.getKey();
            int eventPoints = entry.getValue();
            
            // イベントポイントが1以上ある場合のみ
            if (eventPoints > 0) {
                ranking.add(new AbstractMap.SimpleEntry<>(playerId, eventPoints));
            }
        }
        
        // イベントポイントの多い順にソート
        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return ranking;
    }



    private void rebuildOrder() {
        patrolOrder.clear();
        int totalPlayers = Bukkit.getOnlinePlayers().size();
        int eligibleCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isEligible(p)) {
                patrolOrder.add(p.getUniqueId());
                eligibleCount++;
            }
        }
        patrolIndex = patrolOrder.isEmpty() ? -1 : 0;
        if (!patrolOrder.contains(currentTargetId)) currentTargetId = null;
        // デバッグログ（頻繁に呼ばれるため削除）
        // getLogger().info("rebuildOrder: オンライン=" + totalPlayers + "人, 対象=" + eligibleCount + "人, patrollerId=" + patrollerId);
    }

    private boolean isEligible(Player p) {
        if (p == null || !p.isOnline()) return false;
        if (p.isDead()) return false;
        // 実行者自身は対象外
        if (patrollerId != null && p.getUniqueId().equals(patrollerId)) {
            // getLogger().info("isEligible: " + p.getName() + " は実行者自身のため除外");
            return false;
        }
        // explicit name exclude
        if (!excludedPlayers.isEmpty() && excludedPlayers.contains(p.getName().toLowerCase())) {
            return false;
        }
        if (exemptPermission != null && !exemptPermission.isBlank() && p.hasPermission(exemptPermission)) {
            return false;
        }
        if (!allowedWorlds.isEmpty()) {
            World w = p.getWorld();
            if (w == null || !allowedWorlds.contains(w.getName())) {
                return false;
            }
        }
        return true;
    }

    private Player nextTarget() {
        if (patrolOrder.isEmpty()) return null;
        int tries = patrolOrder.size();
        while (tries-- > 0) {
            patrolIndex = (patrolIndex + 1) % patrolOrder.size();
            Player candidate = Bukkit.getPlayer(patrolOrder.get(patrolIndex));
            if (isEligible(candidate)) return candidate;
        }
        return null;
    }

    private void tickPatrolOnlineConsistency() {
        // 既存の不適格ターゲットを除外
        patrolOrder.removeIf(uuid -> !isEligible(Bukkit.getPlayer(uuid)));
        // 新規にオンラインになった適格プレイヤーを追加
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isEligible(p) && !patrolOrder.contains(p.getUniqueId())) {
                patrolOrder.add(p.getUniqueId());
            }
        }
        if (patrolOrder.isEmpty()) {
            patrolIndex = -1;
        } else if (patrolIndex < 0) {
            patrolIndex = 0;
        }
    }

    private boolean autoRunning = false;
    private long nextSwitchAtTick = 0L;

    private void tickAuto() {
        if (!autoRunning) return;
        
        // ランキング発表中は視点移動を停止
        if (isRankingAnnouncement) {
            return;
        }
        
        tickPatrolOnlineConsistency();
        Player patroller = getPatroller();
        if (patroller == null) {
            // 実行者が見つからない場合、自動巡回を停止
            getLogger().warning("実行者が見つからないため、自動巡回を停止します");
            stopAutoPatrol();
            return;
        }
        
        // 参加者がいない場合の観光地巡回
        if (patrolOrder.isEmpty()) {
            // 観光地巡回モードに入る
            if (!isInTouristMode) {
                isInTouristMode = true;
                getLogger().info("観光地巡回モードを開始");
                patroller.sendMessage(ChatColor.GREEN + "🗺️ 観光地巡回モードを開始しました！");
            }
            
            // 指定された間隔で観光地を巡回
            if (Bukkit.getCurrentTick() >= nextTouristLocationSwitch) {
                cycleTouristLocation(patroller);
                nextTouristLocationSwitch = Bukkit.getCurrentTick() + intervalTicks;
            }
            
            // 定期的に参加者募集メッセージを表示
            if (Bukkit.getCurrentTick() % 200 == 0) { // 10秒ごと
                patroller.sendActionBar(ChatColor.GOLD + "🎥 参加者募集中！お気軽に参加してください！");
                patroller.sendActionBar(ChatColor.AQUA + "🗺️ 現在観光地を探索中です");
            }
            return;
        }
        
        // 人が参加した場合、観光地巡回モードを終了
        if (isInTouristMode) {
            isInTouristMode = false;
            getLogger().info("観光地巡回モードを終了、人の視点に切り替え");
            patroller.sendMessage(ChatColor.GREEN + "🎉 参加者が来ました！人の視点に切り替えます");
        }
        
        long nowMs = System.currentTimeMillis();
        if (nowMs < backoffUntilMs) return;
        
        // 対象が1人だけなら、その人を映し続ける（切替しない）
        if (patrolOrder.size() == 1) {
            Player only = Bukkit.getPlayer(patrolOrder.get(0));
            if (isEligible(only)) {
                if (currentTargetId == null || !currentTargetId.equals(only.getUniqueId())) {
                    cycleToTarget(patroller, only);
                } else {
                    // 視点追従チェックを削除（ブルブル完全防止）
                    // checkAndRefreshSpectatorTarget(patroller, only);
                }
                patroller.sendActionBar(ChatColor.GOLD + "🎯 いま『" + only.getName() + "』さんの視点です（1人参加中）");
                return; // 切替処理は行わない
            }
        }
        
        Player target = currentTargetId == null ? null : Bukkit.getPlayer(currentTargetId);
        if (target == null || !isEligible(target)) {
            // 視点を外す処理を即座に実行（遅延を削除）
            if (idleSpectator && patroller.getGameMode() != GameMode.SPECTATOR) {
                patroller.setGameMode(GameMode.SPECTATOR);
                patroller.setSpectatorTarget(null);
            } else if (useSpectatorCamera) {
                patroller.setSpectatorTarget(null);
            }
            currentTargetId = null;
            backoffUntilMs = nowMs + switchBackoffMs;
            Player next = nextTarget();
            if (next != null) {
                cycleToTarget(patroller, next);
            } else {
                patroller.sendActionBar(ChatColor.RED + "次の対象が見つかりません");
            }
            return;
        }
        
        // 視点追従チェック（商人視点奪取防止版）
        checkAndRefreshSpectatorTarget(patroller, target);
        
        if (Bukkit.getCurrentTick() >= nextSwitchAtTick && nowMs >= lockUntilMs) {
            Player next = nextTarget();
            if (next != null && !next.getUniqueId().equals(currentTargetId)) {
                getLogger().info("視点切替: " + target.getName() + " → " + next.getName());
                cycleToTarget(patroller, next);
            } else {
                if (next == null) {
                    patroller.sendActionBar(ChatColor.RED + "次の対象が見つかりません（切替スキップ）");
                } else {
                    patroller.sendActionBar(ChatColor.YELLOW + "同じ対象のため切替をスキップ: " + next.getName());
                }
                nextSwitchAtTick = Bukkit.getCurrentTick() + 40L;
            }
        }
        
        // ActionBarを定期的に更新（視点表示を維持）
        if (currentTargetId != null) {
            Player currentTarget = Bukkit.getPlayer(currentTargetId);
            if (currentTarget != null && currentTarget.isOnline()) {
                patroller.sendActionBar(ChatColor.GOLD + "🎯 いま『" + currentTarget.getName() + "』さんの視点です");
            }
        }
    }

    // 視点追従チェックと再奪取機能（商人視点奪取防止版）
    private long lastSpectatorCheck = 0L;
    private static final long SPECTATOR_CHECK_INTERVAL = 10000L; // 10秒間隔（短縮）
    
    private void checkAndRefreshSpectatorTarget(Player patroller, Player target) {
        if (!useSpectatorCamera || target == null || !target.isOnline()) return;
        
        long currentTime = System.currentTimeMillis();
        
        // 10秒に1回のみチェック（視点奪取確実性向上）
        if (currentTime - lastSpectatorCheck < SPECTATOR_CHECK_INTERVAL) {
            return;
        }
        lastSpectatorCheck = currentTime;
        
        // 現在の視点をチェック
        Entity currentTarget = patroller.getSpectatorTarget();
        if (currentTarget == null || !currentTarget.equals(target)) {
            // 視点が外れている場合のみ再奪取
            getLogger().info("視点追従: " + target.getName() + " の視点を再奪取");
            try {
                // 商人視点奪取防止：プレイヤー以外のエンティティの場合は即座に再設定
                if (currentTarget != null && !(currentTarget instanceof Player)) {
                    getLogger().info("商人視点奪取検出: " + currentTarget.getName() + " → " + target.getName());
                    patroller.setSpectatorTarget(target);
                } else {
                    // 通常の視点再奪取
                    patroller.setSpectatorTarget(target);
                }
            } catch (Exception e) {
                getLogger().warning("視点再奪取失敗: " + e.getMessage());
            }
        }
    }

    private Player getPatroller() {
        Player bound = patrollerId == null ? null : Bukkit.getPlayer(patrollerId);
        if (bound != null && bound.isOnline()) {
            // ログ出力を削除（頻繁に呼ばれるため）
            return bound;
        } else if (bound != null && !bound.isOnline()) {
            // バインドされた実行者が切断している場合
            getLogger().warning("getPatroller: バインドされたパトローラー " + bound.getName() + " が切断しています");
            patrollerId = null; // バインドを解除
        }
        
        // 権限を持つ他のプレイヤーを探す
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("patrolspectator.use")) {
                return p;
            }
        }
        return null;
    }

    private void startAutoPatrol(Player patroller, long ticks) {
        autoRunning = true;
        patrollerId = patroller.getUniqueId();
        // 開始地点を記録
        try { patrolStartLocation = patroller.getLocation().clone(); } catch (Throwable ignored) {}
        if (ticks <= 0) ticks = intervalTicks;
        intervalTicks = ticks;
        nextSwitchAtTick = Bukkit.getCurrentTick();
        rebuildOrder();
        
        // アイテム保護: インベントリと装備を保存
        savePlayerInventory(patroller);
        
        // パトローラーを無敵状態にする
        patroller.setInvulnerable(true);
        patroller.setCollidable(false);
        
        // 視聴者参加促進メッセージ
        int total = Bukkit.getOnlinePlayers().size();
        String names = patrolOrder.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .collect(Collectors.joining(", "));
        
        // 配信開始メッセージ
        patroller.sendMessage(ChatColor.GREEN + "🎥 無人配信を開始しました！");
        patroller.sendMessage(ChatColor.YELLOW + "📊 現在の参加者: " + total + "人 / 巡回対象: " + patrolOrder.size() + "人");
        patroller.sendMessage(ChatColor.GRAY + "👥 参加者: " + (names.isEmpty() ? "まだいません" : names));
        patroller.sendMessage(ChatColor.AQUA + "⏰ 視点切替間隔: " + (ticks/20) + "秒");
        patroller.sendMessage(ChatColor.GOLD + "💡 視聴者の皆さん、お気軽に参加してください！");
        patroller.sendMessage(ChatColor.LIGHT_PURPLE + "🛡️ 無敵状態になりました（アイテム保護済み）");
        
        // 参加者がいない場合の即座のメッセージ
        if (patrolOrder.isEmpty()) {
            patroller.sendActionBar(ChatColor.GOLD + "🎥 無人配信中！参加者募集中です");
            patroller.sendActionBar(ChatColor.AQUA + "💫 お気軽に参加してください！");
        }
        
        // 全プレイヤーに参加促進メッセージ
        if (announceToPlayers) {
            Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "🎥 無人配信が開始されました！");
            Bukkit.getServer().broadcastMessage(ChatColor.AQUA + "💫 参加して配信に出演しませんか？");
            Bukkit.getServer().broadcastMessage(ChatColor.GRAY + "📝 参加方法: サーバーに接続するだけ！");
        }
        
        if (idleSpectator && patroller.getGameMode() != GameMode.SPECTATOR) {
            patroller.setGameMode(GameMode.SPECTATOR);
            patroller.setSpectatorTarget(null);
        }
    }

    private void stopAutoPatrol() {
        autoRunning = false;
        currentTargetId = null;
        
        // パトローラーの無敵状態を解除
        if (patrollerId != null) {
            Player patroller = Bukkit.getPlayer(patrollerId);
            if (patroller != null && patroller.isOnline()) {
                patroller.setInvulnerable(false);
                patroller.setCollidable(true);
            }
        }
        
        patrollerId = null;
    }
    
    // アイテム保護用の変数
    private ItemStack[] savedInventoryContents = null;
    private ItemStack[] savedArmorContents = null;
    private boolean inventorySaved = false;
    
    private void savePlayerInventory(Player player) {
        // 既に保存済みの場合は何もしない
        if (inventorySaved) {
            getLogger().info("アイテム保護: 既に保存済みです");
            return;
        }
        
        // インベントリと装備を保存
        savedInventoryContents = player.getInventory().getContents().clone();
        savedArmorContents = player.getInventory().getArmorContents().clone();
        inventorySaved = true;
        getLogger().info("アイテム保護: " + player.getName() + "のインベントリを保存しました");
    }
    
    private void restorePlayerInventory(Player player) {
        if (savedInventoryContents != null && inventorySaved) {
            // インベントリと装備を復元
            player.getInventory().setContents(savedInventoryContents);
            player.getInventory().setArmorContents(savedArmorContents);
            getLogger().info("アイテム復元: " + player.getName() + "のインベントリを復元しました");
            
            // 保存データをクリア
            savedInventoryContents = null;
            savedArmorContents = null;
            inventorySaved = false;
        } else {
            getLogger().info("アイテム復元: 保存されたアイテムがありません");
        }
    }

    private void clearSpectator(Player patroller) {
        if (useSpectatorCamera) {
            patroller.setSpectatorTarget(null);
        }
        if (idleSpectator && patroller.getGameMode() != GameMode.SPECTATOR) {
            patroller.setGameMode(GameMode.SPECTATOR);
        }
    }

    private void cycleToTarget(Player patroller, Player target) {
        if (target == null || !target.isOnline()) {
            getLogger().warning("cycleToTarget: ターゲットが無効 - " + (target != null ? target.getName() : "null"));
            return;
        }
        
        // デバッグ情報（頻繁に呼ばれるため削除）
        // getLogger().info("cycleToTarget: パトローラー=" + patroller.getName() + ", ターゲット=" + target.getName());
        // getLogger().info("cycleToTarget: useSpectatorCamera=" + useSpectatorCamera + ", idleSpectator=" + idleSpectator);
        
        // ゲームモード設定
        if (useSpectatorCamera || idleSpectator) {
            if (patroller.getGameMode() != GameMode.SPECTATOR) {
                patroller.setGameMode(GameMode.SPECTATOR);
                getLogger().info("ゲームモードをスペクテイターに変更: " + patroller.getName());
            }
        }
        
        // 視点奪取の改善版（確実性向上）
        if (useSpectatorCamera) {
            try {
                // 現在の視点をチェック
                Entity currentTarget = patroller.getSpectatorTarget();
                
                // 商人視点奪取防止：プレイヤー以外のエンティティの場合は即座に再設定
                if (currentTarget != null && !(currentTarget instanceof Player)) {
                    getLogger().info("商人視点奪取検出（cycleToTarget）: " + currentTarget.getName() + " → " + target.getName());
                }
                
                // 視点を設定
                patroller.setSpectatorTarget(target);
                
                // 視点設定の確認（少し待ってから）
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Entity confirmedTarget = patroller.getSpectatorTarget();
                    if (confirmedTarget != null && confirmedTarget.equals(target)) {
                        // getLogger().info("視点奪取成功確認: " + target.getName());
                    } else {
                        getLogger().warning("視点奪取失敗確認: 期待=" + target.getName() + ", 実際=" + (confirmedTarget != null ? confirmedTarget.getName() : "null"));
                        // 失敗した場合は再試行
                        try {
                            patroller.setSpectatorTarget(target);
                        } catch (Exception e) {
                            getLogger().warning("視点奪取再試行失敗: " + e.getMessage());
                        }
                    }
                }, 2L); // 0.1秒後に確認
                
            } catch (Exception e) {
                getLogger().warning("視点奪取失敗: " + e.getMessage());
            }
        } else {
            patroller.teleport(target.getLocation());
        }
        
        // 状態更新
        currentTargetId = target.getUniqueId();
        nextSwitchAtTick = Bukkit.getCurrentTick() + intervalTicks;
        
        // 表示更新（視点設定後に遅延実行）
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (target.isOnline() && patroller.isOnline()) {
                if (useTitle) {
                    patroller.sendTitle(ChatColor.GREEN + "🎯 " + target.getName() + " 視点", ChatColor.YELLOW + "いま『" + target.getName() + "』さんの視点です", 10, 60, 20);
                }
                // ActionBarにも常時表示（タイトルと併用）
                patroller.sendActionBar(ChatColor.GOLD + "🎯 いま『" + target.getName() + "』さんの視点です");
                if (announceToPlayers) {
                    String msg = announceFormat.replace("%target%", target.getName()).replace("%patroller%", patroller.getName());
                    Bukkit.getServer().broadcastMessage(msg);
                }
            }
        }, 10L); // 0.5秒後に表示（視点設定完了後）
    }

    // 古いメソッドを削除（安全版に置き換え済み）

    // ネザー要塞を探す
    private List<Location> findNetherFortresses(World world) {
        List<Location> fortresses = new ArrayList<>();
        for (int x = -2000; x <= 2000; x += 150) {
            for (int z = -2000; z <= 2000; z += 150) {
                for (int y = 0; y < world.getMaxHeight(); y += 5) {
                    Location loc = new Location(world, x, y, z);
                    if (loc.getBlock().getType().name().contains("NETHER_BRICK")) {
                        fortresses.add(loc);
                        break;
                    }
                }
            }
        }
        return fortresses;
    }

    // ブレイズスポナーを探す
    private List<Location> findBlazeSpawners(World world) {
        List<Location> spawners = new ArrayList<>();
        for (int x = -2000; x <= 2000; x += 100) {
            for (int z = -2000; z <= 2000; z += 100) {
                for (int y = 0; y < world.getMaxHeight(); y += 5) {
                    Location loc = new Location(world, x, y, z);
                    if (loc.getBlock().getType().name().contains("SPAWNER")) {
                        spawners.add(loc);
                        break;
                    }
                }
            }
        }
        return spawners;
    }

    // エンドシティを探す
    private List<Location> findEndCities(World world) {
        List<Location> cities = new ArrayList<>();
        for (int x = -5000; x <= 5000; x += 200) {
            for (int z = -5000; z <= 5000; z += 200) {
                for (int y = 0; y < world.getMaxHeight(); y += 10) {
                    Location loc = new Location(world, x, y, z);
                    if (loc.getBlock().getType().name().contains("PURPUR") ||
                        loc.getBlock().getType().name().contains("END_STONE_BRICK")) {
                        cities.add(loc);
                        break;
                    }
                }
            }
        }
        return cities;
    }

    // エンドラの巣を探す
    private List<Location> findDragonPerches(World world) {
        List<Location> perches = new ArrayList<>();
        // エンドラの巣の中心付近
        perches.add(new Location(world, 0, 70, 0));
        return perches;
    }

    // 古いメソッドを削除（安全版に置き換え済み）



    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player quittingPlayer = e.getPlayer();
        UUID playerId = quittingPlayer.getUniqueId();
        
        // 生存時間を計算して記録
        if (playerJoinTimes.containsKey(playerId)) {
            long joinTime = playerJoinTimes.get(playerId);
            long survivalTime = System.currentTimeMillis() - joinTime;
            long survivalMinutes = survivalTime / (1000 * 60);
            
            getLogger().info("プレイヤー退出: " + quittingPlayer.getName() + 
                           " (生存時間: " + survivalMinutes + "分, キル数: " + 
                           playerKillCounts.getOrDefault(playerId, 0) + 
                           ", デス数: " + playerDeathCounts.getOrDefault(playerId, 0) + ")");
        }
        
        // ターゲットプレイヤーが切断した場合（ブルブル防止のため遅延処理）
        if (quittingPlayer.getUniqueId().equals(currentTargetId)) {
            // 即座に視点を外さず、少し待ってから処理（ブルブル防止）
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (currentTargetId != null && currentTargetId.equals(quittingPlayer.getUniqueId())) {
                    backoffUntilMs = System.currentTimeMillis() + switchBackoffMs;
                    currentTargetId = null;
                    getLogger().info("ターゲットプレイヤーが切断: " + quittingPlayer.getName() + " (遅延処理)");
                }
            }, 20L); // 1秒後に処理
        }
        
        // 実行者（パトローラー）が切断した場合
        if (patrollerId != null && quittingPlayer.getUniqueId().equals(patrollerId)) {
            getLogger().info("実行者が切断: " + quittingPlayer.getName() + " - 自動巡回を停止します");
            stopAutoPatrol();
            
            // 全プレイヤーに通知
            if (announceToPlayers) {
                Bukkit.getServer().broadcastMessage(ChatColor.RED + "📺 配信が終了しました");
                Bukkit.getServer().broadcastMessage(ChatColor.GRAY + "実行者が切断したため、自動巡回を停止しました");
            }
        }
        
        // 参加率向上システムの呼び出し
        if (engagementSystem != null) {
            engagementSystem.onPlayerQuit(quittingPlayer);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player deadPlayer = e.getEntity();
        UUID playerId = deadPlayer.getUniqueId();
        Player killer = deadPlayer.getKiller();
        
        // 【除外チェック】死亡したプレイヤーが除外対象の場合はランキング記録しない
        if (isPlayerExcluded(deadPlayer)) {
            getLogger().info("除外対象プレイヤー死亡: " + deadPlayer.getName() + " (ランキング記録除外)");
            return;
        }
        
        // プレイヤーキル（PK）の場合の記録
        if (killer != null && !killer.equals(deadPlayer)) {
            UUID killerId = killer.getUniqueId();
            
            // 【除外チェック】キラーが除外対象の場合はPK記録しない
            if (isPlayerExcluded(killer)) {
                getLogger().info("除外対象プレイヤーPK: " + killer.getName() + " → " + deadPlayer.getName() + " (ランキング記録除外)");
            } else {
                int killCount = playerKillCounts.getOrDefault(killerId, 0) + 1;
                playerKillCounts.put(killerId, killCount);
                
                getLogger().info("PK記録: " + killer.getName() + " → " + deadPlayer.getName() + " (PK数: " + killCount + ")");
                
                // 全プレイヤーに通知
                Bukkit.getServer().broadcastMessage(ChatColor.RED + "⚔️ " + killer.getName() + "が" + deadPlayer.getName() + "を倒しました！");
            }
        }
        
        // 死亡時の累計生存時間を更新
        if (playerJoinTimes.containsKey(playerId)) {
            long joinTime = playerJoinTimes.get(playerId);
            long currentSurvivalTime = System.currentTimeMillis() - joinTime;
            long totalTime = playerTotalSurvivalTime.getOrDefault(playerId, 0L) + currentSurvivalTime;
            playerTotalSurvivalTime.put(playerId, totalTime);
            
            // 死亡数をカウント
            playerDeathCounts.put(playerId, playerDeathCounts.getOrDefault(playerId, 0) + 1);
            
            getLogger().info("プレイヤー死亡: " + deadPlayer.getName() + 
                           " (累計生存時間: " + (totalTime / (1000 * 60)) + "分, 死亡数: " + 
                           playerDeathCounts.get(playerId) + ")");
        }
        
        // ターゲットプレイヤーが死亡した場合（ブルブル防止のため遅延処理）
        if (playerId.equals(currentTargetId)) {
            // 即座に視点を外さず、少し待ってから処理（ブルブル防止）
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (currentTargetId != null && currentTargetId.equals(playerId)) {
                    lockUntilMs = 0L;
                    backoffUntilMs = System.currentTimeMillis() + switchBackoffMs;
                    currentTargetId = null;
                    getLogger().info("ターゲットプレイヤーが死亡: " + deadPlayer.getName() + " (遅延処理)");
                }
            }, 20L); // 1秒後に処理
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player respawnPlayer = e.getPlayer();
        UUID playerId = respawnPlayer.getUniqueId();
        
        // 実行者（パトローラー）はランキングから除外
        if (patrollerId != null && playerId.equals(patrollerId)) {
            getLogger().info("実行者リスポーン: " + respawnPlayer.getName() + " (ランキング記録除外)");
            if (playerId.equals(currentTargetId)) {
                currentTargetId = null;
            }
            return; // ランキング記録をスキップ
        }
        
        // リスポーン時に新しい参加時間を記録
        playerJoinTimes.put(playerId, System.currentTimeMillis());
        
        if (playerId.equals(currentTargetId)) {
            // リスポーン時も遅延処理（ブルブル防止）
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (currentTargetId != null && currentTargetId.equals(playerId)) {
                    currentTargetId = null;
                    getLogger().info("ターゲットプレイヤーがリスポーン: " + respawnPlayer.getName() + " (遅延処理)");
                }
            }, 20L); // 1秒後に処理
        }
    }
    

    
    // エンドラ討伐イベント
    @EventHandler
    public void onEnderDragonDeath(EntityDeathEvent e) {
        getLogger().info("エンティティ死亡イベント発生: " + e.getEntity().getType());
        
        if (e.getEntity().getType() == EntityType.ENDER_DRAGON) {
            getLogger().info("エンドラ討伐検知！");
            
            // エンドラ討伐時の処理
            Player killer = e.getEntity().getKiller();
            if (killer != null) {
                UUID playerId = killer.getUniqueId();
                
                getLogger().info("エンドラ討伐者: " + killer.getName() + " (UUID: " + playerId + ")");
                
                // 【除外チェック】討伐者が除外対象の場合はランキング記録しない
                if (isPlayerExcluded(killer)) {
                    getLogger().info("除外対象プレイヤーエンドラ討伐: " + killer.getName() + " (ランキング記録除外)");
                    return; // ランキング記録をスキップ
                }
                
                int dragonKills = playerEnderDragonKills.getOrDefault(playerId, 0) + 1;
                playerEnderDragonKills.put(playerId, dragonKills);
                
                getLogger().info("エンドラ討伐記録: " + killer.getName() + " (討伐数: " + dragonKills + ")");
                
                // 全プレイヤーに通知（Title表示も追加）
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendTitle(
                        ChatColor.LIGHT_PURPLE + "🐉 エンドラ討伐！ 🐉",
                        ChatColor.YELLOW + killer.getName() + "がエンドラを討伐しました！",
                        10, 60, 20
                    );
                }
                
                // チャットでも通知
                Bukkit.getServer().broadcastMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                Bukkit.getServer().broadcastMessage(ChatColor.LIGHT_PURPLE + "🐉 " + killer.getName() + "がエンドラを討伐しました！ 🐉");
                Bukkit.getServer().broadcastMessage(ChatColor.YELLOW + "🎉 おめでとうございます！");
                Bukkit.getServer().broadcastMessage(ChatColor.LIGHT_PURPLE + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                
                // ランキングデータを即座に保存
                saveRankingData();
                
                // エンドワールドリセットの準備
                scheduleEndWorldReset();
                
            } else {
                getLogger().warning("エンドラ討伐者を特定できませんでした");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player joiningPlayer = e.getPlayer();
        UUID playerId = joiningPlayer.getUniqueId();
        
        // 【第1段階】実行者（パトローラー）はランキングから除外
        if (patrollerId != null && playerId.equals(patrollerId)) {
            getLogger().info("実行者参加: " + joiningPlayer.getName() + " (ランキング記録除外)");
            
            // 【第2段階】既存データがあれば削除
            cleanupPatrollerData(playerId, joiningPlayer.getName());
            
            return; // ランキング記録をスキップ
        }
        
        // 【第3段階】除外リストに含まれている場合もランキングから除外
        if (excludedPlayers.contains(joiningPlayer.getName().toLowerCase())) {
            getLogger().info("除外リスト参加: " + joiningPlayer.getName() + " (ランキング記録除外)");
            
            // 既存データがあれば削除
            cleanupPatrollerData(playerId, joiningPlayer.getName());
            
            return; // ランキング記録をスキップ
        }
        
        // プレイヤー情報を記録（ランキング用）
        playerJoinTimes.put(playerId, System.currentTimeMillis());
        playerNames.put(playerId, joiningPlayer.getName());
        
        // 初回参加の場合はキル数・デス数・イベントポイントを0で初期化
        if (!playerKillCounts.containsKey(playerId)) {
            playerKillCounts.put(playerId, 0);
            playerDeathCounts.put(playerId, 0);
            playerEventPoints.put(playerId, 0);
        }
        
        getLogger().info("プレイヤー参加: " + joiningPlayer.getName() + " (ランキング記録開始)");
        
        // 既存の巡回機能
        if (!autoRunning) return;
        if (!isEligible(joiningPlayer)) return;
        if (!patrolOrder.contains(playerId)) {
            patrolOrder.add(playerId);
        }
        
        // 新規参加者への歓迎メッセージ
        sendMultilingualMessage(joiningPlayer,
            ChatColor.GREEN + "🎉 配信に参加していただき、ありがとうございます！",
            ChatColor.GREEN + "🎉 Thank you for joining the stream!"
        );
        sendMultilingualMessage(joiningPlayer,
            ChatColor.AQUA + "📺 あなたのゲームプレイが配信されます",
            ChatColor.AQUA + "📺 Your gameplay will be streamed"
        );
        sendMultilingualMessage(joiningPlayer,
            ChatColor.GOLD + "💡 自由にプレイしてください！",
            ChatColor.GOLD + "💡 Please play freely!"
        );
        
        // 全プレイヤーに新規参加者を通知
        if (announceToPlayers) {
                    broadcastMultilingualMessage(
            ChatColor.GREEN + "🎉 " + joiningPlayer.getName() + "さんが配信に参加しました！",
            ChatColor.GREEN + "🎉 " + joiningPlayer.getName() + " joined the stream!"
        );
        broadcastMultilingualMessage(
            ChatColor.AQUA + "📺 視点が切り替わる可能性があります",
            ChatColor.AQUA + "📺 Viewpoint may switch"
        );
        }
        
        // 現在ターゲットがいない場合は即座に視点を奪う
        if (currentTargetId == null) {
            Player patroller = getPatroller();
            if (patroller != null) {
                cycleToTarget(patroller, joiningPlayer);
            }
        }
        
        // 参加率向上システムの呼び出し
        if (engagementSystem != null) {
            engagementSystem.onPlayerJoin(joiningPlayer);
        }
    }

    @EventHandler
    public void onCombat(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && Objects.equals(p.getUniqueId(), currentTargetId)) {
            lockUntilMs = Math.max(lockUntilMs, System.currentTimeMillis() + combatLockMs); // extend stay on action
        }
    }
    
    // エンドラ討伐の代替検知（EntityDeathEventの補完）
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getType() == EntityType.ENDER_DRAGON) {
            getLogger().info("EntityDeathEvent: エンドラ討伐検知（代替）");
            
            // 最後にダメージを与えたプレイヤーを探す
            Player killer = null;
            for (Entity entity : e.getEntity().getNearbyEntities(50, 50, 50)) {
                if (entity instanceof Player) {
                    Player player = (Player) entity;
                    // 最後にダメージを与えたプレイヤーを特定（簡易版）
                    if (player.getGameMode() != GameMode.SPECTATOR) {
                        killer = player;
                        break;
                    }
                }
            }
            
            if (killer != null) {
                UUID playerId = killer.getUniqueId();
                
                getLogger().info("代替検知: エンドラ討伐者: " + killer.getName());
                
                // 実行者（パトローラー）はランキングから除外
                if (patrollerId != null && playerId.equals(patrollerId)) {
                    getLogger().info("実行者エンドラ討伐（代替検知）: " + killer.getName() + " (ランキング記録除外)");
                    return;
                }
                
                int dragonKills = playerEnderDragonKills.getOrDefault(playerId, 0) + 1;
                playerEnderDragonKills.put(playerId, dragonKills);
                
                getLogger().info("代替検知: エンドラ討伐記録: " + killer.getName() + " (討伐数: " + dragonKills + ")");
                
                // 全プレイヤーに通知
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendTitle(
                        ChatColor.LIGHT_PURPLE + "🐉 エンドラ討伐！ 🐉",
                        ChatColor.YELLOW + killer.getName() + "がエンドラを討伐しました！",
                        10, 60, 20
                    );
                }
                
                Bukkit.getServer().broadcastMessage(ChatColor.LIGHT_PURPLE + "🐉 " + killer.getName() + "がエンドラを討伐しました！ 🐉");
                
                // ランキングデータを即座に保存
                saveRankingData();
            }
        }
    }
    
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!autoRunning) return;
        Player changed = e.getPlayer();
        
        // パトローラーがワールドを変更した場合
        if (patrollerId != null && changed.getUniqueId().equals(patrollerId)) {
            getLogger().info("パトローラーがワールドを変更: " + changed.getName() + " (" + e.getFrom().getName() + " → " + changed.getWorld().getName() + ")");
            // ワールド変更後、少し待ってから視点を再設定
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (currentTargetId != null) {
                    Player target = Bukkit.getPlayer(currentTargetId);
                    if (target != null && target.isOnline()) {
                        cycleToTarget(changed, target);
                        getLogger().info("ワールド変更後の視点再設定: " + target.getName());
                    }
                }
            }, 20L); // 1秒後に再設定
        }
        
        // ターゲットがワールドを変更した場合
        if (currentTargetId != null && changed.getUniqueId().equals(currentTargetId)) {
            getLogger().info("ターゲットがワールドを変更: " + changed.getName() + " (" + e.getFrom().getName() + " → " + changed.getWorld().getName() + ")");
            // ワールド変更後、少し待ってから視点を再設定
            Player patroller = getPatroller();
            if (patroller != null) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (changed.isOnline() && patroller.isOnline()) {
                        cycleToTarget(patroller, changed);
                        getLogger().info("ターゲットワールド変更後の視点再設定: " + changed.getName());
                    }
                }, 20L); // 1秒後に再設定
            }
        }
    }

    // プレイヤーの移動を検知して視点を再奪取（削除 - ブルブル防止）
    // @EventHandler
    // public void onPlayerMove(PlayerMoveEvent e) {
    //     if (!autoRunning || !useSpectatorCamera) return;
    //     
    //     Player movingPlayer = e.getPlayer();
    //     
    //     // 現在のターゲットが移動した場合、視点を再奪取
    //     if (currentTargetId != null && movingPlayer.getUniqueId().equals(currentTargetId)) {
    //         Player patroller = getPatroller();
    //         if (patroller != null && patroller.isOnline()) {
    //             // 移動による視点再奪取を削除（ブルブルの原因）
    //         }
    //     }
    // }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "プレイヤーのみ実行できます。");
            return true;
        }
        if (command.getName().equalsIgnoreCase("patrol")) {
            // OP権限チェック
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "このコマンドはOP権限が必要です");
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("next")) {
                Player target = nextTarget();
                if (target == null) {
                    if (idleSpectator) {
                        player.setGameMode(GameMode.SPECTATOR);
                        player.setSpectatorTarget(null);
                        player.sendMessage(ChatColor.GRAY + "対象がいないため、スペクテイターで待機します（idle）");
                        return true;
                    }
                    player.sendMessage(ChatColor.RED + "巡回対象が見つかりません");
                    return true;
                }
                cycleToTarget(player, target);
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "start":
                    long ticks = intervalTicks;
                    if (args.length >= 2) {
                        try { ticks = 20L * Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
                    }
                    startAutoPatrol(player, ticks);
                    return true;
                case "stop":
                    stopAutoPatrol();
                    player.sendMessage(ChatColor.YELLOW + "巡回を停止しました（Spectator維持）。");
                    return true;
                case "end": // 停止してサバイバルに戻す
                    stopAutoPatrol();
                    player.setSpectatorTarget(null);
                    
                    // アイテムを復元
                    restorePlayerInventory(player);
                    
                    // 開始地点に戻す（あれば）
                    if (patrolStartLocation != null) {
                        try { player.teleport(patrolStartLocation); } catch (Throwable ignored) {}
                    }
                    if (player.getGameMode() != GameMode.SURVIVAL) {
                        player.setGameMode(GameMode.SURVIVAL);
                    }
                    patrolStartLocation = null;
                    player.sendMessage(ChatColor.YELLOW + "巡回を終了しました。サバイバルに戻ります。");
                    player.sendMessage(ChatColor.GREEN + "📦 アイテムを復元しました");
                    return true;
                case "rebuild":
                    rebuildOrder();
                    String names = patrolOrder.stream()
                            .map(Bukkit::getPlayer)
                            .filter(Objects::nonNull)
                            .map(Player::getName)
                            .collect(Collectors.joining(", "));
                    player.sendMessage(ChatColor.YELLOW + "巡回対象を再構築しました（" + patrolOrder.size() + "人）。");
                    player.sendMessage(ChatColor.GRAY + "巡回対象: " + (names.isEmpty() ? "なし" : names));
                    return true;
                case "list":
                    String names2 = patrolOrder.stream()
                            .map(Bukkit::getPlayer)
                            .filter(Objects::nonNull)
                            .map(Player::getName)
                            .collect(Collectors.joining(", "));
                    player.sendMessage(ChatColor.GRAY + "巡回対象一覧: " + (names2.isEmpty() ? "なし" : names2));
                    player.sendMessage(ChatColor.GRAY + "オンライン人数: " + Bukkit.getOnlinePlayers().size());
                    return true;
                case "reload":
                    reloadLocalConfig();
                    rebuildOrder();
                    player.sendMessage(ChatColor.YELLOW + "設定を再読み込みしました。");
                    return true;
                case "addlocation":
                    if (args.length < 5) {
                        player.sendMessage(ChatColor.RED + "使用方法: /patrol addlocation <名前> <x> <y> <z> <説明>");
                        player.sendMessage(ChatColor.YELLOW + "例: /patrol addlocation トライアルチャンバー 400 50 400 古代の試練の場");
                        return true;
                    }
                    try {
                        String name = args[1];
                        int x = Integer.parseInt(args[2]);
                        int y = Integer.parseInt(args[3]);
                        int z = Integer.parseInt(args[4]);
                        String description = args.length > 5 ? String.join(" ", Arrays.copyOfRange(args, 5, args.length)) : "観光地";
                        
                        Location loc = new Location(player.getWorld(), x, y, z);
                        TouristLocation touristLoc = new TouristLocation(name, loc, description, "overworld");
                        touristLocations.add(touristLoc);
                        
                        player.sendMessage(ChatColor.GREEN + "観光地を追加しました: " + name);
                        player.sendMessage(ChatColor.GRAY + "座標: " + x + ", " + y + ", " + z);
                        player.sendMessage(ChatColor.GRAY + "説明: " + description);
                        return true;
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "座標は数値で入力してください");
                        return true;
                    }
                case "removelocation":
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "使用方法: /patrol removelocation <名前>");
                        return true;
                    }
                    String name = args[1];
                    boolean removed = touristLocations.removeIf(loc -> loc.getName().equalsIgnoreCase(name));
                    if (removed) {
                        player.sendMessage(ChatColor.GREEN + "観光地を削除しました: " + name);
                    } else {
                        player.sendMessage(ChatColor.RED + "観光地が見つかりませんでした: " + name);
                    }
                    return true;
                case "listlocations":
                    if (touristLocations.isEmpty()) {
                        player.sendMessage(ChatColor.YELLOW + "🗺️ 観光地が設定されていません");
                        player.sendMessage(ChatColor.GRAY + "観光地を再初期化するには: /patrol reloadlocations");
                        return true;
                    }
                    
                    player.sendMessage(ChatColor.GREEN + "🗺️ 観光地一覧 (" + touristLocations.size() + "箇所):");
                    player.sendMessage(ChatColor.GRAY + "テレポートするには: /patrol teleportlocation <番号>");
                    player.sendMessage("");
                    
                    // ワールド別にグループ化して表示
                    Map<String, List<TouristLocation>> worldGroups = new HashMap<>();
                    for (TouristLocation loc : touristLocations) {
                        worldGroups.computeIfAbsent(loc.getWorldType(), k -> new ArrayList<>()).add(loc);
                    }
                    
                    int globalIndex = 1;
                    for (Map.Entry<String, List<TouristLocation>> entry : worldGroups.entrySet()) {
                        String worldType = entry.getKey();
                        List<TouristLocation> locations = entry.getValue();
                        
                        String worldName = getWorldDisplayName(worldType);
                        String worldIcon = getWorldIcon(worldType);
                        
                        player.sendMessage(ChatColor.AQUA + "=== " + worldIcon + " " + worldName + " (" + locations.size() + "箇所) ===");
                        
                        for (TouristLocation loc : locations) {
                            ChatColor indexColor = globalIndex <= 3 ? ChatColor.GOLD : ChatColor.YELLOW;
                            String indexPrefix = globalIndex <= 3 ? "🏆 " : "";
                            
                            player.sendMessage(indexColor + indexPrefix + globalIndex + ". " + ChatColor.WHITE + loc.getName() + 
                                            ChatColor.GRAY + " (" + loc.getLocation().getBlockX() + ", " + 
                                            loc.getLocation().getBlockY() + ", " + loc.getLocation().getBlockZ() + ")");
                            player.sendMessage(ChatColor.GRAY + "   " + loc.getDescription());
                            
                            globalIndex++;
                        }
                        player.sendMessage("");
                    }
                    
                    player.sendMessage(ChatColor.GREEN + "✅ 観光地の表示が完了しました");
                    return true;
                case "reloadlocations":
                    player.sendMessage(ChatColor.YELLOW + "🗺️ 観光地を再初期化しています...");
                    player.sendMessage(ChatColor.GRAY + "建造物の検索には数秒かかる場合があります");
                    
                    // 既存の観光地をクリア
                    int oldCount = touristLocations.size();
                    touristLocations.clear();
                    player.sendMessage(ChatColor.GRAY + "既存の観光地 " + oldCount + " 箇所をクリアしました");
                    
                    // 非同期で初期化（時間がかかる可能性があるため）
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        try {
                            // 基本的な観光地を先に追加
                            Bukkit.getScheduler().runTask(this, () -> {
                                player.sendMessage(ChatColor.GRAY + "基本的な観光地を追加中...");
                                addBasicTouristLocations();
                            });
                            
                            // 建造物検索の進捗を表示
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                player.sendMessage(ChatColor.GRAY + "建造物を検索中...");
                            }, 40L); // 2秒後
                            
                            // 建造物検索を実行
                            for (World world : Bukkit.getWorlds()) {
                                addStructureLocations(world);
                            }
                            
                            // 結果をメインスレッドで通知
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                int newCount = touristLocations.size();
                                player.sendMessage(ChatColor.GREEN + "✅ 観光地の再初期化完了: " + newCount + "箇所");
                                
                                if (newCount > oldCount) {
                                    player.sendMessage(ChatColor.GREEN + "🎉 " + (newCount - oldCount) + "箇所の新しい観光地を発見しました！");
                                }
                                
                                player.sendMessage(ChatColor.GRAY + "観光地一覧を確認するには: /patrol listlocations");
                                player.sendMessage(ChatColor.GRAY + "観光地にテレポートするには: /patrol teleportlocation <番号>");
                            }, 100L); // 5秒後に結果表示
                            
                        } catch (Exception e) {
                            Bukkit.getScheduler().runTask(this, () -> {
                                player.sendMessage(ChatColor.RED + "❌ 観光地の再初期化でエラーが発生しました: " + e.getMessage());
                                getLogger().warning("観光地再初期化エラー: " + e.getMessage());
                            });
                        }
                    });
                    return true;
                case "teleportlocation":
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "使用方法: /patrol teleportlocation <番号>");
                        player.sendMessage(ChatColor.GRAY + "観光地一覧: /patrol listlocations");
                        return true;
                    }
                    try {
                        int index = Integer.parseInt(args[1]) - 1;
                        if (index >= 0 && index < touristLocations.size()) {
                            TouristLocation loc = touristLocations.get(index);
                            player.teleport(loc.getLocation());
                            player.sendMessage(ChatColor.GREEN + "✅ " + loc.getName() + "にテレポートしました");
                            player.sendMessage(ChatColor.GRAY + loc.getDescription());
                        } else {
                            player.sendMessage(ChatColor.RED + "無効な番号です (1-" + touristLocations.size() + ")");
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "番号は数値で入力してください");
                    }
                    return true;
                case "clearpatrollerdata":
                    if (patrollerId != null) {
                        Player patroller = Bukkit.getPlayer(patrollerId);
                        String patrollerName = patroller != null ? patroller.getName() : "Unknown";
                        
                        // 【3段階対策】完全データクリーンアップ実行
                        cleanupPatrollerData(patrollerId, patrollerName);
                        
                        player.sendMessage(ChatColor.GREEN + "【3段階対策】パトローラーのデータを完全削除しました: " + patrollerName);
                        getLogger().info("【手動実行】パトローラーのデータを完全削除: " + patrollerName);
                    } else {
                        player.sendMessage(ChatColor.RED + "パトローラーが設定されていません");
                    }
                    return true;
                case "clearexcludeddata":
                    // 除外リストのプレイヤーデータを一括削除
                    cleanupExcludedPlayersData();
                    player.sendMessage(ChatColor.GREEN + "【3段階対策】除外リストプレイヤーのデータを完全削除しました");
                    getLogger().info("【手動実行】除外リストプレイヤーのデータを完全削除");
                    return true;
                case "clearplayerdata":
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "使用方法: /patrol clearplayerdata <プレイヤー名>");
                        return true;
                    }
                    String targetName = args[1];
                    Player targetPlayer = Bukkit.getPlayer(targetName);
                    UUID targetId = null;
                    
                    if (targetPlayer != null) {
                        targetId = targetPlayer.getUniqueId();
                    } else {
                        // オフラインプレイヤーの場合、名前からUUIDを検索
                        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
                            if (entry.getValue().equalsIgnoreCase(targetName)) {
                                targetId = entry.getKey();
                                break;
                            }
                        }
                    }
                    
                    if (targetId != null) {
                        cleanupPatrollerData(targetId, targetName);
                        player.sendMessage(ChatColor.GREEN + "【3段階対策】" + targetName + "のデータを完全削除しました");
                        getLogger().info("【手動実行】プレイヤーデータを完全削除: " + targetName);
                    } else {
                        player.sendMessage(ChatColor.RED + "プレイヤー " + targetName + " が見つかりません");
                    }
                    return true;
                case "dragon":
                    // エンドラ討伐手動記録コマンド
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "使用方法: /patrol dragon <プレイヤー名>");
                        return true;
                    }
                    String playerName = args[1];
                    Player dragonPlayer = Bukkit.getPlayer(playerName);
                    if (dragonPlayer != null) {
                        UUID playerId = dragonPlayer.getUniqueId();
                        
                        // 実行者（パトローラー）はランキングから除外
                        if (patrollerId != null && playerId.equals(patrollerId)) {
                            player.sendMessage(ChatColor.YELLOW + "実行者（パトローラー）はランキング記録から除外されます");
                            return true;
                        }
                        
                        int dragonKills = playerEnderDragonKills.getOrDefault(playerId, 0) + 1;
                        playerEnderDragonKills.put(playerId, dragonKills);
                        
                        getLogger().info("手動記録: エンドラ討伐: " + dragonPlayer.getName() + " (討伐数: " + dragonKills + ")");
                        
                        // 全プレイヤーに通知
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            onlinePlayer.sendTitle(
                                ChatColor.LIGHT_PURPLE + "🐉 エンドラ討伐！ 🐉",
                                ChatColor.YELLOW + dragonPlayer.getName() + "がエンドラを討伐しました！",
                                10, 60, 20
                            );
                        }
                        
                        Bukkit.getServer().broadcastMessage(ChatColor.LIGHT_PURPLE + "🐉 " + dragonPlayer.getName() + "がエンドラを討伐しました！ 🐉");
                        
                        // ランキングデータを即座に保存
                        saveRankingData();
                        
                        player.sendMessage(ChatColor.GREEN + dragonPlayer.getName() + "のエンドラ討伐を記録しました（討伐数: " + dragonKills + "）");
                    } else {
                        player.sendMessage(ChatColor.RED + "プレイヤー " + playerName + " が見つかりません");
                    }
                    return true;
                case "engagement":
                    // エンゲージメント統計表示
                    if (engagementSystem != null) {
                        engagementSystem.showEngagementStats(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "エンゲージメントシステムが無効です");
                    }
                    return true;
                case "autoevent":
                    // 自動イベント管理コマンド
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.YELLOW + "自動イベントコマンド:");
                        player.sendMessage(ChatColor.GRAY + "/patrol autoevent status - イベント状況");
                        player.sendMessage(ChatColor.GRAY + "/patrol autoevent stop - 自動イベント停止");
                        player.sendMessage(ChatColor.GRAY + "/patrol autoevent start - 自動イベント開始");
                        return true;
                    }
                    
                    switch (args[1].toLowerCase()) {
                        case "status":
                            if (autoEventSystem != null) {
                                if (autoEventSystem.isEventActive()) {
                                    player.sendMessage(ChatColor.GREEN + "現在のイベント: " + autoEventSystem.getCurrentEvent());
                                } else {
                                    player.sendMessage(ChatColor.GRAY + "現在イベントは開催されていません");
                                }
                                player.sendMessage(ChatColor.GRAY + "自動イベント間隔: 10分ごと");
                            }
                            return true;
                        case "stop":
                            if (autoEventSystem != null) {
                                autoEventSystem.stopAutoEvents();
                                player.sendMessage(ChatColor.YELLOW + "自動イベントを停止しました");
                            }
                            return true;
                        case "start":
                            if (autoEventSystem != null) {
                                autoEventSystem.startAutoEvents();
                                player.sendMessage(ChatColor.GREEN + "自動イベントを開始しました");
                            }
                            return true;
                    }
                    return true;
                case "rewards":
                    // 報酬リセット
                    if (engagementSystem != null) {
                        engagementSystem.resetRewards();
                        player.sendMessage(ChatColor.GREEN + "参加者数報酬をリセットしました");
                    }
                    return true;
            }
        } else if (command.getName().equalsIgnoreCase("spectate")) {
            // OP権限チェック
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "このコマンドはOP権限が必要です");
                return true;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
                player.setSpectatorTarget(null);
                player.sendMessage(ChatColor.YELLOW + "サバイバルに戻りました");
            } else {
                player.setGameMode(GameMode.SPECTATOR);
                player.setSpectatorTarget(null);
                player.sendMessage(ChatColor.YELLOW + "スペクテイターになりました");
            }
            return true;
        }
        return false;
    }
    
    // イベントポイントをランキングに加算するメソッド
    public void addEventPointsToRanking(UUID playerId, int points, String eventType) {
        // 除外プレイヤーはランキングに加算しない
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && isPlayerExcluded(player)) {
            getLogger().info("除外プレイヤーのイベントポイント加算をスキップ: " + player.getName());
            return;
        }
        
        // イベントポイントを加算
        int currentPoints = playerEventPoints.getOrDefault(playerId, 0);
        playerEventPoints.put(playerId, currentPoints + points);
        
        getLogger().info("イベントポイント加算: " + (player != null ? player.getName() : playerId) + 
                        " +" + points + " (" + eventType + ") 合計: " + (currentPoints + points));
    }
    
    // 多言語メッセージ表示用ヘルパーメソッド
    private void sendMultilingualMessage(Player player, String japanese, String english) {
        if (enableEnglishMessages) {
            player.sendMessage(ChatColor.GRAY + "[" + ChatColor.YELLOW + "EN" + ChatColor.GRAY + "] " + english);
        }
        player.sendMessage(japanese);
    }
    
    private void sendMultilingualActionBar(Player player, String japanese, String english) {
        if (enableEnglishMessages) {
            player.sendActionBar(ChatColor.GRAY + "[" + ChatColor.YELLOW + "EN" + ChatColor.GRAY + "] " + english);
        }
        player.sendActionBar(japanese);
    }
    
    private void broadcastMultilingualMessage(String japanese, String english) {
        if (enableEnglishMessages) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "[" + ChatColor.YELLOW + "EN" + ChatColor.GRAY + "] " + english);
        }
        Bukkit.broadcastMessage(japanese);
    }
}
