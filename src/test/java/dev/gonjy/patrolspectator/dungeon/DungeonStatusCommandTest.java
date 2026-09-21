package dev.gonjy.patrolspectator.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

class DungeonStatusCommandTest {
    @Test
    void reportsPlannedAndVerifiedFloorsWithoutCoordinates() {
        DungeonManager manager = mock(DungeonManager.class);
        CommandSender sender = mock(CommandSender.class);
        World world = mock(World.class);
        when(sender.isOp()).thenReturn(true);
        when(manager.isEnabled()).thenReturn(true);
        when(manager.getCenter()).thenReturn(new Location(world, 1234, 64, 5678));
        when(manager.getFloorCount()).thenReturn(22);
        when(manager.getRecordedBuiltCount()).thenReturn(22);
        when(manager.isBuilt()).thenReturn(true);
        when(manager.inspectBuiltFloors()).thenReturn(new DungeonManager.FloorInspection(22, 22, 0));

        new DungeonCommand(manager).onCommand(sender, mock(Command.class), "dungeon", new String[] {"status"});

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(messages.capture());
        List<String> output = messages.getAllValues();
        assertTrue(output.stream().anyMatch(line -> line.contains("B1〜B22")));
        assertTrue(output.stream().anyMatch(line -> line.contains("22/22階")));
        assertTrue(output.stream().noneMatch(line -> line.contains("1234") || line.contains("5678")));
    }
}
