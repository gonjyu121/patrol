package dev.gonjy.patrolspectator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerDiagnosticsReportTest {
    @Test
    void healthyReportIncludesClientRestartAsSuggestionNotDiagnosis() {
        ServerDiagnosticsReport report = report("OK", 20063, 1);
        List<String> lines = report.displayLines();

        assertTrue(lines.stream().anyMatch(line -> line.contains("Overall: §aOK")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("トラブルシューティング候補")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("クライアント側が原因です")));
    }

    @Test
    void warningReportDoesNotClaimServerSideIsHealthy() {
        ServerDiagnosticsReport report = report("WARNING", null, null);
        List<String> lines = report.displayLines();

        assertTrue(lines.stream().anyMatch(line -> line.contains("Overall: §eWARNING")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("サーバー側の診断項目は正常")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("External Connection Test: §7NOT AVAILABLE")));
    }

    private ServerDiagnosticsReport report(String overall, Integer port, Integer players) {
        return new ServerDiagnosticsReport("OK", 25565, 3, List.of("world"), "1.9.136",
                true, "OtouGame", true, overall, "2.11.3", port,
                "OK", "2.2.5", players, overall);
    }
}
