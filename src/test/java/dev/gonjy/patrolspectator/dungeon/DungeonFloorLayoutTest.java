package dev.gonjy.patrolspectator.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonFloorLayoutTest {

    @Test
    void spawnClearanceBlocksNearbyCentersButAllowsDistantOnes() {
        assertTrue(DungeonManager.isTooCloseToSpawn(0, 0, 0, 0));
        assertTrue(DungeonManager.isTooCloseToSpawn(63, -63, 0, 0));
        assertFalse(DungeonManager.isTooCloseToSpawn(64, 0, 0, 0));
        assertFalse(DungeonManager.isTooCloseToSpawn(256, 256, 0, 0));
    }

    @Test
    void calculatesMaximumFloorsWithoutCrossingWorldBottom() {
        assertEquals(22, DungeonManager.calculateFloorCount(64, -64, 100));
    }

    @Test
    void respectsConfiguredFloorLimit() {
        assertEquals(8, DungeonManager.calculateFloorCount(64, -64, 8));
    }

    @Test
    void alwaysKeepsAtLeastEntranceFloor() {
        assertEquals(1, DungeonManager.calculateFloorCount(-63, -64, 100));
        assertEquals(1, DungeonManager.calculateFloorCount(64, -64, 0));
    }

    @Test
    void lootProgressionIncreasesFromEntranceToDeepestFloor() {
        assertEquals(0.0, DungeonLootSystem.progression(1, 22));
        assertEquals(1.0, DungeonLootSystem.progression(22, 22));
        assertEquals(1.0, DungeonLootSystem.progression(100, 22));
    }
}
