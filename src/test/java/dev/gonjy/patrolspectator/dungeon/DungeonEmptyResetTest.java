package dev.gonjy.patrolspectator.dungeon;

import dev.gonjy.patrolspectator.PatrolSpectatorPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DungeonEmptyResetTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void schedulesOnlyWhenPreviouslyOccupiedDungeonBecomesEmpty() {
        assertTrue(DungeonListener.shouldScheduleEmptyReset(true, false, false));
        assertFalse(DungeonListener.shouldScheduleEmptyReset(false, false, false));
        assertFalse(DungeonListener.shouldScheduleEmptyReset(true, true, false));
        assertFalse(DungeonListener.shouldScheduleEmptyReset(true, false, true));
    }

    @Test
    void defaultEmptyResetDelayIsSixtySeconds() {
        MockBukkit.mock();
        PatrolSpectatorPlugin plugin = MockBukkit.load(PatrolSpectatorPlugin.class);

        assertEquals(60, plugin.getConfig().getInt("dungeon.resetWhenEmptyDelaySeconds"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resettingTrapStateClearsPlayerCooldowns() throws Exception {
        TrapRunner runner = new TrapRunner(mock(PatrolSpectatorPlugin.class), mock(DungeonManager.class));
        Field field = TrapRunner.class.getDeclaredField("cooldowns");
        field.setAccessible(true);
        Map<UUID, Long> cooldowns = (Map<UUID, Long>) field.get(runner);
        cooldowns.put(UUID.randomUUID(), System.currentTimeMillis() + 30_000L);

        runner.resetState();

        assertTrue(cooldowns.isEmpty());
    }
}
