package dev.gonjy.patrolspectator;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class PlayerStatsStorage {
    private static final Map<String, Integer> playtime = new HashMap<>();

    public static void addPlaytime(Player player, int ticks) {
        playtime.put(player.getName(), playtime.getOrDefault(player.getName(), 0) + ticks);
    }

    public static int getPlaytime(Player player) {
        return playtime.getOrDefault(player.getName(), 0);
    }

    public static void reset(Player player) {
        playtime.remove(player.getName());
    }

    public static void resetAll() {
        playtime.clear();
    }
}
