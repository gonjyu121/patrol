package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Safely disables Paper's bundled spark on low-resource servers. */
final class PaperSparkGuard {
    private static final String BACKUP_NAME = "paper-global.yml.patrol-backup";
    private static final String TEMP_NAME = "paper-global.yml.patrol.tmp";

    private final PatrolSpectatorPlugin plugin;

    PaperSparkGuard(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
    }

    void apply() {
        if (!plugin.getConfig().getBoolean("performance.disableBundledSpark", true)) return;

        Path config = findPaperGlobalConfig();
        if (config == null) {
            plugin.getLogger().info("[Performance] paper-global.ymlが見つからないためSpark設定を変更しません。");
            return;
        }

        try {
            String original = Files.readString(config, StandardCharsets.UTF_8);
            String updated = disableSpark(original);
            if (updated.equals(original)) return;
            if (!ensureBackup(config)) {
                plugin.getLogger().severe("[Performance] バックアップを作成できないためSpark設定を変更しませんでした。");
                return;
            }
            writeAtomically(config, updated);
            plugin.getLogger().warning(
                    "[Performance] Paper内蔵Sparkを無効化しました。反映は次回サーバー起動時です。");
        } catch (IOException | SecurityException e) {
            plugin.getLogger().severe("[Performance] Spark設定の確認・更新に失敗しました: "
                    + e.getClass().getSimpleName());
        }
    }

    private Path findPaperGlobalConfig() {
        Path workingDirectoryFile = Path.of("config", "paper-global.yml").toAbsolutePath().normalize();
        if (Files.isRegularFile(workingDirectoryFile)) return workingDirectoryFile;
        Path worldContainer;
        try {
            worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        } catch (RuntimeException unsupportedEnvironment) {
            // MockBukkitや非標準ホストでは取得できないことがある。設定を推測して書き換えない。
            return null;
        }
        Path parent = worldContainer.getParent();
        if (parent == null) return null;
        Path parentConfig = parent.resolve("config").resolve("paper-global.yml");
        return Files.isRegularFile(parentConfig) ? parentConfig : null;
    }

    private boolean ensureBackup(Path config) {
        Path backup = config.resolveSibling(BACKUP_NAME);
        if (Files.exists(backup)) return true;
        try {
            Files.copy(config, backup, StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("[Performance] paper-global.ymlの初回バックアップを作成しました。");
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private static void writeAtomically(Path config, String contents) throws IOException {
        Path temporary = config.resolveSibling(TEMP_NAME);
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, config, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, config, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String disableSpark(String original) {
        String newline = original.contains("\r\n") ? "\r\n" : "\n";
        boolean trailingNewline = original.endsWith("\n") || original.endsWith("\r");
        String[] sourceLines = original.split("\\R", -1);
        int effectiveLength = trailingNewline && sourceLines.length > 0
                && sourceLines[sourceLines.length - 1].isEmpty() ? sourceLines.length - 1 : sourceLines.length;
        List<String> lines = new ArrayList<>(List.of(sourceLines).subList(0, effectiveLength));

        int sparkLine = findTopLevelSpark(lines);
        if (sparkLine < 0) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) lines.add("");
            lines.add("spark:");
            lines.add("  enabled: false");
        } else {
            int sectionEnd = findSectionEnd(lines, sparkLine + 1);
            int enabledLine = findEnabledLine(lines, sparkLine + 1, sectionEnd);
            if (enabledLine >= 0) {
                String line = lines.get(enabledLine);
                String indent = line.substring(0, line.indexOf('e'));
                lines.set(enabledLine, indent + "enabled: false");
            } else {
                lines.add(sparkLine + 1, "  enabled: false");
            }
        }

        String result = String.join(newline, lines);
        return trailingNewline ? result + newline : result;
    }

    private static int findTopLevelSpark(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(" ") && !line.startsWith("\t") && line.trim().equals("spark:")) return i;
        }
        return -1;
    }

    private static int findSectionEnd(List<String> lines, int start) {
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            if (!line.startsWith(" ") && !line.startsWith("\t")) return i;
        }
        return lines.size();
    }

    private static int findEnabledLine(List<String> lines, int start, int end) {
        for (int i = start; i < end; i++) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.startsWith("#") && trimmed.matches("enabled\\s*:.*")) return i;
        }
        return -1;
    }
}
