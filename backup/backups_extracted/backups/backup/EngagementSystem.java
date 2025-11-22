package dev.gonjy.patrolspectator;

import dev.gonjy.patrolspectator.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class EngagementSystem {
    private final Plugin plugin;

    public EngagementSystem(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                int now = Ticks.current();
                if (now % 6000 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage("🕒 現在のプレイ時間 (tick基準): " + now);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[EngagementSystem] エラー: " + e.getMessage());
            }
        }, 0L, 600L);
    }
}
