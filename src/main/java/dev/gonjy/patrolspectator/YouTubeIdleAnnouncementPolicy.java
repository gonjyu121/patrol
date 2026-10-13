package dev.gonjy.patrolspectator;

import java.time.Duration;

/** Tracks human chat activity so automated guidance is only sent during genuine silence. */
final class YouTubeIdleAnnouncementPolicy {
    private final long idleIntervalMillis;
    private long lastActivityAt;

    YouTubeIdleAnnouncementPolicy(Duration idleInterval, long startedAtMillis) {
        idleIntervalMillis = Math.max(1L, idleInterval.toMillis());
        lastActivityAt = startedAtMillis;
    }

    synchronized void recordActivity(long nowMillis) {
        lastActivityAt = nowMillis;
    }

    synchronized boolean shouldAnnounce(long nowMillis) {
        return nowMillis >= lastActivityAt && nowMillis - lastActivityAt >= idleIntervalMillis;
    }

    synchronized void recordAnnouncement(long nowMillis) {
        lastActivityAt = nowMillis;
    }
}
