package dev.gonjy.patrolspectator;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndResetManagerTest {

    private ServerMock server;
    private PatrolSpectatorPlugin plugin;
    private EndResetManager endResetManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PatrolSpectatorPlugin.class);
        endResetManager = plugin.getEndResetManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testEndResetManagerCreation() {
        assertNotNull(endResetManager);
    }

    @Test
    void testCountdownAndCancel() {
        assertNotNull(endResetManager);
        assertEquals(-1, endResetManager.getRemainingResetTimeMillis());

        endResetManager.forceReset();
        assertTrue(endResetManager.getRemainingResetTimeMillis() > 0);

        endResetManager.cancelReset();
        assertEquals(-1, endResetManager.getRemainingResetTimeMillis());
        assertFalse(endResetManager.isResetting());
    }
}
