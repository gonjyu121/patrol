package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PatrolCommandPrivacyTest {

    @Test
    void patrolLocationMessagesDoNotExposeWorldOrCoordinates() {
        String saved = PatrolCommand.homeSavedMessage(1);
        String registered = PatrolCommand.homeStatusMessage(1, true);
        String missing = PatrolCommand.homeStatusMessage(2, false);
        String startRegistered = PatrolCommand.startLocationStatusMessage(true);
        String startMissing = PatrolCommand.startLocationStatusMessage(false);
        String returnAvailable = PatrolCommand.savedReturnLocationMessage();
        String returned = PatrolCommand.teleportBackSuccessMessage();

        assertEquals("§a[Patrol] 帰還地点1を登録しました。", saved);
        assertEquals("§e1: §a登録済み", registered);
        assertEquals("§e2: §7未登録", missing);
        assertEquals("§b[Patrol] 保存地点: §a登録済み", startRegistered);
        assertEquals("§b[Patrol] 保存地点: §7未設定", startMissing);
        assertEquals("§a[Patrol] 保存済みの戻り地点があります。", returnAvailable);
        assertEquals("§a[Patrol] 保存済みの戻り地点へTPしました！", returned);

        for (String message : new String[]{
                saved, registered, missing, startRegistered, startMissing, returnAvailable, returned}) {
            assertFalse(message.contains("world"));
            assertFalse(message.matches(".*-?\\d+\\.\\d+.*"));
            assertFalse(message.contains("("));
        }
    }
}
