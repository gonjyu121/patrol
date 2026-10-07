package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ServerSettingsGuardTest {
    @Test
    void repairsManagedPropertiesAndPreservesOtherLines() {
        String original = "# server settings\r\nspawn-protection=16\r\nonline-mode=true\r\n"
                + "enforce-secure-profile=true\r\n";
        String updated = ServerSettingsGuard.updatePropertiesText(original, desired());
        assertTrue(updated.contains("# server settings\r\n"));
        assertTrue(updated.contains("spawn-protection=0\r\n"));
        assertTrue(updated.contains("online-mode=true\r\n"));
        assertTrue(updated.contains("enforce-secure-profile=false\r\n"));
        assertFalse(updated.contains("spawn-protection=16"));
    }

    @Test
    void appendsMissingPropertiesAndIsIdempotent() {
        String updated = ServerSettingsGuard.updatePropertiesText("motd=Patrol Server\n", desired());
        assertEquals(updated, ServerSettingsGuard.updatePropertiesText(updated, desired()));
        assertTrue(updated.contains("spawn-protection=0"));
        assertTrue(updated.contains("enforce-secure-profile=false"));
    }

    @Test
    void doesNotTreatCommentedPropertiesAsConfigured() {
        String updated = ServerSettingsGuard.updatePropertiesText("# enforce-secure-profile=true\n", desired());
        assertTrue(updated.contains("# enforce-secure-profile=true"));
        assertTrue(updated.contains("enforce-secure-profile=false"));
    }

    private static Map<String, String> desired() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("spawn-protection", "0");
        values.put("enforce-secure-profile", "false");
        return values;
    }
}
