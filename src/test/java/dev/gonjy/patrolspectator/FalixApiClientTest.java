package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FalixApiClientTest {
    @Test
    void selectsOnlySingleExplicitlyLabelledNonPrimaryAllocation() {
        FalixDiagnosticsResult result = FalixApiClient.summarize(List.of(
                new FalixApiClient.Allocation(25565, true, "Minecraft", "server.example"),
                new FalixApiClient.Allocation(20063, false, "Geyser", null),
                new FalixApiClient.Allocation(20064, false, "Voice Chat", null)), "Geyser");

        assertEquals("OK", result.apiStatus());
        assertEquals(25565, result.primaryPort());
        assertEquals(20063, result.geyserPort());
        assertEquals("server.example", result.address());
    }

    @Test
    void doesNotGuessWhenGeyserLabelIsMissing() {
        FalixDiagnosticsResult result = FalixApiClient.summarize(List.of(
                new FalixApiClient.Allocation(25565, true, null, null),
                new FalixApiClient.Allocation(20063, false, null, null)), "Geyser");

        assertNull(result.geyserPort());
    }

    @Test
    void doesNotGuessWhenGeyserLabelIsAmbiguous() {
        FalixDiagnosticsResult result = FalixApiClient.summarize(List.of(
                new FalixApiClient.Allocation(25565, true, null, null),
                new FalixApiClient.Allocation(20063, false, "Geyser", null),
                new FalixApiClient.Allocation(20107, false, "geyser", null)), "Geyser");

        assertNull(result.geyserPort());
    }
}
