package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

public final class EngagementSystem {
    private final JavaPlugin plugin;
    private final Logger log;

    public EngagementSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /** 起動時や必要時に呼ぶ：公平性維持のためHUDを抑制 */
    public void applyServerRules() {
        run("gamerule doDaylightCycle true");
        run("gamerule keepInventory false");
        // Bedrock環境の公平性維持（いずれか片方のみ有効な環境あり）
        run("gamerule locatorBar false");
        run("gamerule showCoordinates false");
    }

    private void run(String cmd) {
        try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
              log.info("[Rules] applied: " + cmd);
        } catch (Throwable t) {
              log.warning("[Rules] failed: " + cmd + " (" + t.getMessage() + ")");
        }
    }
}
