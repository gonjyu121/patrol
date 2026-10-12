package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeAutoReplyPolicyTest {
    @Test
    void recognizesClearParticipationQuestionsOnly() {
        assertTrue(YouTubeAutoReplyPolicy.isParticipationQuestion("参加してもいいですか？"));
        assertTrue(YouTubeAutoReplyPolicy.isParticipationQuestion("サーバー入っていい？"));
        assertTrue(YouTubeAutoReplyPolicy.isParticipationQuestion("参加できますか！"));
        assertFalse(YouTubeAutoReplyPolicy.isParticipationQuestion("昨日参加しました"));
        assertFalse(YouTubeAutoReplyPolicy.isParticipationQuestion("参加できます"));
        assertFalse(YouTubeAutoReplyPolicy.isParticipationQuestion("いい配信ですね"));
    }

    @Test
    void appliesGlobalAndPerAuthorCooldowns() {
        YouTubeAutoReplyPolicy policy = new YouTubeAutoReplyPolicy(Duration.ofMinutes(5), Duration.ofHours(6));
        long now = 1_000_000L;
        assertTrue(policy.shouldReply("author-a", "参加していい？", now));
        policy.recordReply("author-a", now);

        assertFalse(policy.shouldReply("author-b", "参加していい？", now + Duration.ofMinutes(4).toMillis()));
        assertFalse(policy.shouldReply("author-a", "参加していい？", now + Duration.ofHours(1).toMillis()));
        assertTrue(policy.shouldReply("author-b", "参加していい？", now + Duration.ofMinutes(6).toMillis()));
        assertTrue(policy.shouldReply("author-a", "参加していい？", now + Duration.ofHours(7).toMillis()));
    }
}
