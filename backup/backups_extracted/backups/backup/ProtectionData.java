package dev.gonjy.patrolspectator;

import org.bukkit.Location;
import java.util.HashSet;
import java.util.Set;

public class ProtectionData {
    private static final Set<Location> protectedBlocks = new HashSet<>();

    public static void protect(Location loc) {
        protectedBlocks.add(loc);
    }

    public static boolean isProtected(Location loc) {
        return protectedBlocks.contains(loc);
    }

    public static void unprotect(Location loc) {
        protectedBlocks.remove(loc);
    }

    public static void clearAll() {
        protectedBlocks.clear();
    }
}
