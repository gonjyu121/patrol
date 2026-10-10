package dev.gonjy.patrolspectator;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stores recent participant locations separately from the permanent tour list. */
final class RecentPlayerLocationStore {
    static final String ID_PREFIX = "recent_player_";
    static final String LOCATION_MESSAGE = "§fさんが最後にいた場所";
    static final String RETURN_MESSAGE = "§aまたの参加をお待ちしています！";

    private final File file;
    private final Logger logger;
    private final int maxEntries;
    private final long retentionMillis;
    private final int dwellSeconds;

    RecentPlayerLocationStore(PatrolSpectatorPlugin plugin, int maxEntries, int retentionDays, int dwellSeconds) {
        this(new File(plugin.getDataFolder(), "recent_player_locations.yml"), plugin.getLogger(),
                maxEntries, retentionDays, dwellSeconds);
    }

    RecentPlayerLocationStore(File file, Logger logger, int maxEntries, int retentionDays, int dwellSeconds) {
        this.file = file;
        this.logger = logger;
        this.maxEntries = Math.max(1, maxEntries);
        this.retentionMillis = Math.max(1, retentionDays) * 24L * 60L * 60L * 1000L;
        this.dwellSeconds = Math.max(3, dwellSeconds);
    }

    List<TouristLocation> load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return List.of();

        long cutoff = System.currentTimeMillis() - retentionMillis;
        List<Entry> entries = new ArrayList<>();
        for (String key : players.getKeys(false)) {
            ConfigurationSection section = players.getConfigurationSection(key);
            if (section == null) continue;
            Entry entry = readEntry(key, section);
            if (entry != null && entry.updatedAt >= cutoff) entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(Entry::updatedAt).reversed());
        if (entries.size() > maxEntries) entries = new ArrayList<>(entries.subList(0, maxEntries));

        rewrite(entries);
        return entries.stream().map(this::toTouristLocation).toList();
    }

    TouristLocation record(Player player) {
        if (player == null) return null;
        return record(player.getUniqueId(), player.getName(), player.getLocation(), createPlayerHead(player),
                System.currentTimeMillis());
    }

    TouristLocation record(UUID uuid, String playerName, Location location, long updatedAt) {
        return record(uuid, playerName, location, null, updatedAt);
    }

    TouristLocation record(UUID uuid, String playerName, Location location, ItemStack playerHead, long updatedAt) {
        if (uuid == null || playerName == null || location == null || location.getWorld() == null) return null;

        List<Entry> entries = readAllUnexpired(updatedAt);
        entries.removeIf(entry -> entry.uuid.equals(uuid));
        entries.add(new Entry(uuid, safePlayerName(playerName), location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), updatedAt,
                safeHead(playerHead)));
        entries.sort(Comparator.comparingLong(Entry::updatedAt).reversed());
        if (entries.size() > maxEntries) entries = new ArrayList<>(entries.subList(0, maxEntries));
        rewrite(entries);
        return toTouristLocation(entries.get(0));
    }

    private List<Entry> readAllUnexpired(long now) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return new ArrayList<>();
        long cutoff = now - retentionMillis;
        List<Entry> entries = new ArrayList<>();
        for (String key : players.getKeys(false)) {
            ConfigurationSection section = players.getConfigurationSection(key);
            Entry entry = section == null ? null : readEntry(key, section);
            if (entry != null && entry.updatedAt >= cutoff) entries.add(entry);
        }
        return entries;
    }

    private Entry readEntry(String key, ConfigurationSection section) {
        try {
            UUID uuid = UUID.fromString(key);
            String name = safePlayerName(section.getString("name", "Player"));
            String world = section.getString("world");
            if (world == null || world.isBlank()) return null;
            return new Entry(uuid, name, world,
                    section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw"), (float) section.getDouble("pitch"),
                    section.getLong("updatedAt"), decodeHead(section.getString("headData")));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void rewrite(List<Entry> entries) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Entry entry : entries) {
            String base = "players." + entry.uuid;
            yaml.set(base + ".name", entry.playerName);
            yaml.set(base + ".world", entry.world);
            yaml.set(base + ".x", entry.x);
            yaml.set(base + ".y", entry.y);
            yaml.set(base + ".z", entry.z);
            yaml.set(base + ".yaw", entry.yaw);
            yaml.set(base + ".pitch", entry.pitch);
            yaml.set(base + ".updatedAt", entry.updatedAt);
            String headData = encodeHead(entry.playerHead);
            if (headData != null) yaml.set(base + ".headData", headData);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "[Patrol] 最近の参加地点を保存できませんでした。", e);
        }
    }

    private TouristLocation toTouristLocation(Entry entry) {
        return new TouristLocation(
                ID_PREFIX + entry.uuid,
                "§b" + entry.playerName,
                entry.world, entry.x, entry.y, entry.z, entry.yaw, entry.pitch,
                RETURN_MESSAGE, worldType(entry.world), dwellSeconds, false);
    }

    static boolean isRecentPlayerLocation(TouristLocation location) {
        return location != null && location.id != null && location.id.startsWith(ID_PREFIX);
    }

    ItemStack loadPlayerHead(TouristLocation location) {
        UUID uuid = playerUuid(location);
        if (uuid == null) return new ItemStack(Material.PLAYER_HEAD);

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return headOrDefault(decodeHead(yaml.getString("players." + uuid + ".headData")));
    }

    static UUID playerUuid(TouristLocation location) {
        if (!isRecentPlayerLocation(location)) return null;
        try {
            return UUID.fromString(location.id.substring(ID_PREFIX.length()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setPlayerProfile(player.getPlayerProfile());
            head.setItemMeta(meta);
        } catch (Throwable ignored) {
            // Geyser/Floodgate側でスキンが公開されていない場合は通常ヘッドを使う。
        }
        return head;
    }

    private static ItemStack safeHead(ItemStack head) {
        return head != null && head.getType() == Material.PLAYER_HEAD ? head.clone() : null;
    }

    private static ItemStack headOrDefault(ItemStack head) {
        ItemStack safe = safeHead(head);
        return safe == null ? new ItemStack(Material.PLAYER_HEAD) : safe;
    }

    private static String encodeHead(ItemStack head) {
        ItemStack safe = safeHead(head);
        if (safe == null) return null;
        try {
            return Base64.getEncoder().encodeToString(safe.serializeAsBytes());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack decodeHead(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            return safeHead(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safePlayerName(String name) {
        String stripped = ChatColor.stripColor(name);
        return stripped == null || stripped.isBlank() ? "Player" : stripped.replaceAll("[\\r\\n]", "");
    }

    private static String worldType(String worldName) {
        String lower = worldName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith("_the_end")) return "end";
        if (lower.endsWith("_nether")) return "nether";
        return "overworld";
    }

    private record Entry(UUID uuid, String playerName, String world, double x, double y, double z,
                         float yaw, float pitch, long updatedAt, ItemStack playerHead) { }
}
