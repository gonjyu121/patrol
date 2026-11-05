package dev.gonjy.patrolspectator.dungeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class DungeonCommand implements CommandExecutor, TabCompleter {

    private final DungeonManager manager;

    public DungeonCommand(DungeonManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            send(sender, "権限がありません。", NamedTextColor.RED);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status": {
                int planned = manager.getFloorCount();
                send(sender, "[死の迷宮] 生成状況（座標非表示）", NamedTextColor.GOLD);
                send(sender, "設定: " + (manager.isEnabled() ? "有効" : "無効")
                        + " / 生成予定: B1〜B" + planned, NamedTextColor.YELLOW);
                if (manager.getCenter() == null || manager.getCenter().getWorld() == null) {
                    send(sender, "中心ワールドを読み込めず、構造を確認できません。", NamedTextColor.RED);
                    break;
                }
                boolean building = manager.getPlugin() != null && manager.getPlugin().getDungeonBuilder() != null
                        && manager.getPlugin().getDungeonBuilder().isBuilding();
                DungeonManager.FloorInspection inspection = manager.inspectBuiltFloors();
                send(sender, "生成処理: " + (building ? "進行中" : "停止中")
                        + " / 完了記録: " + manager.getRecordedBuiltCount() + "階", NamedTextColor.YELLOW);
                send(sender, "構造の抜き取り確認: " + inspection.verifiedCount()
                        + "/" + planned + "階" + (inspection.deepestVerified() > 0
                        ? "（最深 B" + inspection.deepestVerified() + "）" : ""), NamedTextColor.YELLOW);
                if (!building && manager.isBuilt() && inspection.verifiedCount() == planned) {
                    send(sender, "記録と各階の主要構造が一致しています（全ブロックの保証ではありません）。", NamedTextColor.GREEN);
                } else if (!building) {
                    send(sender, "未完成または要確認です。起動ログと安全スキャン結果を確認してください。", NamedTextColor.RED);
                }
                break;
            }
            case "setcenter":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("プレイヤーのみ実行可能です。");
                    return true;
                }
                Player p = (Player) sender;
                if (DungeonManager.isTooCloseToSpawn(p.getLocation().getBlockX(), p.getLocation().getBlockZ(),
                        p.getWorld().getSpawnLocation().getBlockX(), p.getWorld().getSpawnLocation().getBlockZ())) {
                    send(sender, "初期リスポーン地点に近すぎます。十分離れた場所で設定してください。", NamedTextColor.RED);
                    return true;
                }
                manager.setCenter(p.getLocation());
                send(sender, "迷宮の中心を現在地に設定しました。", NamedTextColor.GREEN);
                if (manager.getPlugin() != null && manager.getPlugin().getPatrolManager() != null) {
                    manager.getPlugin().getPatrolManager().addDungeonLocations(manager);
                }
                break;

            case "enable":
                manager.setEnabled(true);
                send(sender, "迷宮を有効化しました。", NamedTextColor.GREEN);
                if (manager.getPlugin() != null && manager.getPlugin().getPatrolManager() != null) {
                    manager.getPlugin().getPatrolManager().addDungeonLocations(manager);
                }
                break;

            case "disable":
                manager.setEnabled(false);
                send(sender, "迷宮を無効化しました。", NamedTextColor.YELLOW);
                break;

            case "scan":
                StringBuilder report = new StringBuilder();
                send(sender, "安全スキャンを開始中...", NamedTextColor.YELLOW);
                if (manager.scanForSafety(report)) {
                    send(sender, "安全スキャン完了: 既存の建造物は見つかりませんでした。生成可能です。", NamedTextColor.GREEN);
                } else {
                    sender.sendMessage(report.toString());
                    send(sender, "警告: 既存のブロックが検出されました。build を実行する前に確認してください。", NamedTextColor.RED);
                }
                break;

            case "build": {
                if (args.length < 2 || (!args[1].equalsIgnoreCase("floors") && !args[1].equalsIgnoreCase("b1"))) {
                    send(sender, "使用法: /dungeon build floors", NamedTextColor.RED);
                    return true;
                }
                if (!manager.isEnabled()) {
                    send(sender, "エラー: まず迷宮を有効化し、中心座標を設定してください。(/dungeon enable, /dungeon setcenter)", NamedTextColor.RED);
                    return true;
                }

                StringBuilder buildReport = new StringBuilder();
                if (!manager.scanForSafety(buildReport)) {
                    sender.sendMessage(buildReport.toString());
                    send(sender, "エラー: 既存の建造物が検出されたため、生成を中止しました。", NamedTextColor.RED);
                    return true;
                }

                send(sender, "B1〜B" + manager.getFloorCount()
                        + " の生成を開始します... (サーバー負荷軽減のため時間がかかります)", NamedTextColor.YELLOW);
                dev.gonjy.patrolspectator.PatrolSpectatorPlugin buildPlugin = manager.getPlugin();
                if (buildPlugin != null && buildPlugin.getDungeonBuilder() != null) {
                    buildPlugin.getDungeonBuilder().buildB1();
                } else {
                    send(sender, "システムエラー: DungeonBuilderが初期化されていません。", NamedTextColor.RED);
                }
                break;
            }

            case "reset": {
                if (!manager.isEnabled()) {
                    send(sender, "エラー: 迷宮が有効化されていません。", NamedTextColor.RED);
                    return true;
                }
                dev.gonjy.patrolspectator.PatrolSpectatorPlugin resetPlugin = manager.getPlugin();
                if (resetPlugin == null || resetPlugin.getDungeonBuilder() == null) {
                    send(sender, "システムエラー: DungeonBuilderが初期化されていません。", NamedTextColor.RED);
                    return true;
                }
                manager.setBuilt(false);
                if (resetPlugin.getDungeonBuilder().buildB1()) {
                    send(sender, "迷宮の安全な再構築を開始しました。", NamedTextColor.YELLOW);
                } else {
                    send(sender, "迷宮は既に再構築中です。", NamedTextColor.RED);
                }
                break;
            }

            case "tp":
            case "entrance": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("プレイヤーのみ実行可能です。");
                    return true;
                }
                Player tpPlayer = (Player) sender;
                org.bukkit.Location tpCenter = manager.getCenter();
                if (tpCenter == null) {
                    send(sender, "エラー: 迷宮の中心座標が設定されていません。(/dungeon setcenter)", NamedTextColor.RED);
                    return true;
                }
                dev.gonjy.patrolspectator.PatrolSpectatorPlugin tpPlugin = manager.getPlugin();
                if (tpPlugin != null && tpPlugin.getDungeonBuilder() != null) {
                    int startX = tpCenter.getBlockX() - 30;
                    int startZ = tpCenter.getBlockZ() - 30;
                    int baseY = tpCenter.getBlockY();
                    tpPlugin.getDungeonBuilder().buildEntranceGate(tpCenter.getWorld(), startX, baseY, startZ);
                }
                org.bukkit.Location entranceLoc = new org.bukkit.Location(tpCenter.getWorld(), tpCenter.getBlockX() + 0.5, tpCenter.getBlockY() + 1.0, tpCenter.getBlockZ() - 34.5, 0f, 0f);
                tpPlayer.teleport(entranceLoc);
                send(sender, "死の迷宮の正面入口にテレポートしました！", NamedTextColor.GREEN);
                break;
            }

            case "build_entrance": {
                org.bukkit.Location beCenter = manager.getCenter();
                if (beCenter == null) {
                    send(sender, "エラー: 迷宮の中心座標が設定されていません。(/dungeon setcenter)", NamedTextColor.RED);
                    return true;
                }
                dev.gonjy.patrolspectator.PatrolSpectatorPlugin bePlugin = manager.getPlugin();
                if (bePlugin != null && bePlugin.getDungeonBuilder() != null) {
                    int startX = beCenter.getBlockX() - 30;
                    int startZ = beCenter.getBlockZ() - 30;
                    int baseY = beCenter.getBlockY();
                    bePlugin.getDungeonBuilder().buildEntranceGate(beCenter.getWorld(), startX, baseY, startZ);
                    send(sender, "迷宮の正面入口門（アーチ・看板・壁くり抜き）を生成しました。", NamedTextColor.GREEN);
                }
                break;
            }

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "========== [ Dungeon Admin Help ] ==========", NamedTextColor.GOLD);
        sendHelpLine(sender, "/dungeon status", "予定階数と生成済み構造を確認（座標非表示）");
        sendHelpLine(sender, "/dungeon tp", "迷宮の正面入口へTP（門自動生成）");
        sendHelpLine(sender, "/dungeon build_entrance", "正面入口門をその場に生成");
        sendHelpLine(sender, "/dungeon setcenter", "現在地を迷宮の中心に設定");
        sendHelpLine(sender, "/dungeon scan", "範囲内の建造物チェック");
        sendHelpLine(sender, "/dungeon build floors", "設置可能な地下階層を分割生成");
        sendHelpLine(sender, "/dungeon reset", "攻略状況・敵・宝箱を初期化して再構築");
        sendHelpLine(sender, "/dungeon enable", "迷宮の有効化");
        sendHelpLine(sender, "/dungeon disable", "迷宮の無効化");
    }

    private void send(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
    }

    private void sendHelpLine(CommandSender sender, String command, String description) {
        sender.sendMessage(Component.text(command, NamedTextColor.GREEN)
                .append(Component.text(" - " + description, NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("status", "tp", "entrance", "build_entrance", "setcenter", "enable", "disable", "scan", "build", "reset")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("build")) {
            return Arrays.asList("floors", "b1");
        }
        return new ArrayList<>();
    }
}
