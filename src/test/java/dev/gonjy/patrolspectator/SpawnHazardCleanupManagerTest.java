package dev.gonjy.patrolspectator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SpawnHazardCleanupManagerTest {
    @Test
    void managedAreaUsesSameSquareChunkRadiusAsSpawnReset() {
        World world = mock(World.class);
        Location spawn = new Location(world, 0, 64, 0);

        assertTrue(SpawnHazardCleanupManager.isWithinChunkRadius(spawn,
                new Location(world, 4 * 16 + 15, 64, 0), 4));
        assertFalse(SpawnHazardCleanupManager.isWithinChunkRadius(spawn,
                new Location(world, 5 * 16, 64, 0), 4));
    }

    @Test
    void managedAreaRejectsOtherWorld() {
        World first = mock(World.class);
        World second = mock(World.class);

        assertFalse(SpawnHazardCleanupManager.isWithinChunkRadius(
                new Location(first, 0, 64, 0), new Location(second, 0, 64, 0), 4));
    }

    @Test
    void hazardKindsNeverMatchOrdinaryBuildingBlocks() {
        assertTrue(SpawnHazardCleanupManager.HazardKind.LAVA.matches(Material.LAVA));
        assertTrue(SpawnHazardCleanupManager.HazardKind.FIRE.matches(Material.FIRE));
        assertTrue(SpawnHazardCleanupManager.HazardKind.TNT.matches(Material.TNT));
        assertFalse(SpawnHazardCleanupManager.HazardKind.LAVA.matches(Material.STONE));
        assertFalse(SpawnHazardCleanupManager.HazardKind.FIRE.matches(Material.OAK_PLANKS));
        assertFalse(SpawnHazardCleanupManager.HazardKind.TNT.matches(Material.DIAMOND_BLOCK));
    }
}
