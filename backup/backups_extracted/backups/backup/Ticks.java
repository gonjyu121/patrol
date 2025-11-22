package dev.gonjy.patrolspectator.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Paper の Bukkit.getCurrentTick() が無い環境（Spigot）でも動作するティック取得ユーティリティ。
 * Paper: 反射で getCurrentTick() を呼ぶ
 * Spigot: 1tick 周期の scheduler でカウンタを進めるフォールバック
 */
public final class Ticks {
  private static volatile Method paperGetCurrentTick;
  private static final AtomicLong fallbackTick = new AtomicLong(0L);
  private static volatile BukkitTask tickerTask;
  private static volatile boolean initialized = false;

  private Ticks() {}

  public static void init(Plugin plugin) {
    if (initialized) return;
    initialized = true;

    try {
      Method m = Bukkit.class.getMethod("getCurrentTick");
      if (m.getReturnType() == int.class && m.getParameterCount() == 0) {
        paperGetCurrentTick = m;
      }
    } catch (NoSuchMethodException ignored) {
      paperGetCurrentTick = null;
    }

    if (paperGetCurrentTick == null) {
      // Spigot フォールバック: 1tick ごとにカウントアップ
      tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
        fallbackTick.incrementAndGet();
      }, 0L, 1L);
    }
  }

  public static void shutdown() {
    if (tickerTask != null) {
      try { tickerTask.cancel(); } catch (Throwable ignored) {}
      tickerTask = null;
    }
    initialized = false;
  }

  /** 現在ティック（Paper ならネイティブ、Spigot ならフォールバック） */
  public static int current() {
    Method m = paperGetCurrentTick;
    if (m != null) {
      try {
        return (int) m.invoke(null);
      } catch (Throwable ignored) {
        // フォールバックに降格
      }
    }
    long t = fallbackTick.get();
    return (int) (t & 0x7FFFFFFF);
  }
}
