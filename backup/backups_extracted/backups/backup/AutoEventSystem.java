package dev.gonjy.patrolspectator;

import dev.gonjy.patrolspectator.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class AutoEventSystem {
    private final Plugin plugin;
    private boolean running;

    public AutoEventSystem(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (running) return;
        running = true;

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                int now = Ticks.current();
                if (now % 12000 == 0) {
                    Bukkit.broadcastMessage("📊 自動イベント: 定期更新処理 tick=" + now);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[AutoEventSystem] タスク実行中エラー: " + e.getMessage());
            }
        }, 0L, 1200L); // 1分ごと
    }
}
