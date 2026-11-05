package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageUtilsTitleMigrationTest {
    @Test
    void usesAdventureTitleAndPreservesLegacyInputFormatting() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/gonjy/patrolspectator/MessageUtils.java"));

        assertTrue(source.contains("player.showTitle(Title.title("));
        assertTrue(source.contains("Title.Times.times("));
        assertTrue(source.contains("ticksToDuration(titleConf.fadeIn)"));
        assertTrue(source.contains("LEGACY_SECTION.deserialize(title)"));
        assertTrue(source.contains("Component.text(name).decorate(TextDecoration.BOLD)"));
        assertFalse(source.contains("p.sendTitle("));
        assertFalse(source.contains("@SuppressWarnings(\"deprecation\")"));
    }
}
