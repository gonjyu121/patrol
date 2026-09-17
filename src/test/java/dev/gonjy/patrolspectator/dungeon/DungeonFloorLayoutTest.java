package dev.gonjy.patrolspectator.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DungeonFloorLayoutTest {

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
