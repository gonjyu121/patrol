package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeQuotaBudgetTest {
    @TempDir
    Path tempDirectory;

    @Test
    void persistsLimitsAcrossRestart() {
        Path usageFile = tempDirectory.resolve("usage.properties");
        Clock clock = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);
        YouTubeQuotaBudget first = new YouTubeQuotaBudget(usageFile, 25, 1, clock);
        assertTrue(first.tryConsume(5, false));
        assertTrue(first.tryConsume(20, true));
        assertFalse(first.tryConsume(1, false));

        YouTubeQuotaBudget restarted = new YouTubeQuotaBudget(usageFile, 25, 1, clock);
        assertEquals(25, restarted.units());
        assertFalse(restarted.tryConsume(20, true));
    }

    @Test
    void resetsOnNextUtcDay() {
        Path usageFile = tempDirectory.resolve("usage.properties");
        YouTubeQuotaBudget first = new YouTubeQuotaBudget(usageFile, 5, 1,
                Clock.fixed(Instant.parse("2026-10-12T23:59:00Z"), ZoneOffset.UTC));
        assertTrue(first.tryConsume(5, false));

        YouTubeQuotaBudget nextDay = new YouTubeQuotaBudget(usageFile, 5, 1,
                Clock.fixed(Instant.parse("2026-10-13T00:01:00Z"), ZoneOffset.UTC));
        assertEquals(0, nextDay.units());
        assertTrue(nextDay.tryConsume(5, false));
    }
}
