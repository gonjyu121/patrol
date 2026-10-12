package dev.gonjy.patrolspectator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Properties;

final class YouTubeQuotaBudget {
    private static final String FILE_NAME = "youtube-usage.properties";

    private final Path storagePath;
    private final int dailyUnitLimit;
    private final int dailyReplyLimit;
    private final Clock clock;
    private LocalDate date;
    private int units;
    private int replies;
    private boolean storageHealthy = true;

    YouTubeQuotaBudget(Path pluginDataDirectory, int dailyUnitLimit, int dailyReplyLimit) {
        this(pluginDataDirectory.resolve(FILE_NAME), dailyUnitLimit, dailyReplyLimit, Clock.systemUTC());
    }

    YouTubeQuotaBudget(Path storagePath, int dailyUnitLimit, int dailyReplyLimit, Clock clock) {
        this.storagePath = storagePath;
        this.dailyUnitLimit = Math.max(1, dailyUnitLimit);
        this.dailyReplyLimit = Math.max(0, dailyReplyLimit);
        this.clock = clock;
        load();
    }

    synchronized boolean tryConsume(int requestedUnits, boolean reply) {
        rolloverIfNeeded();
        if (!storageHealthy || requestedUnits < 0) return false;
        if (units + requestedUnits > dailyUnitLimit) return false;
        if (reply && replies + 1 > dailyReplyLimit) return false;

        int previousUnits = units;
        int previousReplies = replies;
        units += requestedUnits;
        if (reply) replies++;
        if (!save()) {
            units = previousUnits;
            replies = previousReplies;
            storageHealthy = false;
            return false;
        }
        return true;
    }

    synchronized int units() {
        rolloverIfNeeded();
        return units;
    }

    private void rolloverIfNeeded() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(date)) {
            date = today;
            units = 0;
            replies = 0;
            storageHealthy = save();
        }
    }

    private void load() {
        date = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!Files.isRegularFile(storagePath)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(storagePath)) {
            properties.load(input);
            LocalDate storedDate = LocalDate.parse(properties.getProperty("date", date.toString()));
            if (storedDate.equals(date)) {
                units = Math.max(0, Integer.parseInt(properties.getProperty("units", "0")));
                replies = Math.max(0, Integer.parseInt(properties.getProperty("replies", "0")));
            }
        } catch (IOException | RuntimeException e) {
            storageHealthy = false;
        }
    }

    private boolean save() {
        try {
            Files.createDirectories(storagePath.getParent());
            Properties properties = new Properties();
            properties.setProperty("date", date.toString());
            properties.setProperty("units", Integer.toString(units));
            properties.setProperty("replies", Integer.toString(replies));
            Path temporaryPath = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temporaryPath)) {
                properties.store(output, "PatrolSpectatorPlugin YouTube quota usage (UTC)");
            }
            Files.move(temporaryPath, storagePath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
