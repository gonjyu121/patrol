package dev.gonjy.patrolspectator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatrolDiagnosticsCommandTest {
    @Test
    void hidesDiagnoseFromGeneralPlayersAndShowsItWithAdminPermission() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.isOp()).thenReturn(false);
        PatrolCommand command = command();

        List<String> hidden = command.onTabComplete(sender, mock(Command.class), "patrol", new String[] {"d"});
        assertFalse(hidden.contains("diagnose"));

        when(sender.hasPermission("patrol.admin")).thenReturn(true);
        List<String> visible = command.onTabComplete(sender, mock(Command.class), "patrol", new String[] {"d"});
        assertTrue(visible.contains("diagnose"));
    }

    @Test
    void rejectsDiagnoseWithoutAdminPermission() {
        CommandSender sender = mock(CommandSender.class);
        Command bukkitCommand = mock(Command.class);
        when(bukkitCommand.getName()).thenReturn("patrol");
        ServerDiagnosticsService service = mock(ServerDiagnosticsService.class);
        PatrolCommand command = command(service);

        assertTrue(command.onCommand(sender, bukkitCommand, "patrol", new String[] {"diagnose"}));
        verify(service, never()).run(sender);
    }

    @Test
    void allowsDiagnoseWithAdminPermission() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("patrol.admin")).thenReturn(true);
        Command bukkitCommand = mock(Command.class);
        when(bukkitCommand.getName()).thenReturn("patrol");
        ServerDiagnosticsService service = mock(ServerDiagnosticsService.class);
        PatrolCommand command = command(service);

        assertTrue(command.onCommand(sender, bukkitCommand, "patrol", new String[] {"diagnose"}));
        verify(service).run(sender);
    }

    private PatrolCommand command() {
        return command(mock(ServerDiagnosticsService.class));
    }

    private PatrolCommand command(ServerDiagnosticsService service) {
        return new PatrolCommand(mock(PatrolSpectatorPlugin.class), mock(PatrolManager.class),
                null, null, null, null, null, service);
    }
}
