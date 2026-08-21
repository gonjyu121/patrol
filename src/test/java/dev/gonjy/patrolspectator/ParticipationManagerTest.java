package dev.gonjy.patrolspectator;

import org.mockbukkit.mockbukkit.MockBukkit;

// import org.mockbukkit.mockbukkit.entity.PlayerMock; // Unused
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ParticipationManagerTest {

    // private ServerMock server; // Unused
    private PatrolSpectatorPlugin plugin;
    private ParticipationManager manager;

    @BeforeEach
    void setUp() {
        // server = MockBukkit.mock();
        MockBukkit.mock();
        plugin = MockBukkit.load(PatrolSpectatorPlugin.class);
        manager = new ParticipationManager(plugin);
    }

    @AfterEach
    void tearDown() {
        // Clean up test files
        File dataFolder = plugin.getDataFolder();
        if (dataFolder.exists()) {
            File participationFile = new File(dataFolder, "participation.yml");
            if (participationFile.exists()) {
                participationFile.delete();
            }
        }
        MockBukkit.unmock();
    }

    @Test
    void testManagerCreation() {
        assertNotNull(manager);
    }

    @Test
    void testIncrementJoinCount() {
        UUID playerId = UUID.randomUUID();
        String playerName = "TestPlayer";

        int count1 = manager.incrementJoinCount(playerId, playerName);
        assertEquals(1, count1);

        int count2 = manager.incrementJoinCount(playerId, playerName);
        assertEquals(2, count2);
    }

    @Test
    void testAddPoints() {
        UUID playerId = UUID.randomUUID();
        String playerName = "TestPlayer";

        // Should not throw any exceptions
        manager.addPoints(playerId, playerName, 10, "test");
        manager.addPoints(playerId, playerName, 5, "test2");
    }

    @Test
    void testAddZeroPoints() {
        UUID playerId = UUID.randomUUID();
        String playerName = "TestPlayer";

        // Adding 0 points should be a no-op
        manager.addPoints(playerId, playerName, 0, "test");
    }

    @Test
    void testTopNWithNoData() {
        List<ParticipationManager.Entry> top = manager.topN(10);
        assertNotNull(top);
        assertTrue(top.isEmpty());
    }

    @Test
    void testTopNWithData() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        manager.addPoints(player1, "Player1", 100, "test");
        manager.addPoints(player2, "Player2", 200, "test");
        manager.addPoints(player3, "Player3", 150, "test");

        List<ParticipationManager.Entry> top = manager.topN(2);
        assertEquals(2, top.size());

        // Should be sorted by score descending
        assertEquals(200, top.get(0).score());
        assertEquals(150, top.get(1).score());
    }

    // thankOnJoin test removed

    // testMultipleJoinsIncrementCount removed as it relied on thankOnJoin
}
