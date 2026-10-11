package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperSparkGuardTest {

    @Test
    void disablesOnlySparkEnabledAndPreservesOtherSettings() {
        String original = "_version: 31\r\nspark:\r\n  enable-immediately: false\r\n  enabled: true\r\n"
                + "timings:\r\n  enabled: true\r\n";

        String updated = PaperSparkGuard.disableSpark(original);

        assertTrue(updated.contains("spark:\r\n  enable-immediately: false\r\n  enabled: false"));
        assertTrue(updated.contains("timings:\r\n  enabled: true"));
        assertEquals(updated, PaperSparkGuard.disableSpark(updated));
    }

    @Test
    void addsMissingSparkSectionWithoutChangingExistingContent() {
        String original = "_version: 31\nproxies:\n  velocity:\n    enabled: true\n";

        String updated = PaperSparkGuard.disableSpark(original);

        assertTrue(updated.startsWith(original));
        assertTrue(updated.endsWith("spark:\n  enabled: false\n"));
    }

    @Test
    void addsMissingEnabledEntryToExistingSparkSection() {
        String original = "spark:\n  enable-immediately: false\nmessages:\n  kick: test\n";

        String updated = PaperSparkGuard.disableSpark(original);

        assertTrue(updated.startsWith("spark:\n  enabled: false\n  enable-immediately: false\n"));
        assertTrue(updated.contains("messages:\n  kick: test"));
    }
}
