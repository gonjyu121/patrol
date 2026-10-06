package dev.gonjy.patrolspectator;

import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PatrolBedrockHomeCommandTest {
    @Test
    void nonCameraOpCanSetHomeWhileCameraPatrolIsRunning() {
        Player bedrockOp = mock(Player.class);
        when(bedrockOp.isOp()).thenReturn(true);
        Command bukkitCommand = mock(Command.class);
        when(bukkitCommand.getName()).thenReturn("patrol");
        PatrolManager patrolManager = mock(PatrolManager.class);
        when(patrolManager.isRunning()).thenReturn(true);
        when(patrolManager.isCameraPlayer(bedrockOp)).thenReturn(false);
        when(patrolManager.saveHome(bedrockOp, 1)).thenReturn(true);

        PatrolCommand command = command(patrolManager);
        assertTrue(command.onCommand(bedrockOp, bukkitCommand, "patrol", new String[]{"sethome", "1"}));

        verify(patrolManager).saveHome(bedrockOp, 1);
        verify(bedrockOp).sendMessage(PatrolCommand.homeSavedMessage(1));
    }

    @Test
    void actualCameraStillCannotSaveTourLocationAsHome() {
        Player camera = mock(Player.class);
        when(camera.isOp()).thenReturn(true);
        Command bukkitCommand = mock(Command.class);
        when(bukkitCommand.getName()).thenReturn("patrol");
        PatrolManager patrolManager = mock(PatrolManager.class);
        when(patrolManager.isRunning()).thenReturn(true);
        when(patrolManager.isCameraPlayer(camera)).thenReturn(true);

        PatrolCommand command = command(patrolManager);
        assertTrue(command.onCommand(camera, bukkitCommand, "patrol", new String[]{"sethome", "1"}));

        verify(patrolManager, never()).saveHome(any(), anyInt());
    }

    private PatrolCommand command(PatrolManager patrolManager) {
        return new PatrolCommand(mock(PatrolSpectatorPlugin.class), patrolManager,
                null, null, null, null, null, mock(ServerDiagnosticsService.class));
    }
}
