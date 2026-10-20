package dev.gonjy.patrolspectator.dungeon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DungeonSpawnEventMigrationTest {

    @Test
    void listenerUsesCurrentPaperSpawnEvent() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/dev/gonjy/patrolspectator/dungeon/DungeonListener.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("PlayerSetSpawnEvent event"));
        assertTrue(source.contains("event.getLocation()"));
        assertFalse(source.contains("PlayerSpawnChangeEvent"));
        assertFalse(source.contains("event.getNewSpawn()"));
    }
}
