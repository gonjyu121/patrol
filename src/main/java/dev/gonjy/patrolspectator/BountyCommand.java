package dev.gonjy.patrolspectator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        sender.sendMessage(Component.text("===== 賞金首システム =====", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/bounty list ", NamedTextColor.YELLOW)
                .append(Component.text("- 賞金首リストを表示", NamedTextColor.WHITE)));
        if (sender.hasPermission("patrol.bounty.admin")) {
            sender.sendMessage(Component.text("/bounty add <player> <amount> ", NamedTextColor.YELLOW)
                    .append(Component.text("- 賞金をかける(OP)", NamedTextColor.WHITE)));
        }
    }

    private void handleList(CommandSender sender) {
        if (bountyManager.getActiveBounties().isEmpty()) {
            sender.sendMessage(Component.text("現在、賞金首はいません。平和です。", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("===== 賞金首リスト =====", NamedTextColor.RED));
        for (BountyData data : bountyManager.getActiveBounties().values()) {
            sender.sendMessage(Component.text(data.getTargetName(), NamedTextColor.GOLD)
                    .append(Component.text(" - 賞金: ", NamedTextColor.WHITE))
                    .append(Component.text((int) data.getAmount() + " ダイヤ", NamedTextColor.AQUA))
                    .append(Component.text(" (依頼者: " + data.getIssuer() + ")", NamedTextColor.GRAY)));
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("patrol.bounty.admin")) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("使い方: /bounty add <プレイヤー名> <金額>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("プレイヤーが見つかりません。", NamedTextColor.RED));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("金額は正の数値を指定してください。", NamedTextColor.RED));
            return;
        }

        bountyManager.addBounty(target, amount, sender.getName());
        sender.sendMessage(Component.text("賞金首を追加しました。", NamedTextColor.GREEN));
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
