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
        String falixApiStatus,
        String falixAddress,
        Integer falixPrimaryPort,
        Integer falixGeyserPort,
        String portMatch,
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
        lines.add("§fGeyser Configured Port: §b" + valueOrUnknown(geyserUdpPort));
        lines.add("");
        lines.add("§fFloodgate: " + colorStatus(floodgateStatus));
        lines.add("§fFloodgate Version: §b" + floodgateVersion);
        lines.add("§fBedrock Players: §b" + valueOrUnknown(bedrockPlayers));
        lines.add("");
        lines.add("§6=== Bedrock / Falix Diagnostics ===");
        lines.add("§fFalix API: " + colorStatus(falixApiStatus));
        lines.add("§fFalix Address: §b" + valueOrUnknown(falixAddress));
        lines.add("§fFalix Primary Port: §b" + valueOrUnknown(falixPrimaryPort));
        lines.add("§fFalix Geyser Port: §b" + valueOrUnknown(falixGeyserPort));
        lines.add("§fGeyser Configured Port: §b" + valueOrUnknown(geyserUdpPort));
        lines.add("§fPort Match: " + colorStatus(portMatch));
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

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static String colorStatus(String status) {
        String color = switch (status) {
            case "OK", "RUNNING", "INITIALIZED" -> "§a";
            case "NOT INSTALLED", "NOT CONFIGURED", "DISABLED", "WARNING", "UNKNOWN",
                    "UNAVAILABLE", "AUTH ERROR", "FORBIDDEN", "SERVER NOT FOUND", "RATE LIMITED",
                    "INVALID RESPONSE", "MISMATCH" -> "§e";
            default -> "§b";
        };
        return color + status;
    }
}
