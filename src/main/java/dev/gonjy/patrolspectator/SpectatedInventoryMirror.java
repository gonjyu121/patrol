package dev.gonjy.patrolspectator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/** Mirrors the observed player's server-side container screen to the stream camera. */
final class SpectatedInventoryMirror implements Listener {
    private final PatrolSpectatorPlugin plugin;
    private final PatrolManager patrolManager;

    private UUID cameraUuid;
    private UUID targetUuid;
    private InventoryView targetView;
    private InventoryView cameraView;
    private BukkitTask validationTask;

    SpectatedInventoryMirror(PatrolSpectatorPlugin plugin, PatrolManager patrolManager) {
        this.plugin = plugin;
        this.patrolManager = patrolManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.getConfig().getBoolean("patrol.mirrorObservedContainers", true)) return;
        if (!(event.getPlayer() instanceof Player target)) return;
        if (!isMirrorableInventory(event.getInventory().getType())) return;
        if (!patrolManager.isObservingPlayer(target.getUniqueId())) return;

        Player camera = patrolManager.getCameraPlayer();
        if (camera == null || !camera.isOnline() || camera.getUniqueId().equals(target.getUniqueId())) return;

        Inventory source = event.getInventory();
        InventoryView openedTargetView = event.getView();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!camera.isOnline() || !target.isOnline()) return;
            if (!patrolManager.isObservingPlayer(target.getUniqueId())) return;
            if (!isActiveView(target.getOpenInventory(), openedTargetView)) return;

            try {
                cameraView = camera.openInventory(source);
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("観戦対象のコンテナ画面をミラーできませんでした: " + ex.getMessage());
                return;
            }
            cameraUuid = camera.getUniqueId();
            targetUuid = target.getUniqueId();
            targetView = openedTargetView;
            startValidation();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.getUniqueId().equals(targetUuid)) {
            closeCameraMirror();
        } else if (player.getUniqueId().equals(cameraUuid)) {
            clearState();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isCameraMirror(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isCameraMirror(player)) {
            event.setCancelled(true);
        }
    }

    private boolean isCameraMirror(Player player) {
        return cameraUuid != null && cameraUuid.equals(player.getUniqueId())
                && cameraView != null && player.getOpenInventory() == cameraView;
    }

    private void startValidation() {
        if (validationTask != null) validationTask.cancel();
        validationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player camera = cameraUuid == null ? null : Bukkit.getPlayer(cameraUuid);
            Player target = targetUuid == null ? null : Bukkit.getPlayer(targetUuid);
            if (camera == null || target == null || !camera.isOnline() || !target.isOnline()
                    || !patrolManager.isObservingPlayer(targetUuid)
                    || !isActiveView(target.getOpenInventory(), targetView)) {
                closeCameraMirror();
            }
        }, 1L, 1L);
    }

    private void closeCameraMirror() {
        Player camera = cameraUuid == null ? null : Bukkit.getPlayer(cameraUuid);
        boolean shouldClose = camera != null && camera.isOnline() && cameraView != null;
        clearState();
        if (shouldClose) camera.closeInventory();
    }

    private void clearState() {
        if (validationTask != null) {
            validationTask.cancel();
            validationTask = null;
        }
        cameraUuid = null;
        targetUuid = null;
        targetView = null;
        cameraView = null;
    }

    static boolean isActiveView(InventoryView current, InventoryView expected) {
        return expected != null && current == expected;
    }

    static boolean isMirrorableInventory(InventoryType type) {
        return type != null && type != InventoryType.PLAYER
                && type != InventoryType.CRAFTING
                && type != InventoryType.CREATIVE;
    }
}
