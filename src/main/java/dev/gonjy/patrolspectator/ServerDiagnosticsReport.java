package dev.gonjy.patrolspectator;

import java.util.ArrayList;
import java.util.List;

record ServerDiagnosticsReport(
        String serverStatus,
        int paperPort,
        int onlinePlayers,
        List<String> worlds,
        String patrolVersion,
        boolean patrolRunning,
        String camera,
        boolean coreInitialized,
        String geyserStatus,
        String geyserVersion,
        Integer geyserUdpPort,
        String floodgateStatus,
        String floodgateVersion,
        Integer bedrockPlayers,
        String overall) {

    List<String> displayLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§6=== Patrol Server Diagnostics ===");
        lines.add("");
        lines.add("§fServer: " + colorStatus(serverStatus));
        lines.add("§fPaper Port: §b" + paperPort);
        lines.add("§fOnline Players: §b" + onlinePlayers);
        lines.add("§fWorlds: §b" + (worlds.isEmpty() ? "NONE" : String.join(", ", worlds)));
        lines.add("");
        lines.add("§fPatrolSpectatorPlugin: " + colorStatus("OK"));
        lines.add("§fVersion: §b" + patrolVersion);
        lines.add("§fPatrol: " + colorStatus(patrolRunning ? "RUNNING" : "STOPPED"));
        lines.add("§fCamera: §b" + camera);
        lines.add("§fCore Systems: " + colorStatus(coreInitialized ? "INITIALIZED" : "WARNING"));
        lines.add("");
        lines.add("§fGeyser: " + colorStatus(geyserStatus));
        lines.add("§fGeyser Version: §b" + geyserVersion);
        lines.add("§fGeyser UDP Port: §b" + valueOrUnknown(geyserUdpPort));
        lines.add("");
        lines.add("§fFloodgate: " + colorStatus(floodgateStatus));
        lines.add("§fFloodgate Version: §b" + floodgateVersion);
        lines.add("§fBedrock Players: §b" + valueOrUnknown(bedrockPlayers));
        lines.add("");
        lines.add("§fExternal Connection Test: §7NOT AVAILABLE");
        lines.add("§fOverall: " + colorStatus(overall));
        if ("OK".equals(overall)) {
            lines.add("");
            lines.add("§aサーバー側の診断項目は正常です。");
            lines.add("§eBedrockから接続できない場合は、トラブルシューティング候補としてMinecraft for Windowsを完全終了して再起動してください。");
        }
        return lines;
    }

    private static String valueOrUnknown(Integer value) {
        return value == null ? "UNKNOWN" : Integer.toString(value);
    }

    private static String colorStatus(String status) {
        String color = switch (status) {
            case "OK", "RUNNING", "INITIALIZED" -> "§a";
            case "NOT INSTALLED", "DISABLED", "WARNING", "UNKNOWN" -> "§e";
            default -> "§b";
        };
        return color + status;
    }
}
