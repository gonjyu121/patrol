package dev.gonjy.patrolspectator;

import java.util.Locale;
import org.bukkit.GameRule;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;

/** Paper 26.3のRegistry APIでBukkit要素を安全に名前解決する。 */
public final class BukkitRegistryResolver {
    private BukkitRegistryResolver() {
    }

    public static GameRule<?> gameRule(String name) {
        return resolve(Registry.GAME_RULE, name, false);
    }

    public static Sound sound(String name) {
        return resolve(Registry.SOUNDS, name, false);
    }

    public static Attribute attribute(String name) {
        return resolve(Registry.ATTRIBUTE, name, true);
    }

    private static <T extends Keyed> T resolve(Registry<T> registry, String name, boolean stripLegacyGeneric) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String normalized = normalize(name);
        T value = lookup(registry, normalized);
        if (value == null && stripLegacyGeneric && normalized.startsWith("generic_")) {
            value = lookup(registry, normalized.substring("generic_".length()));
        }
        return value;
    }

    private static <T extends Keyed> T lookup(Registry<T> registry, String normalized) {
        NamespacedKey key = normalized.indexOf(':') >= 0
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        return key == null ? null : registry.get(key);
    }

    static String normalize(String name) {
        String trimmed = name.trim();
        int namespaceSeparator = trimmed.indexOf(':');
        String namespace = namespaceSeparator >= 0 ? trimmed.substring(0, namespaceSeparator + 1) : "";
        String value = namespaceSeparator >= 0 ? trimmed.substring(namespaceSeparator + 1) : trimmed;
        value = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
        return namespace.toLowerCase(Locale.ROOT) + value;
    }
}
