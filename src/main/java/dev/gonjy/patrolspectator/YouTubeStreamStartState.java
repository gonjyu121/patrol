package dev.gonjy.patrolspectator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Persists which YouTube broadcast already received its one-time welcome message. */
final class YouTubeStreamStartState {
    private static final String KEY = "last_announced_video_id";

    private final Path stateFile;
    private String lastAnnouncedVideoId;

    YouTubeStreamStartState(Path dataFolder) {
        stateFile = dataFolder.resolve("youtube-stream-start.properties");
        lastAnnouncedVideoId = load();
    }

    synchronized boolean needsAnnouncement(String videoId) {
        return videoId != null && !videoId.isBlank() && !videoId.equals(lastAnnouncedVideoId);
    }

    synchronized void markAnnounced(String videoId) throws IOException {
        if (videoId == null || videoId.isBlank()) return;
        lastAnnouncedVideoId = videoId;
        Files.createDirectories(stateFile.getParent());
        Properties properties = new Properties();
        properties.setProperty(KEY, videoId);
        Path temporary = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "PatrolSpectatorPlugin YouTube stream-start announcement state");
        }
        try {
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String load() {
        if (!Files.isRegularFile(stateFile)) return null;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(stateFile)) {
            properties.load(input);
            String value = properties.getProperty(KEY);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (IOException ignored) {
            return null;
        }
    }
}
