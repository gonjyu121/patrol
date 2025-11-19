package dev.gonjy.patrolspectator;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameModeEnforcerTest {

    private ServerMock server;
    private PatrolSpectatorPlugin plugin;
    private GameModeEnforcer enforcer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PatrolSpectatorPlugin.class);
        enforcer = new GameModeEnforcer(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testEnforcerCreation() {
        assertNotNull(enforcer);
    }

    @Test
    void testStartAndStop() {
        // Should not throw any exceptions
        enforcer.start();
        enforcer.stop();
    }

    @Test
    void testSetCameraOperator() {
        PlayerMock player = server.addPlayer();
        enforcer.setCameraOperator(player.getUniqueId());
        // Should not throw any exceptions
    }

    @Test
    void testClearCameraOperator() {
        PlayerMock player = server.addPlayer();
        enforcer.setCameraOperator(player.getUniqueId());
        enforcer.clearCameraOperator();
        // Should not throw any exceptions
    }

    @Test
    void testGameModeEnforcement() {
        PlayerMock player = server.addPlayer();
        player.setGameMode(GameMode.CREATIVE);

        enforcer.start();

        // Wait for the task to run (it runs every 20 ticks = 1 second)
        server.getScheduler().performTicks(25L);

        // Player should be forced back to SURVIVAL
        assertEquals(GameMode.SURVIVAL, player.getGameMode());

        enforcer.stop();
    }

    @Test
    void testCameraOperatorExemption() {
        PlayerMock cameraPlayer = server.addPlayer("Camera");
        PlayerMock normalPlayer = server.addPlayer("Normal");

        cameraPlayer.setGameMode(GameMode.SPECTATOR);
        normalPlayer.setGameMode(GameMode.CREATIVE);

        enforcer.setCameraOperator(cameraPlayer.getUniqueId());
        enforcer.start();

        // Wait for the task to run
        server.getScheduler().performTicks(25L);

        // Camera operator should keep their game mode
        assertEquals(GameMode.SPECTATOR, cameraPlayer.getGameMode());

        // Normal player should be forced to SURVIVAL
        assertEquals(GameMode.SURVIVAL, normalPlayer.getGameMode());

        enforcer.stop();
    }
}
