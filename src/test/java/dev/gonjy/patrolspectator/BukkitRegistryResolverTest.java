package dev.gonjy.patrolspectator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BukkitRegistryResolverTest {
    @Test
    void normalizesLegacyAndModernBukkitNames() {
        assertEquals("do_daylight_cycle", BukkitRegistryResolver.normalize("doDaylightCycle"));
        assertEquals("minecraft:keep_inventory", BukkitRegistryResolver.normalize("minecraft:keep_inventory"));
        assertEquals("ui_toast_challenge_complete", BukkitRegistryResolver.normalize("UI_TOAST_CHALLENGE_COMPLETE"));
        assertEquals("generic_attack_damage", BukkitRegistryResolver.normalize("GENERIC_ATTACK_DAMAGE"));
    }

    @Test
    void migratedSourcesDoNotUseDeprecatedNameLookups() throws Exception {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        assertFalse(source.indexOf("GameRule.getByName(") >= 0);
        assertFalse(source.indexOf("Sound.valueOf(") >= 0);
        assertFalse(source.indexOf("Attribute.valueOf(") >= 0);
        assertFalse(source.indexOf("Registry.STRUCTURE") >= 0);
    }
}
