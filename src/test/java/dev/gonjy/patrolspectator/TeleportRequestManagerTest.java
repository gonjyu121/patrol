package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportRequestManagerTest {
    @Test
    void requestExpiresAtSixtySeconds() {
        long created = 1_000_000L;
        TeleportRequestManager.Request request = new TeleportRequestManager.Request(
                UUID.randomUUID(), UUID.randomUUID(), created);

        assertFalse(TeleportRequestManager.isExpired(request, created + 59_999L));
        assertTrue(TeleportRequestManager.isExpired(request, created + 60_000L));
    }

    @Test
    void combatLockOnlyAppliesForTenSecondsAfterCombat() {
        long now = 1_000_000L;
        assertFalse(TeleportRequestManager.isCombatRecent(now, null));
        assertTrue(TeleportRequestManager.isCombatRecent(now, now - 9_999L));
        assertFalse(TeleportRequestManager.isCombatRecent(now, now - 10_000L));
    }
}
