package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudSyncFailureGuardTest {

    @Test
    void stopsRequestsAndLogsOnlyOnceAfterUnauthorized() {
        List<String> messages = new ArrayList<>();
        CloudSyncFailureGuard guard = new CloudSyncFailureGuard(capturingLogger(messages));

        assertTrue(guard.canRequest());
        guard.recordHttpFailure("プル", 401);
        guard.recordHttpFailure("プッシュ", 401);

        assertFalse(guard.canRequest());
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("同期を停止"));
    }

    @Test
    void transientFailureKeepsRequestsEnabled() {
        List<String> messages = new ArrayList<>();
        CloudSyncFailureGuard guard = new CloudSyncFailureGuard(capturingLogger(messages));

        guard.recordHttpFailure("プッシュ", 500);

        assertTrue(guard.canRequest());
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("次回の同期で再試行"));
    }

    private static Logger capturingLogger(List<String> messages) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }
}
