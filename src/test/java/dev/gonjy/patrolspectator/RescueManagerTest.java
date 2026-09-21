package dev.gonjy.patrolspectator;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescueManagerTest {
    @Test
    void rescueIsOnlyAvailableShortlyAfterDeath() {
        long now = 1_000_000L;
        assertEquals(RescueManager.Denial.NONE,
                RescueManager.denialReason(now, now - 59_999L, null, null));
        assertEquals(RescueManager.Denial.NOT_RECENTLY_DEAD,
                RescueManager.denialReason(now, now - 60_001L, null, null));
        assertEquals(RescueManager.Denial.NOT_RECENTLY_DEAD,
                RescueManager.denialReason(now, null, null, null));
    }

    @Test
    void recentCombatAndCooldownBlockRescue() {
        long now = 1_000_000L;
        assertEquals(RescueManager.Denial.RECENTLY_DAMAGED,
                RescueManager.denialReason(now, now - 1_000L, now - 4_999L, null));
        assertEquals(RescueManager.Denial.COOLDOWN,
                RescueManager.denialReason(now, now - 1_000L, now - 5_001L, now + 1L));
    }

    @Test
    void dangerousAndUnstableSurfacesAreRejected() {
        assertTrue(RescueManager.isSafeGround(Material.GRASS_BLOCK));
        assertTrue(RescueManager.isSafeGround(Material.STONE));
        assertFalse(RescueManager.isSafeGround(Material.WATER));
        assertFalse(RescueManager.isSafeGround(Material.LAVA));
        assertFalse(RescueManager.isSafeGround(Material.MAGMA_BLOCK));
        assertFalse(RescueManager.isSafeGround(Material.OAK_LEAVES));
    }

    @Test
    void playerFacingDenialsNeverExposeCoordinates() {
        for (RescueManager.Denial denial : RescueManager.Denial.values()) {
            assertFalse(denial.message.contains("world"));
            assertFalse(denial.message.matches(".*[XYZxyz][=:]-?\\d+.*"));
            assertFalse(denial.message.contains("座標"));
        }
    }
}
