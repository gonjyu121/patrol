package dev.gonjy.patrolspectator.dungeon;

import org.bukkit.ChatColor;
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
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setcenter":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("プレイヤーのみ実行可能です。");
                    return true;
                }
                Player p = (Player) sender;
                manager.setCenter(p.getLocation());
                sender.sendMessage(ChatColor.GREEN + "迷宮の中心を現在地に設定しました。");
                if (manager.getPlugin() != null && manager.getPlugin().getPatrolManager() != null) {
                    manager.getPlugin().getPatrolManager().addDungeonLocations(manager);
                }
                break;

            case "enable":
                manager.setEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "迷宮を有効化しました。");
                if (manager.getPlugin() != null && manager.getPlugin().getPatrolManager() != null) {
                    manager.getPlugin().getPatrolManager().addDungeonLocations(manager);
                }
                break;

            case "disable":
                manager.setEnabled(false);
                sender.sendMessage(ChatColor.YELLOW + "迷宮を無効化しました。");
                break;

            case "scan":
                StringBuilder report = new StringBuilder();
                sender.sendMessage(ChatColor.YELLOW + "安全スキャンを開始中...");
                if (manager.scanForSafety(report)) {
                    sender.sendMessage(ChatColor.GREEN + "安全スキャン完了: 既存の建造物は見つかりませんでした。生成可能です。");
                } else {
                    sender.sendMessage(report.toString());
                    sender.sendMessage(ChatColor.RED + "警告: 既存のブロックが検出されました。build を実行する前に確認してください。");
                }
                break;

            case "build":
                if (args.length < 2 || !args[1].equalsIgnoreCase("b1")) {
                    sender.sendMessage(ChatColor.RED + "使用法: /dungeon build b1");
                    return true;
                }
                if (!manager.isEnabled()) {
                    sender.sendMessage(
                            ChatColor.RED + "エラー: まず迷宮を有効化し、中心座標を設定してください。(/dungeon enable, /dungeon setcenter)");
                    return true;
                }

                StringBuilder buildReport = new StringBuilder();
                if (!manager.scanForSafety(buildReport)) {
                    sender.sendMessage(buildReport.toString());
                    sender.sendMessage(ChatColor.RED + "エラー: 既存の建造物が検出されたため、生成を中止しました。");
                    return true;
                }

                sender.sendMessage(ChatColor.YELLOW + "B1階層の生成を開始します... (サーバー負荷軽減のため時間がかかります)");
                // メインクラスのインスタンスを取得してBuilderを呼ぶ (DungeonManagerからPluginインスタンスを参照するか、別途渡す)
                // 暫定的にpluginインスタンスをDungeonManager経由で取得
                dev.gonjy.patrolspectator.PatrolSpectatorPlugin plugin = manager.getPlugin();
                if (plugin != null && plugin.getDungeonBuilder() != null) {
                    plugin.getDungeonBuilder().buildB1();
                } else {
                    sender.sendMessage(ChatColor.RED + "システムエラー: DungeonBuilderが初期化されていません。");
                }
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== [ Dungeon Admin Help ] ==========");
        sender.sendMessage("§a/dungeon setcenter §7- 現在地を迷宮の中心に設定");
        sender.sendMessage("§a/dungeon scan      §7- 範囲内の建造物チェック");
        sender.sendMessage("§a/dungeon build b1  §7- B1階層の生成 (分割設置)");
        sender.sendMessage("§a/dungeon enable    §7- 迷宮の有効化");
        sender.sendMessage("§a/dungeon disable   §7- 迷宮の無効化");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("setcenter", "enable", "disable", "scan", "build", "stats")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("build")) {
            return Arrays.asList("b1");
        }
        return new ArrayList<>();
    }
}
