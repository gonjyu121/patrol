package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraMoveLoadGuardTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defersMoveOnlyWhenEnabledAndAverageTickTimeIsHigh() {
        assertTrue(TickMonitor.shouldDeferCameraMove(true, 45.01));
        assertFalse(TickMonitor.shouldDeferCameraMove(true, 45.0));
        assertFalse(TickMonitor.shouldDeferCameraMove(false, 80.0));
    }

    @Test
    void safeCameraMoveDefaultsAreLoaded() {
        MockBukkit.mock();
        PatrolSpectatorPlugin plugin = MockBukkit.load(PatrolSpectatorPlugin.class);

        assertTrue(plugin.getPerformanceConf().deferCameraMovesOnHighLoad);
        assertEquals(5, plugin.getPerformanceConf().highLoadRetrySeconds);
    }
}
