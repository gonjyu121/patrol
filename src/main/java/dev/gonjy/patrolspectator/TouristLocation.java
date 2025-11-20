package dev.gonjy.patrolspectator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 巡回スポット1件分
 */
public class TouristLocation {
    public final String id;
    public final String name;
    public final String world;
    public final double x, y, z;
    public final float yaw, pitch;
    public final String description;
    public final String worldType; // "overworld", "nether", "end"

    public TouristLocation(String id, String name, String world, double x, double y, double z, float yaw, float pitch,
            String description, String worldType) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.description = description != null ? description : "";
        this.worldType = worldType != null ? worldType : "overworld";
    }

    /**
     * YAMLファイルから観光地リストを読み込みます。
     */
    public static List<TouristLocation> loadFromYaml(File file) {
        List<TouristLocation> list = new ArrayList<>();
        if (!file.exists())
            return list;

        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(file);
        List<Map<?, ?>> maps = config.getMapList("locations");
        return fromMapList(maps);
    }

    /**
     * MapのリストからTouristLocationのリストを生成します。
     */
    public static List<TouristLocation> fromMapList(List<Map<?, ?>> maps) {
        List<TouristLocation> list = new ArrayList<>();
        if (maps == null)
            return list;

        for (Map<?, ?> map : maps) {
            try {
                @SuppressWarnings("unchecked")
                TouristLocation loc = fromMap((Map<String, Object>) map);
                if (loc != null) {
                    list.add(loc);
                }
            } catch (Exception e) {
                // ログ出力などは呼び出し元に任せるか、ここで標準出力に出す
                System.err.println("Failed to parse location: " + e.getMessage());
            }
        }
        return list;
    }

    /**
     * 指定されたワールド内でランダムな観光地を自動生成します。
     * （簡易実装：ランダムな座標を生成）
     */
    public static List<TouristLocation> autoGenerate(org.bukkit.World world, int count, int radius, double yOffset) {
        List<TouristLocation> list = new ArrayList<>();
        if (world == null)
            return list;

        Random random = new Random();
        for (int i = 0; i < count; i++) {
            double x = (random.nextDouble() * 2 - 1) * radius;
            double z = (random.nextDouble() * 2 - 1) * radius;
            double y = world.getHighestBlockYAt((int) x, (int) z) + yOffset + 1.5; // 地面より少し上

            String id = "auto_" + i;
            String name = "Auto Point " + (i + 1);

            list.add(new TouristLocation(id, name, world.getName(), x, y, z, 0f, 0f, "Auto Generated", "overworld"));
        }
        return list;
    }

    public static TouristLocation fromMap(Map<String, Object> map) {
        String id = (String) map.getOrDefault("id", UUID.randomUUID().toString());
        String name = (String) map.getOrDefault("name", "Unknown");
        String world = (String) map.getOrDefault("world", "world");

        // 座標の取得（数値型へのキャストを安全に）
        double x = getDouble(map, "x");
        double y = getDouble(map, "y");
        double z = getDouble(map, "z");
        float yaw = (float) getDouble(map, "yaw");
        float pitch = (float) getDouble(map, "pitch");

        String description = (String) map.getOrDefault("description", "");
        String worldType = (String) map.getOrDefault("worldType", "overworld");

        return new TouristLocation(id, name, world, x, y, z, yaw, pitch, description, worldType);
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return 0.0;
    }

    @Override
    public String toString() {
        return String.format("TouristLocation{id='%s', name='%s', loc=(%s,%.1f,%.1f,%.1f)}",
                id, name, world, x, y, z);
    }
}
