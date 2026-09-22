package dev.gonjy.patrolspectator;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SpawnResetManagerTest {

    @Test
    void defaultPlanContainsEightyOneUniqueChunksAndStartsAtSpawn() {
        World world = mock(World.class);
        Location spawn = new Location(world, 33, 64, -17);
        List<SpawnResetManager.ChunkPosition> plan =
                SpawnResetManager.createPlan(spawn, SpawnResetManager.DEFAULT_RADIUS_CHUNKS);

        assertEquals(81, plan.size());
        assertEquals(81, new HashSet<>(plan).size());
        assertEquals(new SpawnResetManager.ChunkPosition(2, -2), plan.get(0));
    }

    @Test
    void detectsDungeonChunkOverlapWithoutExposingCoordinates() {
        World world = mock(World.class);
        Location spawn = new Location(world, 0, 64, 0);
        HashSet<SpawnResetManager.ChunkPosition> area =
                new HashSet<>(SpawnResetManager.createPlan(spawn, 4));

        assertTrue(SpawnResetManager.overlapsDungeon(area, new Location(world, 70, 64, 0), 30));
        assertFalse(SpawnResetManager.overlapsDungeon(area, new Location(world, 200, 64, 200), 30));
    }

    @Test
    void confirmationExpiresAfterDeadline() {
        assertTrue(SpawnResetManager.isConfirmationValid(10_000L, 10_000L));
        assertFalse(SpawnResetManager.isConfirmationValid(9_999L, 10_000L));
        assertFalse(SpawnResetManager.isConfirmationValid(null, 10_000L));
    }
}
