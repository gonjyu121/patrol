package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PatrolCommandPrivacyTest {

    @Test
    void homeMessagesDoNotExposeWorldOrCoordinates() {
        String saved = PatrolCommand.homeSavedMessage(1);
        String registered = PatrolCommand.homeStatusMessage(1, true);
        String missing = PatrolCommand.homeStatusMessage(2, false);

        assertEquals("§a[Patrol] 帰還地点1を登録しました。", saved);
        assertEquals("§e1: §a登録済み", registered);
        assertEquals("§e2: §7未登録", missing);

        for (String message : new String[]{saved, registered, missing}) {
            assertFalse(message.contains("world"));
            assertFalse(message.matches(".*-?\\d+\\.\\d+.*"));
            assertFalse(message.contains("("));
        }
    }
}
