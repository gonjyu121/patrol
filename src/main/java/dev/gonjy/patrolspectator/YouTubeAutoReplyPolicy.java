package dev.gonjy.patrolspectator;

import java.text.Normalizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class YouTubeAutoReplyPolicy {
    private static final Pattern PARTICIPATION_QUESTION = Pattern.compile(
            ".*(参加(して)?(も)?(いい|よい|大丈夫|できます)|入って(も)?(いい|よい|大丈夫)|参加ok).*",
            Pattern.CASE_INSENSITIVE);

    private final long globalCooldownMillis;
    private final long authorCooldownMillis;
    private final Map<String, Long> lastReplyByAuthor = new HashMap<>();
    private long lastGlobalReplyAt = Long.MIN_VALUE;

    YouTubeAutoReplyPolicy(Duration globalCooldown, Duration authorCooldown) {
        globalCooldownMillis = globalCooldown.toMillis();
        authorCooldownMillis = authorCooldown.toMillis();
    }

    synchronized boolean shouldReply(String authorChannelId, String message, long nowMillis) {
        return shouldReply(authorChannelId, message, nowMillis, false);
    }

    synchronized boolean shouldReply(String authorChannelId, String message, long nowMillis,
                                     boolean replyToAnyComment) {
        if (authorChannelId == null || authorChannelId.isBlank() || message == null || message.isBlank()) return false;
        if (!replyToAnyComment && !isParticipationQuestion(message)) return false;
        if (lastGlobalReplyAt != Long.MIN_VALUE && nowMillis - lastGlobalReplyAt < globalCooldownMillis) return false;
        Long authorLastReply = lastReplyByAuthor.get(authorChannelId);
        return authorLastReply == null || nowMillis - authorLastReply >= authorCooldownMillis;
    }

    synchronized void recordReply(String authorChannelId, long nowMillis) {
        lastGlobalReplyAt = nowMillis;
        lastReplyByAuthor.put(authorChannelId, nowMillis);
    }

    static boolean isParticipationQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        boolean hasQuestionMark = message.indexOf('?') >= 0 || message.indexOf('？') >= 0;
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}！？。、・]+", "");
        boolean hasQuestionEnding = normalized.endsWith("か") || normalized.endsWith("かな");
        return (hasQuestionMark || hasQuestionEnding) && PARTICIPATION_QUESTION.matcher(normalized).matches();
    }
}
