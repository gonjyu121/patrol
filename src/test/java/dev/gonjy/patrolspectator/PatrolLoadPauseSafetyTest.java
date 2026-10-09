package dev.gonjy.patrolspectator;

import org.bukkit.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PatrolLoadPauseSafetyTest {

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
    void loadPauseKeepsCameraProtectedAndCanResumeWithoutFullStop() {
        PlayerMock camera = server.addPlayer("OtouGame");
        PatrolManager manager = plugin.getPatrolManager();

        manager.startPatrol(camera, 10);
        assertTrue(manager.isRunning());

        assertTrue(manager.pausePatrolForLoad());
        assertFalse(manager.isRunning());
        assertEquals(GameMode.SPECTATOR, camera.getGameMode());
        assertTrue(camera.isInvulnerable());
        assertTrue(camera.isFlying());

        assertTrue(manager.resumePatrolAfterLoad());
        assertTrue(manager.isRunning());
        assertEquals(GameMode.SPECTATOR, camera.getGameMode());
        assertTrue(camera.isInvulnerable());
    }
}
