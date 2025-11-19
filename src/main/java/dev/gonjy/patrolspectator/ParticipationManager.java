package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 参加回数＋ランキング統合管理
 */
public class ParticipationManager {
    private final PatrolSpectatorPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public ParticipationManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "participation.yml");
        this.yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private String base(UUID id) { return "players." + id; }
    private void save() { try { yaml.save(file); } catch (Exception ignored) {} }

    public int incrementJoinCount(UUID id, String name) {
        String k = base(id) + ".count";
        int c = yaml.getInt(k, 0) + 1;
        yaml.set(k, c);
        yaml.set(base(id) + ".name", name);
        save();
        return c;
    }

    public void addPoints(UUID id, String name, int pts, String reason) {
        if (pts == 0) return;
        String k = base(id) + ".score";
        int c = yaml.getInt(k, 0) + pts;
        yaml.set(k, c);
        yaml.set(base(id) + ".name", name);
        yaml.set(base(id) + ".lastReason", reason);
        save();
    }

    public List<Entry> topN(int n) {
        var s = yaml.getConfigurationSection("players");
        if (s == null) return Collections.emptyList();
        return s.getKeys(false).stream().map(id -> {
            String b = "players." + id;
            return new Entry(UUID.fromString(id),
                    yaml.getString(b + ".name", id),
                    yaml.getInt(b + ".score", 0),
                    yaml.getInt(b + ".count", 0));
        }).sorted(Comparator.comparingInt(Entry::score).reversed()).limit(n).collect(Collectors.toList());
    }

    public record Entry(UUID id, String name, int score, int count) {}

    public void thankOnJoin(Player p) {
        if (!plugin.getConfig().getBoolean("patrol.greetings.enabled", true)) return;
        int count = incrementJoinCount(p.getUniqueId(), p.getName());
        int pts = plugin.getConfig().getInt("patrol.greetings.pointsOnJoin", 1);
        if (pts > 0) addPoints(p.getUniqueId(), p.getName(), pts, "join");

        boolean showCount = plugin.getConfig().getBoolean("patrol.greetings.includeCount", true);
        String mode = plugin.getConfig().getString("patrol.greetings.mode", "title");
        String server = plugin.getConfig().getString("patrol.greetings.serverName", "OtouGame");
        String msg = showCount ? "（" + count + "回目）" : "";

        if ("chat".equalsIgnoreCase(mode))
            p.sendMessage("ようこそ " + server + "！遊んでくれてありがとう " + msg);
        else
            plugin.showTitleLargeSmall(p,
                    plugin.textBold("#A5D6A7", server + " へようこそ！"),
                    plugin.text("#FFFFFF", "遊んでくれてありがとう " + msg));
    }
}
