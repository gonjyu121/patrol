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
    void excludesOnlyDungeonChunksAndKeepsRemainingSpawnArea() {
        World world = mock(World.class);
        List<SpawnResetManager.ChunkPosition> planned =
                SpawnResetManager.createPlan(new Location(world, 0, 64, 0), 4);

        SpawnResetManager.ResetPlan safe = SpawnResetManager.excludeDungeonChunks(
                planned, new Location(world, 70, 64, 0), 30);

        assertTrue(safe.excludedDungeonChunks() > 0);
        assertEquals(81, safe.chunks().size() + safe.excludedDungeonChunks());
        assertTrue(safe.chunks().contains(new SpawnResetManager.ChunkPosition(0, 0)));
        assertFalse(safe.chunks().contains(new SpawnResetManager.ChunkPosition(4, 0)));
    }

    @Test
    void confirmationExpiresAfterDeadline() {
        assertTrue(SpawnResetManager.isConfirmationValid(10_000L, 10_000L));
        assertFalse(SpawnResetManager.isConfirmationValid(9_999L, 10_000L));
        assertFalse(SpawnResetManager.isConfirmationValid(null, 10_000L));
    }

    @Test
    void ignoresOnlyConfiguredCameraPlayerRegardlessOfNameCase() {
        assertTrue(SpawnResetManager.isCameraPlayer("OtouGame", "OtouGame"));
        assertTrue(SpawnResetManager.isCameraPlayer("otougame", "OtouGame"));
        assertFalse(SpawnResetManager.isCameraPlayer(".OtouGame", "OtouGame"));
        assertFalse(SpawnResetManager.isCameraPlayer("participant", "OtouGame"));
        assertFalse(SpawnResetManager.isCameraPlayer(null, "OtouGame"));
    }

    @Test
    void reportsProgressEveryFiveChunksAndAtCompletion() {
        assertEquals(100L, SpawnResetManager.CHUNK_INTERVAL_TICKS);
        assertFalse(SpawnResetManager.shouldReportProgress(1, 79));
        assertTrue(SpawnResetManager.shouldReportProgress(5, 79));
        assertFalse(SpawnResetManager.shouldReportProgress(6, 79));
        assertTrue(SpawnResetManager.shouldReportProgress(79, 79));
    }
}
