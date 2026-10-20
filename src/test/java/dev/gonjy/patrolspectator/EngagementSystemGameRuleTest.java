package dev.gonjy.patrolspectator;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EngagementSystemGameRuleTest {

    @Test
    void showCoordinatesDoesNotUseJavaCommandFallback() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/dev/gonjy/patrolspectator/EngagementSystem.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("setDynamicRuleWithFallback(world, new String[] { \"show_coordinates\""));
        assertFalse(source.contains("setDynamicRuleWithFallbackQuiet(world, new String[] { \"show_coordinates\""));
    }
}
