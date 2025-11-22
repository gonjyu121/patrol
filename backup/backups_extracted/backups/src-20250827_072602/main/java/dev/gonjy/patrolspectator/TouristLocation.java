package dev.gonjy.patrolspectator;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * 観光地情報を管理するクラス
 */
public class TouristLocation {
    private final String name;
    private final Location location;
    private final String description;
    private final String worldType; // "overworld", "nether", "end"
    
    public TouristLocation(String name, Location location, String description, String worldType) {
        this.name = name;
        this.location = location;
        this.description = description;
        this.worldType = worldType;
    }
    
    public String getName() {
        return name;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getWorldType() {
        return worldType;
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s) - %s", name, worldType, description);
    }
}
