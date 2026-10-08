package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps the server properties required for an unprotected cross-play server. */
public final class ServerSettingsGuard {
    private static final String BACKUP_NAME = "server.properties.patrol-backup";
    private static final String TEMP_NAME = "server.properties.patrol.tmp";

    private final PatrolSpectatorPlugin plugin;
    private BukkitTask task;
    private boolean restartNoticeSent;

    public ServerSettingsGuard(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("server_settings_guard.enabled", true)) return;
        long intervalMinutes = Math.max(1,
                plugin.getConfig().getInt("server_settings_guard.interval_minutes", 5));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAndRepair,
                1L, intervalMinutes * 60L * 20L);
        plugin.getLogger().info("[ServerSettings] サーバー設定の監視を開始しました（間隔="
                + intervalMinutes + "分）。");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    void checkAndRepair() {
        if (Bukkit.getServer().getSpawnRadius() != 0) {
            Bukkit.getServer().setSpawnRadius(0);
            plugin.getLogger().warning("[ServerSettings] 初期リス保護を0へ戻し、即時反映しました。");
        }

        Path properties = findServerProperties();
        if (properties == null) {
            plugin.getLogger().warning("[ServerSettings] server.propertiesが見つからないため、設定監視をスキップしました。");
            return;
        }

        try {
            String original = Files.readString(properties, StandardCharsets.ISO_8859_1);
            String updated = updatePropertiesText(original, desiredProperties());
            if (!updated.equals(original)) {
                if (!ensureBackup(properties)) {
                    plugin.getLogger().severe("[ServerSettings] バックアップを作成できないため、server.propertiesを変更しませんでした。");
                    return;
                }
                writeAtomically(properties, updated);
                plugin.getLogger().warning("[ServerSettings] spawn-protection=0 と enforce-secure-profile=false を保存しました。");
            }

            if (Bukkit.getServer().isEnforcingSecureProfiles()) notifyRestartRequired();
            else restartNoticeSent = false;
        } catch (IOException | SecurityException e) {
            plugin.getLogger().severe("[ServerSettings] server.propertiesの確認・更新に失敗しました: "
                    + e.getClass().getSimpleName());
        }
    }

    private Path findServerProperties() {
        Path workingDirectoryFile = Path.of("server.properties").toAbsolutePath().normalize();
        if (Files.isRegularFile(workingDirectoryFile)) return workingDirectoryFile;
        Path worldContainerFile = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize()
                .resolve("server.properties");
        return Files.isRegularFile(worldContainerFile) ? worldContainerFile : null;
    }

    private static Map<String, String> desiredProperties() {
        Map<String, String> desired = new LinkedHashMap<>();
        desired.put("spawn-protection", "0");
        desired.put("enforce-secure-profile", "false");
        return desired;
    }

    private boolean ensureBackup(Path properties) {
        Path backup = properties.resolveSibling(BACKUP_NAME);
        if (Files.exists(backup)) return true;
        try {
            Files.copy(properties, backup, StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("[ServerSettings] server.propertiesの初回バックアップを作成しました。");
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private void writeAtomically(Path properties, String contents) throws IOException {
        Path temporary = properties.resolveSibling(TEMP_NAME);
        Files.writeString(temporary, contents, StandardCharsets.ISO_8859_1);
        try {
            Files.move(temporary, properties, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, properties, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void notifyRestartRequired() {
        if (restartNoticeSent) return;
        restartNoticeSent = true;
        String message = "§e[Patrol] BEチャット用設定を保存しました。反映にはもう一度サーバー再起動が必要です。";
        plugin.getLogger().warning("[ServerSettings] enforce-secure-profile=false の反映にはサーバー再起動が必要です。");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() || player.hasPermission("patrol.admin")) player.sendMessage(message);
        }
    }

    static String updatePropertiesText(String original, Map<String, String> desired) {
        String newline = original.contains("\r\n") ? "\r\n" : "\n";
        boolean trailingNewline = original.endsWith("\n") || original.endsWith("\r");
        String[] lines = original.split("\\R", -1);
        Map<String, Boolean> found = new LinkedHashMap<>();
        desired.keySet().forEach(key -> found.put(key, false));
        StringBuilder result = new StringBuilder(original.length() + 64);
        int effectiveLength = trailingNewline && lines.length > 0 && lines[lines.length - 1].isEmpty()
                ? lines.length - 1 : lines.length;
        for (int i = 0; i < effectiveLength; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            int separator = trimmed.indexOf('=');
            if (!trimmed.startsWith("#") && separator > 0) {
                String key = trimmed.substring(0, separator).trim();
                if (desired.containsKey(key)) {
                    line = key + "=" + desired.get(key);
                    found.put(key, true);
                }
            }
            if (result.length() > 0) result.append(newline);
            result.append(line);
        }
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            if (Boolean.TRUE.equals(found.get(entry.getKey()))) continue;
            if (result.length() > 0) result.append(newline);
            result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        if (trailingNewline) result.append(newline);
        return result.toString();
    }
}
