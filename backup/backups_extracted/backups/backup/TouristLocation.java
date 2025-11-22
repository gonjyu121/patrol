package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class TouristLocation {
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public TouristLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0); // fallback
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    @Override
    public String toString() {
        return "TouristLocation{" +
                "world='" + worldName + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", yaw=" + yaw +
                ", pitch=" + pitch +
                '}';
    }
}
