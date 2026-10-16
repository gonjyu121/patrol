package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NoAutomaticOperatorEscalationTest {
    @Test
    void productionClassesDoNotCallPlayerSetOp() throws Exception {
        assertClassDoesNotReferenceSetOp(GameModeEnforcer.class);
        assertClassDoesNotReferenceSetOp(PatrolSpectatorPlugin.class);
    }

    private void assertClassDoesNotReferenceSetOp(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing compiled class: " + resource);
            String constantPool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(constantPool.contains("setOp"),
                    () -> type.getSimpleName() + " must not contain a hidden operator-escalation path");
        }
    }
}
