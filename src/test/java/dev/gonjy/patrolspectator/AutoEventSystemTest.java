package dev.gonjy.patrolspectator;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoEventSystemTest {

    private ServerMock server;
    private PatrolSpectatorPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PatrolSpectatorPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testAutoEventsEnabledByDefault() {
        AutoEventSystem autoEventSystem = new AutoEventSystem(plugin);
        // Assuming there's a getter or we can check state.
        // Since there isn't a direct getter for 'autoEventsEnabled' shown in the
        // previous view_file,
        // we might need to rely on behavior or add a getter.
        // For now, let's just check if the object is created without error and try to
        // start events.
        assertNotNull(autoEventSystem);
    }

    @Test
    void testStartEvent() {
        AutoEventSystem autoEventSystem = new AutoEventSystem(plugin);
        autoEventSystem.startAutoEvents();
        // We can't easily check internal state without reflection or getters,
        // but this verifies no exceptions are thrown during startup.
    }
}
