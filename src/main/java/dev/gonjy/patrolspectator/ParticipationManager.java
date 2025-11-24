package dev.gonjy.patrolspectator;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 参加回数＋ランキング統合管理
 */
public class ParticipationManager implements Listener {
    private final PatrolSpectatorPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public ParticipationManager(PatrolSpectatorPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "participation.yml");
        this.yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        // イベントリスナー登録
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * PlayerStatsStorage を受け取るコンストラクタ（互換性のため）。
     * 現在は使用していませんが、将来的に統合する可能性があります。
     */
    public ParticipationManager(PatrolSpectatorPlugin plugin, PlayerStatsStorage statsStorage) {
        this(plugin); // 既存のコンストラクタに委譲
    }

    private String base(UUID id) {
        return "players." + id;
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (Exception ignored) {
        }
    }

    public int incrementJoinCount(UUID id, String name) {
        String k = base(id) + ".count";
        int c = yaml.getInt(k, 0) + 1;
        yaml.set(k, c);
        yaml.set(base(id) + ".name", name);
        save();
        return c;
    }

    public void addPoints(UUID id, String name, int pts, String reason) {
        if (pts == 0)
            return;
        String k = base(id) + ".score";
        int c = yaml.getInt(k, 0) + pts;
        yaml.set(k, c);
        yaml.set(base(id) + ".name", name);
        yaml.set(base(id) + ".lastReason", reason);
        save();
    }

    public List<Entry> topN(int n) {
        var s = yaml.getConfigurationSection("players");
        if (s == null)
            return Collections.emptyList();
        return s.getKeys(false).stream().map(id -> {
            String b = "players." + id;
            return new Entry(UUID.fromString(id),
                    yaml.getString(b + ".name", id),
                    yaml.getInt(b + ".score", 0),
                    yaml.getInt(b + ".count", 0));
        }).sorted(Comparator.comparingInt(Entry::score).reversed()).limit(n).collect(Collectors.toList());
    }

    public record Entry(UUID id, String name, int score, int count) {
    }

    public void thankOnJoin(Player p) {
        if (!plugin.getConfig().getBoolean("patrol.greetings.enabled", true))
            return;
        int count = incrementJoinCount(p.getUniqueId(), p.getName());
        int pts = plugin.getConfig().getInt("patrol.greetings.pointsOnJoin", 1);
        if (pts > 0)
            addPoints(p.getUniqueId(), p.getName(), pts, "join");

        boolean showCount = plugin.getConfig().getBoolean("patrol.greetings.includeCount", true);
        String mode = plugin.getConfig().getString("patrol.greetings.mode", "title");
        String server = plugin.getConfig().getString("patrol.greetings.serverName", "OtouGame");
        String msg = showCount ? "（" + count + "回目）" : "";

        if ("chat".equalsIgnoreCase(mode))
            p.sendMessage("ようこそ " + server + "！遊んでくれてありがとう " + msg);
        else
            MessageUtils.showTitleLargeSmall(p,
                    MessageUtils.textBold("#A5D6A7", server + " へようこそ！"),
                    MessageUtils.text("#FFFFFF", "遊んでくれてありがとう " + msg));
    }

    /**
     * プレイヤーが観戦された（映った）ことを記録します。
     * <p>
     * 参加回数をインクリメントし、設定に応じてポイントを付与します。
     */
    public void noteParticipation(UUID uuid, String name) {
        if (uuid == null || name == null)
            return;

        // 参加回数をインクリメント
        incrementJoinCount(uuid, name);

        // ポイント付与（設定で有効な場合）
        int pts = plugin.getConfig().getInt("patrol.participation.points", 1);
        if (pts > 0) {
            addPoints(uuid, name, pts, "観戦された");
        }
    }
}
