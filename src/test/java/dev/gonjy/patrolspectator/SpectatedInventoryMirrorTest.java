package dev.gonjy.patrolspectator;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SpectatedInventoryMirrorTest {
    @Test
    void mirrorsServerContainersButNotClientOwnedPlayerScreens() {
        assertTrue(SpectatedInventoryMirror.isMirrorableInventory(InventoryType.CHEST));
        assertTrue(SpectatedInventoryMirror.isMirrorableInventory(InventoryType.BARREL));
        assertTrue(SpectatedInventoryMirror.isMirrorableInventory(InventoryType.FURNACE));
        assertTrue(SpectatedInventoryMirror.isMirrorableInventory(InventoryType.MERCHANT));

        assertFalse(SpectatedInventoryMirror.isMirrorableInventory(InventoryType.PLAYER));
        assertFalse(SpectatedInventoryMirror.isMirrorableInventory(InventoryType.CRAFTING));
        assertFalse(SpectatedInventoryMirror.isMirrorableInventory(InventoryType.CREATIVE));
        assertFalse(SpectatedInventoryMirror.isMirrorableInventory(null));
    }

    @Test
    void considersOnlyTheExactStillOpenViewActive() {
        InventoryView opened = mock(InventoryView.class);
        InventoryView replacement = mock(InventoryView.class);

        assertTrue(SpectatedInventoryMirror.isActiveView(opened, opened));
        assertFalse(SpectatedInventoryMirror.isActiveView(replacement, opened));
        assertFalse(SpectatedInventoryMirror.isActiveView(opened, null));
    }
}
