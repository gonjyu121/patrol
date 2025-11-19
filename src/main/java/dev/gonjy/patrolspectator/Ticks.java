package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

public final class Ticks {
    private static volatile Method paperGetCurrentTick;
    private static final AtomicLong fallbackTick = new AtomicLong();

    public static void init(Plugin plugin) {
        try {
            paperGetCurrentTick = Bukkit.class.getMethod("getCurrentTick");
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTaskTimer(plugin, () -> fallbackTick.incrementAndGet(), 1L, 1L);
        }
    }

    public static int current() {
        Method m = paperGetCurrentTick;
        if (m != null) {
            try { return (int) m.invoke(null); } catch (Throwable ignored) {}
        }
        return (int) (fallbackTick.get() & 0x7FFFFFFF);
    }
}
