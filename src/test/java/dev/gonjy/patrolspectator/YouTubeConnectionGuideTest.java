package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YouTubeConnectionGuideTest {
    @Test
    void appendsJavaAndBedrockPublicEndpoints() {
        YouTubeConnectionGuide guide = new YouTubeConnectionGuide(
                "play.example.net", "bedrock.example.net", 19132);

        assertEquals("参加自由です！ Java版: play.example.net / BE版: bedrock.example.net:19132",
                guide.appendTo("参加自由です！"));
    }

    @Test
    void fallsBackToJavaWhenFalixDataIsUnavailable() {
        YouTubeConnectionGuide guide = new YouTubeConnectionGuide("play.example.net", null, null)
                .withFalix(FalixDiagnosticsResult.unavailable("UNAVAILABLE"));

        assertEquals("Java版: play.example.net", guide.text());
    }

    @Test
    void addsBedrockEndpointFromFalixDiagnostics() {
        YouTubeConnectionGuide guide = new YouTubeConnectionGuide("play.example.net", null, null)
                .withFalix(new FalixDiagnosticsResult("OK", "node.example.net", 25565, 20063));

        assertEquals("Java版: play.example.net / BE版: node.example.net:20063", guide.text());
    }

    @Test
    void rejectsUnsafeAddressesAndInvalidPorts() {
        YouTubeConnectionGuide guide = new YouTubeConnectionGuide(
                "https://private.example/path", "node.example.net\nsecret", 70000);

        assertEquals("案内", guide.appendTo("案内"));
    }
}
