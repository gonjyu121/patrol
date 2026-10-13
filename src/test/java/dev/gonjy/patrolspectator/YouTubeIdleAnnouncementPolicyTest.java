package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeIdleAnnouncementPolicyTest {
    @Test
    void waitsForSilenceAndResetsAfterHumanActivityOrAnnouncement() {
        YouTubeIdleAnnouncementPolicy policy = new YouTubeIdleAnnouncementPolicy(Duration.ofHours(3), 1_000L);
        assertFalse(policy.shouldAnnounce(1_000L + Duration.ofHours(3).toMillis() - 1));
        assertTrue(policy.shouldAnnounce(1_000L + Duration.ofHours(3).toMillis()));

        long activityAt = 2_000_000L;
        policy.recordActivity(activityAt);
        assertFalse(policy.shouldAnnounce(activityAt + Duration.ofHours(2).toMillis()));
        assertTrue(policy.shouldAnnounce(activityAt + Duration.ofHours(3).toMillis()));

        policy.recordAnnouncement(activityAt + Duration.ofHours(3).toMillis());
        assertFalse(policy.shouldAnnounce(activityAt + Duration.ofHours(4).toMillis()));
    }
}
