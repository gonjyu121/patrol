package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /patrol コマンドの処理を担当するクラス。
 */
public class PatrolCommand implements CommandExecutor, TabCompleter {

    private final PatrolSpectatorPlugin plugin;
    private final PatrolManager patrolManager;
    private final RankingDisplaySystem rankingDisplaySystem;

    public PatrolCommand(PatrolSpectatorPlugin plugin, PatrolManager patrolManager,
            RankingDisplaySystem rankingDisplaySystem) {
        this.plugin = plugin;
        this.patrolManager = patrolManager;
        this.rankingDisplaySystem = rankingDisplaySystem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"patrol".equalsIgnoreCase(command.getName()))
            return false;

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§a/patrol start [dwellSeconds] - 観光巡りをスタート");
            sender.sendMessage("§a/patrol stop                 - 停止");
            sender.sendMessage("§a/patrol where                - 保存済みの開始地点(復帰地点)を表示");
            sender.sendMessage("§a/patrol tpback               - 保存済みの開始地点に手動でTP");
            sender.sendMessage("§a/patrol spawn                - 初期スポーン地点へ戻り、開始地点をリセット");
            sender.sendMessage("§a/patrol travel               - 初期リスから遠く離れた村(またはランダム地点)へTP");
            sender.sendMessage("§a/patrol status               - 状態表示");
            sender.sendMessage("§a/patrol rank                 - ランキング表示");
            if (sender.isOp()) {
                sender.sendMessage("§a/patrol reset_survival       - 連続生存時間リセット(OP)");
                sender.sendMessage("§a/patrol backup               - ランキングのDiscordバックアップ(OP)");
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                Player p = (Player) sender;

                int dwell = plugin.getTourConf().dwellSeconds;
                if (args.length >= 2) {
                    try {
                        dwell = Math.max(3, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }

                patrolManager.startPatrol(p, dwell);
                sender.sendMessage("§a[Patrol] start (dwell=" + dwell + "s)");
                break;
            }
            case "stop": {
                patrolManager.stopPatrol();
                sender.sendMessage("§e[Patrol] stop");
                // 停止後の複帰地点を案内（及び自分でTPできるTPBACKコマンドを案内）
                // 注意: stopPatrol()内部で自動TP済みなので、ここでは案内のみ
                break;
            }
            case "status": {
                String running = patrolManager.isRunning() ? "RUNNING" : "IDLE";
                sender.sendMessage("§b[Patrol] status=" + running + ", locations=" + patrolManager.getLocationCount());
                // 状態表示時に保存地点も表示
                org.bukkit.Location savedLoc = patrolManager.getStartLocation();
                if (savedLoc != null) {
                    sender.sendMessage(String.format("§b[Patrol] 保存地点: %s (%.1f, %.1f, %.1f)",
                            savedLoc.getWorld().getName(), savedLoc.getX(), savedLoc.getY(), savedLoc.getZ()));
                } else {
                    sender.sendMessage("§b[Patrol] 保存地点: 未設定");
                }
                break;
            }
            case "rank": {
                if (patrolManager.isRunning()) {
                    rankingDisplaySystem.displayRankings();
                    sender.sendMessage("§a[Patrol] Ranking display triggered manually.");
                } else {
                    sender.sendMessage("§c[Patrol] Patrol is not running. Start patrol first.");
                }
                break;
            }
            case "reset_survival": {
                if (!sender.isOp()) {
                    sender.sendMessage("§cPermission denied.");
                    return true;
                }
                plugin.getStatsStorage().resetAllContinuousSurvivalTime();
                sender.sendMessage("§a[Patrol] All continuous survival times have been reset.");
                break;
            }
            case "backup": {
                if (!sender.isOp()) {
                    sender.sendMessage("§cPermission denied.");
                    return true;
                }
                sender.sendMessage("§a[Patrol] Stats backup triggered. Check Discord shortly.");
                plugin.backupStats();
                break;
            }
            case "reload": {
                if (!sender.isOp()) {
                    sender.sendMessage("§cPermission denied.");
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage("§a[Patrol] Configuration and legacy stats reloaded.");
                break;
            }
            case "spawn": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                Player p = (Player) sender;
                org.bukkit.World overworld = Bukkit.getWorlds().get(0);
                org.bukkit.Location spawnLoc = overworld.getSpawnLocation();
                
                // パトロール中なら停止
                if (patrolManager.isRunning()) {
                    patrolManager.stopPatrol();
                }
                
                patrolManager.setStartLocation(spawnLoc);
                p.teleport(spawnLoc);
                sender.sendMessage("§a[Patrol] オーバーワールドの初期スポーン地点へ戻りました。開始地点をここにリセットしました。");
                break;
            }
            case "where": {
                org.bukkit.Location loc = patrolManager.getStartLocation();
                if (loc == null) {
                    sender.sendMessage("§c[Patrol] 保存済みの戻り地点がありません。パトロールを開始してください。");
                } else {
                    sender.sendMessage(String.format(
                            "§a[Patrol] 保存済みの戻り地点: §f%s §7(%.1f, %.1f, %.1f)",
                            loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
                    sender.sendMessage("§7返るには: §e/patrol tpback §7を実行してください");
                }
                break;
            }
            case "tpback": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                Player p = (Player) sender;
                org.bukkit.Location loc = patrolManager.getStartLocation();
                if (loc == null) {
                    sender.sendMessage("§c[Patrol] 保存済みの戻り地点がありません。先に /patrol start を実行してください。");
                } else {
                    // パトロール中なら先に停止
                    if (patrolManager.isRunning()) {
                        patrolManager.stopPatrol();
                        sender.sendMessage("§e[Patrol] パトロールを停止してからTPします...");
                    }
                    p.teleport(loc);
                    sender.sendMessage(String.format(
                            "§a[Patrol] 保存済みの戻り地点 §f%s §a(%.1f, %.1f, %.1f) §aにTPしました！",
                            loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
                }
                break;
            }
            case "travel": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Player only.");
                    return true;
                }
                Player p = (Player) sender;
                travelToFarVillage(p);
                break;
            }
            default:
                sender.sendMessage("Unknown subcommand. /patrol help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>(Arrays.asList("start", "stop", "where", "tpback", "travel", "status", "rank", "spawn"));
            if (sender.isOp()) {
                sub.add("reset_survival");
                sub.add("backup");
                sub.add("reload");
            }
            List<String> ret = new ArrayList<>();
            for (String s : sub) {
                if (s.startsWith(args[0].toLowerCase()))
                    ret.add(s);
            }
            return ret;
        }
        return Collections.emptyList();
    }

    private void travelToFarVillage(Player player) {
        org.bukkit.World world = player.getWorld();
        if (world.getEnvironment() != org.bukkit.World.Environment.NORMAL) {
            player.sendMessage("§c[Patrol] このコマンドはオーバーワールドでのみ実行できます。");
            return;
        }

        player.sendMessage("§a[Patrol] 遠くの村を探索中...（数秒かかる場合があります）");

        org.bukkit.Location spawn = world.getSpawnLocation();
        
        java.util.Random rand = new java.util.Random();
        double angle = rand.nextDouble() * 2 * Math.PI;
        // 3000～8000ブロック離れた座標
        double distance = 3000 + rand.nextInt(5000);
        int targetX = (int) (spawn.getX() + Math.cos(angle) * distance);
        int targetZ = (int) (spawn.getZ() + Math.sin(angle) * distance);
        
        org.bukkit.Location searchOrigin = new org.bukkit.Location(world, targetX, 64, targetZ);
        org.bukkit.Location targetLoc = null;

        try {
            org.bukkit.generator.structure.Structure plainsVillage = org.bukkit.Registry.STRUCTURE.get(org.bukkit.NamespacedKey.minecraft("village_plains"));
            if (plainsVillage != null) {
                org.bukkit.util.StructureSearchResult result = world.locateNearestStructure(searchOrigin, plainsVillage, 150, false);
                if (result != null && result.getLocation() != null) {
                    targetLoc = result.getLocation();
                }
            }
            
            if (targetLoc == null) {
                org.bukkit.generator.structure.Structure desertVillage = org.bukkit.Registry.STRUCTURE.get(org.bukkit.NamespacedKey.minecraft("village_desert"));
                if (desertVillage != null) {
                    org.bukkit.util.StructureSearchResult result = world.locateNearestStructure(searchOrigin, desertVillage, 150, false);
                    if (result != null && result.getLocation() != null) {
                        targetLoc = result.getLocation();
                    }
                }
            }
            
            if (targetLoc == null) {
                org.bukkit.generator.structure.Structure taigaVillage = org.bukkit.Registry.STRUCTURE.get(org.bukkit.NamespacedKey.minecraft("village_taiga"));
                if (taigaVillage != null) {
                    org.bukkit.util.StructureSearchResult result = world.locateNearestStructure(searchOrigin, taigaVillage, 150, false);
                    if (result != null && result.getLocation() != null) {
                        targetLoc = result.getLocation();
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Structure API locate failed: " + t.getMessage());
        }

        if (targetLoc != null) {
            int y = world.getHighestBlockYAt(targetLoc.getBlockX(), targetLoc.getBlockZ());
            targetLoc.setY(y + 1);
            
            if (patrolManager.isRunning()) {
                patrolManager.stopPatrol();
            }
            
            player.teleport(targetLoc);
            player.sendMessage(String.format("§a[Patrol] 初期リスから %.1f ブロック離れた村にテレポートしました！ (X: %d, Z: %d)", 
                    targetLoc.distance(spawn), targetLoc.getBlockX(), targetLoc.getBlockZ()));
        } else {
            int y = world.getHighestBlockYAt(targetX, targetZ);
            org.bukkit.Location fallbackLoc = new org.bukkit.Location(world, targetX, y + 1, targetZ);
            
            if (patrolManager.isRunning()) {
                patrolManager.stopPatrol();
            }
            
            player.teleport(fallbackLoc);
            player.sendMessage(String.format("§e[Patrol] 近くに村が見つからなかったため、初期リスから %.1f ブロック離れたランダムな地表にテレポートしました。 (X: %d, Z: %d)", 
                    fallbackLoc.distance(spawn), targetX, targetZ));
        }
    }
}
