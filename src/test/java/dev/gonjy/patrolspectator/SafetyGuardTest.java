package dev.gonjy.patrolspectator;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyGuardTest {
    @Test
    void reachUsesNearestPointOfTargetHitbox() {
        BoundingBox box = new BoundingBox(10.0, 0.0, 0.0, 11.0, 2.0, 1.0);

        assertFalse(SafetyGuard.isClearlyOutOfReach(0.0, 1.0, 0.5, box, 10.0));
        assertTrue(SafetyGuard.isClearlyOutOfReach(-0.01, 1.0, 0.5, box, 10.0));
    }

    @Test
    void uncertainOrInvalidReachAlwaysFailsOpen() {
        BoundingBox box = new BoundingBox(0.0, 0.0, 0.0, 1.0, 2.0, 1.0);

        assertFalse(SafetyGuard.isClearlyOutOfReach(Double.NaN, 1.0, 0.5, box, 10.0));
        assertFalse(SafetyGuard.isClearlyOutOfReach(100.0, 1.0, 0.5, box, 0.0));
    }

    @Test
    void breakWindowAllowsConfiguredLimitAndBlocksOnlyOverflow() {
        SafetyGuard.BreakWindow window = new SafetyGuard.BreakWindow();

        for (int i = 0; i < 50; i++) {
            assertFalse(window.recordAndCheck(i * 1_000_000L, 50));
        }
        assertTrue(window.recordAndCheck(100_000_000L, 50));
    }

    @Test
    void breakWindowResetsAfterOneSecondOrClockAnomaly() {
        SafetyGuard.BreakWindow window = new SafetyGuard.BreakWindow();
        for (int i = 0; i < 50; i++) {
            window.recordAndCheck(i, 50);
        }

        assertFalse(window.recordAndCheck(1_000_000_000L, 50));
        assertFalse(window.recordAndCheck(1L, 50));
    }
}
