package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeManagerAuthMessageTest {
    @Test
    void explainsInvalidGrantWithoutIncludingCredentialValues() {
        String message = YouTubeManager.authFailureMessage(
                new YouTubeApiClient.ApiException(400, "AUTH_ERROR", "invalid_grant"));

        assertTrue(message.contains("reason=invalid_grant"));
        assertTrue(message.contains("refreshToken"));
        assertFalse(message.contains("error_description"));
    }

    @Test
    void explainsInvalidClient() {
        String message = YouTubeManager.authFailureMessage(
                new YouTubeApiClient.ApiException(401, "AUTH_ERROR", "invalid_client"));

        assertTrue(message.contains("clientId"));
        assertTrue(message.contains("clientSecret"));
    }
}
