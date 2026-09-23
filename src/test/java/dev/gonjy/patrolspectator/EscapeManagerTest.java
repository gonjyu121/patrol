package dev.gonjy.patrolspectator;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EscapeManagerTest {
    @Test
    void combatAndCooldownBlockEscape() {
        long now = 1_000_000L;
        assertEquals(EscapeManager.Denial.NONE, EscapeManager.denialReason(now, null, null));
        assertEquals(EscapeManager.Denial.RECENTLY_DAMAGED,
                EscapeManager.denialReason(now, now - 29_999L, null));
        assertEquals(EscapeManager.Denial.COOLDOWN,
                EscapeManager.denialReason(now, now - 30_001L, now + 1L));
    }

    @Test
    void positionMovementCancelsButLookingAroundDoesNot() {
        World world = mock(World.class);
        Location origin = new Location(world, 10.5, 64, 20.5, 0f, 0f);
        Location lookOnly = new Location(world, 10.5, 64, 20.5, 90f, 30f);
        Location moved = new Location(world, 10.6, 64, 20.5, 0f, 0f);

        assertFalse(EscapeManager.hasMoved(origin, lookOnly));
        assertTrue(EscapeManager.hasMoved(origin, moved));
    }

    @Test
    void denialMessagesDoNotExposeCoordinates() {
        for (EscapeManager.Denial denial : EscapeManager.Denial.values()) {
            assertFalse(denial.message.contains("world"));
            assertFalse(denial.message.matches(".*[XYZxyz][=:]-?\\d+.*"));
            assertFalse(denial.message.contains("座標"));
        }
    }
}
