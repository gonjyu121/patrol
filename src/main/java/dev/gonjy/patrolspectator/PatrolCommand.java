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

        if (!sender.isOp()) {
            sender.sendMessage("§c[Patrol] このコマンドを実行する権限がありません (OP専用)。");
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§a/patrol start [dwellSeconds] - 観光巡りをスタート");
            sender.sendMessage("§a/patrol stop                 - 停止");
            sender.sendMessage("§a/patrol back                 - 最後に手動開始した地点と状態に復帰");
            sender.sendMessage("§a/patrol where                - 保存済みの開始地点(復帰地点)を表示");
            sender.sendMessage("§a/patrol tpback               - 保存済みの開始地点に手動でTP");
            sender.sendMessage("§a/patrol sethome <1|2>        - 現在地を常設の帰還地点に登録");
            sender.sendMessage("§a/patrol home <1|2>           - 登録した帰還地点へ移動");
            sender.sendMessage("§a/patrol homes                - 登録した帰還地点を表示");
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
                    sender.sendMessage("§cプレイヤーのみ実行できます。");
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

                // 開始前の状態を保存
                patrolManager.saveManualStartState(p, p.getLocation(), p.getInventory().getContents(), p.getInventory().getArmorContents());
                patrolManager.startPatrol(p, dwell);
                sender.sendMessage("§a[Patrol] パトロール開始 (停留時間=" + dwell + "秒)");
                break;
            }
            case "back": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cプレイヤーのみ実行できます。");
                    return true;
                }
                Player p = (Player) sender;
                if (patrolManager.restoreManualStartState(p)) {
                    sender.sendMessage("§a[Patrol] 最後に手動でstartした場所と状態に復帰しました！");
                } else {
                    sender.sendMessage("§c[Patrol] 保存された手動開始データが見つかりません。先に /patrol start を手動実行してください。");
                }
                break;
            }
            case "stop": {
                boolean returned = patrolManager.stopPatrol();
                if (returned) {
                    sender.sendMessage("§a[Patrol] パトロールを停止し、開始地点へ戻りました。");
                } else {
                    sender.sendMessage("§c[Patrol] パトロールは停止しましたが、開始地点への帰還に失敗しました。復帰情報は保持されています。");
                }
                break;
            }
            case "status": {
                String running = patrolManager.isRunning() ? "実行中" : "停止中";
                sender.sendMessage("§b[Patrol] 状態=" + running + ", 地点数=" + patrolManager.getLocationCount());
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
                    sender.sendMessage("§a[Patrol] ランキングを手動で表示しました。");
                } else {
                    sender.sendMessage("§c[Patrol] パトロールが起動していません。先に /patrol start を実行してください。");
                }
                break;
            }
            case "reset_survival": {
                if (!sender.isOp()) {
                    sender.sendMessage("§c権限がありません。");
                    return true;
                }
                plugin.getStatsStorage().resetAllContinuousSurvivalTime();
                sender.sendMessage("§a[Patrol] 全プレイヤーの連続生存時間をリセットしました。");
                break;
            }
            case "backup": {
                if (!sender.isOp()) {
                    sender.sendMessage("§c権限がありません。");
                    return true;
                }
                sender.sendMessage("§a[Patrol] バックアップを開始しました。Discordを確認してください。");
                plugin.backupStats();
                break;
            }
            case "reload": {
                if (!sender.isOp()) {
                    sender.sendMessage("§c権限がありません。");
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage("§a[Patrol] 設定とレガシーデータをリロードしました。");
                break;
            }
            case "spawn": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cプレイヤーのみ実行できます。");
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
                    sender.sendMessage("§cプレイヤーのみ実行できます。");
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
                    if (patrolManager.teleportSafely(p, loc, "保存済みの開始地点")) {
                        sender.sendMessage(String.format(
                                "§a[Patrol] 保存済みの戻り地点 §f%s §a(%.1f, %.1f, %.1f) §aにTPしました！",
                                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
                    } else {
                        sender.sendMessage("§c[Patrol] 保存済みの開始地点へのTPに失敗しました。ワールドの状態を確認してください。");
                    }
                }
                break;
            }
            case "sethome": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cプレイヤーのみ実行できます。");
                    return true;
                }
                Integer slot = parseHomeSlot(args);
                if (slot == null) {
                    sender.sendMessage("§c使い方: /patrol sethome <1|2>");
                    return true;
                }
                if (patrolManager.isRunning()) {
                    sender.sendMessage("§c[Patrol] パトロール中のカメラ位置は登録できません。先に /patrol stop を実行してください。");
                    return true;
                }
                Player player = (Player) sender;
                if (patrolManager.saveHome(player, slot)) {
                    org.bukkit.Location home = player.getLocation();
                    sender.sendMessage(String.format(
                            "§a[Patrol] 帰還地点%dを登録しました: §f%s §7(%.1f, %.1f, %.1f)",
                            slot, home.getWorld().getName(), home.getX(), home.getY(), home.getZ()));
                } else {
                    sender.sendMessage("§c[Patrol] 帰還地点の保存に失敗しました。サーバーログを確認してください。");
                }
                break;
            }
            case "home": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cプレイヤーのみ実行できます。");
                    return true;
                }
                Integer slot = parseHomeSlot(args);
                if (slot == null) {
                    sender.sendMessage("§c使い方: /patrol home <1|2>");
                    return true;
                }
                Player player = (Player) sender;
                org.bukkit.Location home = patrolManager.getHome(player, slot);
                if (home == null) {
                    sender.sendMessage("§c[Patrol] 帰還地点" + slot + "は未登録か、保存先ワールドが読み込まれていません。");
                    return true;
                }
                if (patrolManager.teleportHome(player, slot)) {
                    sender.sendMessage("§a[Patrol] 帰還地点" + slot + "へ移動しました。");
                } else {
                    sender.sendMessage("§c[Patrol] 帰還地点" + slot + "への移動に失敗しました。");
                }
                break;
            }
            case "homes": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cプレイヤーのみ実行できます。");
                    return true;
                }
                Player player = (Player) sender;
                sender.sendMessage("§6===== 登録済み帰還地点 =====");
                for (int slot = PatrolHomeStorage.MIN_SLOT; slot <= PatrolHomeStorage.MAX_SLOT; slot++) {
                    org.bukkit.Location home = patrolManager.getHome(player, slot);
                    if (home == null) {
                        sender.sendMessage("§e" + slot + ": §7未登録");
                    } else {
                        sender.sendMessage(String.format("§e%d: §f%s §7(%.1f, %.1f, %.1f)",
                                slot, home.getWorld().getName(), home.getX(), home.getY(), home.getZ()));
                    }
                }
                break;
            }
            case "travel": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cプレイヤーのみ実行できます。");
                    return true;
                }
                Player p = (Player) sender;
                travelToFarVillage(p);
                break;
            }
            default:
                sender.sendMessage("§c不明なサブコマンドです。/patrol help を確認してください。");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp()) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> sub = Arrays.asList("start", "stop", "back", "where", "tpback", "sethome", "home", "homes", "travel", "status", "rank", "spawn", "reset_survival", "backup", "reload");
            List<String> ret = new ArrayList<>();
            for (String s : sub) {
                if (s.startsWith(args[0].toLowerCase())) {
                    ret.add(s);
                }
            }
            return ret;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("sethome") || args[0].equalsIgnoreCase("home"))) {
            return Arrays.asList("1", "2").stream()
                    .filter(slot -> slot.startsWith(args[1]))
                    .toList();
        }
        return Collections.emptyList();
    }

    private Integer parseHomeSlot(String[] args) {
        if (args.length < 2) {
            return null;
        }
        try {
            int slot = Integer.parseInt(args[1]);
            return PatrolHomeStorage.isValidSlot(slot) ? slot : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void travelToFarVillage(Player player) {
        org.bukkit.World world = player.getWorld();
        if (world.getEnvironment() != org.bukkit.World.Environment.NORMAL) {
            player.sendMessage("§c[Patrol] このコマンドはオーバーワールドでのみ実行できます。");
            return;
        }

        player.sendMessage("§a[Patrol] 遠くの村を探索中...（数秒かかる場合があります）");

        org.bukkit.Location spawn = world.getSpawnLocation();
        // 現在地を基準にする（何度叩いても現在地からさらに遠くへ飛べる）
        org.bukkit.Location current = player.getLocation();

        java.util.Random rand = new java.util.Random();
        double angle = rand.nextDouble() * 2 * Math.PI;
        // 現在地から3000～8000ブロック離れた座標
        double distance = 3000 + rand.nextInt(5000);
        int targetX = (int) (current.getX() + Math.cos(angle) * distance);
        int targetZ = (int) (current.getZ() + Math.sin(angle) * distance);

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
            player.sendMessage("§a[Patrol] 遠くの村にテレポートしました！");
        } else {
            int y = world.getHighestBlockYAt(targetX, targetZ);
            org.bukkit.Location fallbackLoc = new org.bukkit.Location(world, targetX, y + 1, targetZ);

            if (patrolManager.isRunning()) {
                patrolManager.stopPatrol();
            }

            player.teleport(fallbackLoc);
            player.sendMessage("§e[Patrol] 近くに村が見つからなかったため、ランダムな地表にテレポートしました。");
        }
    }
}
