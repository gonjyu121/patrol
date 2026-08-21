package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final BountyManager bountyManager;

    public BountyCommand(BountyManager bountyManager) {
        this.bountyManager = bountyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                handleList(sender);
                break;
            case "add":
                handleAdd(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== 賞金首システム =====");
        sender.sendMessage(ChatColor.YELLOW + "/bounty list " + ChatColor.WHITE + "- 賞金首リストを表示");
        if (sender.hasPermission("patrol.bounty.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/bounty add <player> <amount> " + ChatColor.WHITE + "- 賞金をかける(OP)");
        }
    }

    private void handleList(CommandSender sender) {
        if (bountyManager.getActiveBounties().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "現在、賞金首はいません。平和です。");
            return;
        }

        sender.sendMessage(ChatColor.RED + "===== 賞金首リスト =====");
        for (BountyData data : bountyManager.getActiveBounties().values()) {
            sender.sendMessage(ChatColor.GOLD + data.getTargetName() +
                    ChatColor.WHITE + " - 賞金: " + ChatColor.AQUA + (int) data.getAmount() + " ダイヤ" +
                    ChatColor.GRAY + " (依頼者: " + data.getIssuer() + ")");
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("patrol.bounty.admin")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "使い方: /bounty add <プレイヤー名> <金額>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません。");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "金額は正の数値を指定してください。");
            return;
        }

        bountyManager.addBounty(target, amount, sender.getName());
        sender.sendMessage(ChatColor.GREEN + "賞金首を追加しました。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("list");
            if (sender.hasPermission("patrol.bounty.admin")) {
                completions.add("add");
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
