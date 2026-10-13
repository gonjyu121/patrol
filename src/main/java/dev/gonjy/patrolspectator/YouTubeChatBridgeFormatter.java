package dev.gonjy.patrolspectator;

final class YouTubeChatBridgeFormatter {
    private static final String MINECRAFT_PREFIX = "[Minecraft]";
    private static final String PATROL_PREFIX = "[Patrol]";

    private YouTubeChatBridgeFormatter() {}

    static String toYouTube(String playerName, String message) {
        String safeName = sanitize(playerName, 32);
        String safeMessage = sanitize(message, 150);
        if (safeName.isBlank() || safeMessage.isBlank()) return null;
        return MINECRAFT_PREFIX + " " + safeName + ": " + safeMessage;
    }

    static String toMinecraft(String authorName, String message) {
        String safeName = sanitize(authorName, 32);
        String safeMessage = sanitize(message, 180);
        if (safeName.isBlank() || safeMessage.isBlank()) return null;
        return "§c[YouTube] §f" + safeName + "§7: §f" + safeMessage;
    }

    static boolean isPluginGenerated(String message, boolean chatOwner) {
        if (!chatOwner || message == null) return false;
        String trimmed = message.stripLeading();
        return trimmed.startsWith(MINECRAFT_PREFIX) || trimmed.startsWith(PATROL_PREFIX);
    }

    static String autoReply(String authorName, String text) {
        String safeName = sanitize(authorName, 40);
        return PATROL_PREFIX + (safeName.isBlank() ? " " : " @" + safeName + " ") + text;
    }

    static String announcement(String text) {
        String safeText = sanitize(text, 180);
        return safeText.isBlank() ? null : PATROL_PREFIX + " " + safeText;
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return "";
        String sanitized = value.replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .replaceAll("[\\p{Cntrl}\\r\\n]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }
}
