package dev.gonjy.patrolspectator.dungeon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonEntranceTest {

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
    void cameraIsPlacedInsideApproachBelowCeiling() {
        Location center = new Location(world, 100.8, 64.9, 200.7);

        Location camera = DungeonBuilder.createEntranceCameraLocation(center);

        assertEquals(100.5, camera.getX());
        assertEquals(64.2, camera.getY());
        assertEquals(163.5, camera.getZ());
        assertTrue(camera.getY() + 1.62 < center.getBlockY() + 3,
                "プレイヤーの目線が3ブロック高の通路天井より下に収まること");
    }

    @Test
    void approachIsOpenFromCameraToDungeonInterior() {
        int centerX = 100;
        int baseY = 64;
        int entranceZ = 170;

        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = entranceZ - 8; z <= entranceZ + 1; z++) {
                for (int y = baseY - 1; y <= baseY + 2; y++) {
                    world.getBlockAt(x, y, z).setType(Material.BEDROCK);
                }
            }
        }

        DungeonBuilder.carveEntranceApproach(world, centerX, baseY, entranceZ);

        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = entranceZ - 8; z <= entranceZ + 1; z++) {
                assertEquals(Material.POLISHED_BLACKSTONE, world.getBlockAt(x, baseY - 1, z).getType());
                for (int y = baseY; y <= baseY + 2; y++) {
                    assertTrue(world.getBlockAt(x, y, z).getType().isAir());
                }
            }
        }
    }
}
