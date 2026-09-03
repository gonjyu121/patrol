package dev.gonjy.patrolspectator;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PatrolHomeStorageTest {

    private JavaPlugin plugin;
    private World world;
    private PatrolHomeStorage storage;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin("PatrolHomeStorageTest");
        storage = new PatrolHomeStorage(plugin);
    }

    @AfterEach
    void tearDown() {
        if (plugin != null) {
            File file = new File(plugin.getDataFolder(), "patrol_homes.yml");
            if (file.exists()) {
                file.delete();
            }
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    void savesTwoIndependentHomeSlots() {
        UUID playerId = UUID.randomUUID();
        Location first = new Location(world, 10.5, 64, -20.5, 90, 10);
        Location second = new Location(world, -150, 72, 340, -45, 5);

        assertTrue(storage.save(playerId, 1, first));
        assertTrue(storage.save(playerId, 2, second));

        assertLocationEquals(first, storage.load(playerId, 1));
        assertLocationEquals(second, storage.load(playerId, 2));
    }

    @Test
    void rejectsSlotsOutsideOneAndTwo() {
        UUID playerId = UUID.randomUUID();
        Location location = new Location(world, 0, 64, 0);

        assertFalse(storage.save(playerId, 0, location));
        assertFalse(storage.save(playerId, 3, location));
        assertNull(storage.load(playerId, 0));
        assertNull(storage.load(playerId, 3));
    }

    private void assertLocationEquals(Location expected, Location actual) {
        assertNotNull(actual);
        assertEquals(expected.getWorld(), actual.getWorld());
        assertEquals(expected.getX(), actual.getX(), 0.001);
        assertEquals(expected.getY(), actual.getY(), 0.001);
        assertEquals(expected.getZ(), actual.getZ(), 0.001);
        assertEquals(expected.getYaw(), actual.getYaw(), 0.001);
        assertEquals(expected.getPitch(), actual.getPitch(), 0.001);
    }
}
