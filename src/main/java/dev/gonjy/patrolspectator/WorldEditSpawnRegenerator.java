package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;

/** Isolates the optional WorldEdit API from the rest of the plugin. */
final class WorldEditSpawnRegenerator {
    private WorldEditSpawnRegenerator() {
    }

    static boolean isAvailable() {
        Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
        return worldEdit != null && worldEdit.isEnabled();
    }

    static boolean regenerateChunk(World world, int chunkX, int chunkZ) {
        Plugin worldEditPlugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (worldEditPlugin == null || !worldEditPlugin.isEnabled()) return false;

        try {
            ClassLoader loader = worldEditPlugin.getClass().getClassLoader();
            Class<?> worldEditClass = loader.loadClass("com.sk89q.worldedit.WorldEdit");
            Class<?> worldClass = loader.loadClass("com.sk89q.worldedit.world.World");
            Class<?> extentClass = loader.loadClass("com.sk89q.worldedit.extent.Extent");
            Class<?> regionClass = loader.loadClass("com.sk89q.worldedit.regions.Region");
            Class<?> vectorClass = loader.loadClass("com.sk89q.worldedit.math.BlockVector3");
            Class<?> cuboidClass = loader.loadClass("com.sk89q.worldedit.regions.CuboidRegion");
            Class<?> regenOptionsClass = loader.loadClass("com.sk89q.worldedit.world.RegenOptions");
            Class<?> regenOptionsBuilderClass = loader.loadClass("com.sk89q.worldedit.world.RegenOptions$Builder");
            Class<?> editSessionBuilderClass = loader.loadClass("com.sk89q.worldedit.EditSessionBuilder");
            Class<?> editSessionClass = loader.loadClass("com.sk89q.worldedit.EditSession");
            Class<?> sideEffectSetClass = loader.loadClass("com.sk89q.worldedit.util.SideEffectSet");
            Class<?> bukkitAdapterClass = loader.loadClass("com.sk89q.worldedit.bukkit.BukkitAdapter");

            Object worldEditWorld = bukkitAdapterClass.getMethod("adapt", World.class).invoke(null, world);
            Object minimum = vectorClass.getMethod("at", int.class, int.class, int.class)
                    .invoke(null, chunkX << 4, world.getMinHeight(), chunkZ << 4);
            Object maximum = vectorClass.getMethod("at", int.class, int.class, int.class)
                    .invoke(null, (chunkX << 4) + 15, world.getMaxHeight() - 1, (chunkZ << 4) + 15);
            Object region = cuboidClass.getConstructor(worldClass, vectorClass, vectorClass)
                    .newInstance(worldEditWorld, minimum, maximum);

            Object optionsBuilder = regenOptionsClass.getMethod("builder").invoke(null);
            regenOptionsBuilderClass.getMethod("regenBiomes", boolean.class).invoke(optionsBuilder, true);
            Object options = regenOptionsBuilderClass.getMethod("build").invoke(optionsBuilder);

            Object worldEdit = worldEditClass.getMethod("getInstance").invoke(null);
            Object editSessionBuilder = worldEditClass.getMethod("newEditSessionBuilder").invoke(worldEdit);
            editSessionBuilderClass.getMethod("world", worldClass).invoke(editSessionBuilder, worldEditWorld);
            editSessionBuilderClass.getMethod("maxBlocks", int.class).invoke(editSessionBuilder, -1);
            Object editSession = editSessionBuilderClass.getMethod("build").invoke(editSessionBuilder);

            Object noSideEffects = sideEffectSetClass.getMethod("none").invoke(null);
            editSessionClass.getMethod("setSideEffectApplier", sideEffectSetClass)
                    .invoke(editSession, noSideEffects);
            editSessionClass.getMethod("setTrackingHistory", boolean.class).invoke(editSession, false);
            editSessionClass.getMethod("setTickingWatchdog", boolean.class).invoke(editSession, true);

            try {
                Object result = worldClass.getMethod("regenerate", regionClass, extentClass, regenOptionsClass)
                        .invoke(worldEditWorld, region, editSession, options);
                return Boolean.TRUE.equals(result);
            } finally {
                if (editSession instanceof AutoCloseable closeable) closeable.close();
            }
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("WorldEdit regeneration failed", cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("WorldEdit API is incompatible", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("WorldEdit session could not be closed", ex);
        }
    }
}
