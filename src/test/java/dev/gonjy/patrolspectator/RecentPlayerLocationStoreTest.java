package dev.gonjy.patrolspectator;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RecentPlayerLocationStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsLatestLocationWithoutCoordinatesInPresentation() {
        File file = tempDir.resolve("recent.yml").toFile();
        RecentPlayerLocationStore store = new RecentPlayerLocationStore(file, Logger.getLogger("test"), 10, 30, 10);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        UUID uuid = UUID.randomUUID();

        TouristLocation location = store.record(uuid, "Alice", new Location(world, 123.5, 70, -456.5, 90, 12), 1_000_000L);

        assertNotNull(location);
        assertEquals("§bAlice", location.name);
        assertEquals("§fさんが最後にいた場所", RecentPlayerLocationStore.LOCATION_MESSAGE);
        assertEquals(RecentPlayerLocationStore.RETURN_MESSAGE, location.description);
        assertFalse(location.name.contains("123"));
        assertFalse(location.name.contains("456"));
        assertTrue(RecentPlayerLocationStore.isRecentPlayerLocation(location));
    }

    @Test
    void keepsLongPlayerNameSeparateFromLocationDescription() {
        File file = tempDir.resolve("recent.yml").toFile();
        RecentPlayerLocationStore store = new RecentPlayerLocationStore(file, Logger.getLogger("test"), 10, 30, 10);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");

        TouristLocation location = store.record(UUID.randomUUID(), "VeryLongPlayerName123",
                new Location(world, 1, 64, 1), 1_000_000L);

        assertNotNull(location);
        assertEquals("§bVeryLongPlayerName123", location.name);
        assertFalse(location.name.contains("最後にいた場所"));
    }

    @Test
    void keepsOnlyLatestEntryForSamePlayer() {
        File file = tempDir.resolve("recent.yml").toFile();
        RecentPlayerLocationStore store = new RecentPlayerLocationStore(file, Logger.getLogger("test"), 10, 30, 10);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        UUID uuid = UUID.randomUUID();
        long now = System.currentTimeMillis();

        store.record(uuid, "Alice", new Location(world, 1, 64, 1), now - 1000);
        store.record(uuid, "Alice", new Location(world, 9, 70, 9), now);
        List<TouristLocation> loaded = store.load();

        assertEquals(1, loaded.size());
        assertEquals(9, loaded.get(0).x);
        assertEquals(9, loaded.get(0).z);
    }

    @Test
    void enforcesConfiguredMaximumEntries() {
        File file = tempDir.resolve("recent.yml").toFile();
        RecentPlayerLocationStore store = new RecentPlayerLocationStore(file, Logger.getLogger("test"), 2, 30, 10);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        long now = System.currentTimeMillis();

        store.record(UUID.randomUUID(), "Alice", new Location(world, 1, 64, 1), now - 2);
        store.record(UUID.randomUUID(), "Bob", new Location(world, 2, 64, 2), now - 1);
        store.record(UUID.randomUUID(), "Carol", new Location(world, 3, 64, 3), now);

        List<TouristLocation> loaded = store.load();
        assertEquals(2, loaded.size());
        assertTrue(loaded.stream().anyMatch(location -> location.name.contains("Carol")));
        assertFalse(loaded.stream().anyMatch(location -> location.name.contains("Alice")));
    }
}
