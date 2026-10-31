package dev.gonjy.patrolspectator;

/** Builds a public, coordinate-free connection guide for YouTube chat. */
final class YouTubeConnectionGuide {
    private final String javaAddress;
    private final String bedrockAddress;
    private final Integer bedrockPort;

    YouTubeConnectionGuide(String javaAddress, String bedrockAddress, Integer bedrockPort) {
        this.javaAddress = validAddress(javaAddress);
        this.bedrockAddress = validAddress(bedrockAddress);
        this.bedrockPort = bedrockPort != null && bedrockPort >= 1 && bedrockPort <= 65_535
                ? bedrockPort : null;
    }

    String appendTo(String message) {
        String base = message == null ? "" : message.trim();
        String guide = text();
        if (guide.isBlank()) return base;
        if (base.isBlank()) return guide;
        return base + " " + guide;
    }

    String text() {
        StringBuilder guide = new StringBuilder();
        if (javaAddress != null) {
            guide.append("Java版: ").append(javaAddress);
        }
        if (bedrockAddress != null && bedrockPort != null) {
            if (!guide.isEmpty()) guide.append(" / ");
            guide.append("BE版: ").append(bedrockAddress).append(':').append(bedrockPort);
        }
        return guide.toString();
    }

    YouTubeConnectionGuide withFalix(FalixDiagnosticsResult result) {
        if (result == null || !"OK".equals(result.apiStatus())) return this;
        return new YouTubeConnectionGuide(javaAddress, result.address(), result.geyserPort());
    }

    private static String validAddress(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.matches("(?i)[a-z0-9.-]+(?::[0-9]{1,5})?") ? trimmed : null;
    }
}
