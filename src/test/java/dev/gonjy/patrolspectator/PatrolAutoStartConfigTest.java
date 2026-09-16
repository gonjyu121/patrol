package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatrolAutoStartConfigTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void autoStartUsesTenSecondDefault() {
        MockBukkit.mock();
        PatrolSpectatorPlugin plugin = MockBukkit.load(PatrolSpectatorPlugin.class);

        assertEquals(10, plugin.getAutoStartConf().dwellSeconds);
    }
}
