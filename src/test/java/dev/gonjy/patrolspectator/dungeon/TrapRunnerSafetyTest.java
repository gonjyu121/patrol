package dev.gonjy.patrolspectator.dungeon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrapRunnerSafetyTest {

    private WorldMock world;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        world = MockBukkit.getMock().addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void acceptsSolidFloorAndThreePassableBlocks() {
        Location location = new Location(world, 10.5, 64, 10.5);
        world.getBlockAt(10, 63, 10).setType(Material.BEDROCK);
        world.getBlockAt(10, 64, 10).setType(Material.AIR);
        world.getBlockAt(10, 65, 10).setType(Material.AIR);
        world.getBlockAt(10, 66, 10).setType(Material.AIR);

        assertTrue(TrapRunner.isSafeStandingLocation(location, 3));
    }

    @Test
    void rejectsLocationWithBlockedHeadroom() {
        Location location = new Location(world, 10.5, 64, 10.5);
        world.getBlockAt(10, 63, 10).setType(Material.BEDROCK);
        world.getBlockAt(10, 64, 10).setType(Material.AIR);
        world.getBlockAt(10, 65, 10).setType(Material.AIR);
        world.getBlockAt(10, 66, 10).setType(Material.BEDROCK);

        assertFalse(TrapRunner.isSafeStandingLocation(location, 3));
    }

    @Test
    void rejectsLocationWithoutSolidFloor() {
        Location location = new Location(world, 10.5, 64, 10.5);
        world.getBlockAt(10, 63, 10).setType(Material.AIR);

        assertFalse(TrapRunner.isSafeStandingLocation(location, 2));
    }
}
