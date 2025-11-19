package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;

/**
 * 巡回スポット1件分
 */
public class TouristLocation {
    private final String id;
    private final String name;
    private final Location location;

    public TouristLocation(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Location getLocation() { return location; }

    /**
     * YAMLの1要素（Map）から安全に生成する。
     * id/name/world/x/y/z/yaw/pitch を読み取り、欠損はデフォルトで補完。
     */
    public static TouristLocation fromMap(Map<?, ?> m) {
        if (m == null) return null;

        String id = asString(m.get("id"));
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();

        String name = asString(m.get("name"));
        if (name == null || name.isEmpty()) name = id;

        String worldName = asString(m.get("world"));
        World world = (worldName != null) ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            // world指定なし/不正時は既定ワールドを使う
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (worldName == null) worldName = (world != null ? world.getName() : "world");
        }

        double x = asDouble(m.get("x"), 0.0);
        double y = asDouble(m.get("y"), 64.0);
        double z = asDouble(m.get("z"), 0.0);
        float yaw = (float) asDouble(m.get("yaw"), 0.0);
        float pitch = (float) asDouble(m.get("pitch"), 0.0);

        if (world == null) return null; // サーバ起動直後などでワールド未ロードの場合の保険

        Location loc = new Location(world, x, y, z, yaw, pitch);
        return new TouristLocation(id, name, loc);
    }

    // ===== helpers =====
    private static String asString(Object v) {
        if (v == null) return null;
        String s = v.toString();
        return s.trim().isEmpty() ? null : s;
    }

    private static double asDouble(Object v, double def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception ignored) {}
        return def;
    }
}
