package dev.gonjy.patrolspectator;

record FalixDiagnosticsResult(
        String apiStatus,
        String address,
        Integer primaryPort,
        Integer geyserPort) {

    static FalixDiagnosticsResult notConfigured() {
        return new FalixDiagnosticsResult("NOT CONFIGURED", null, null, null);
    }

    static FalixDiagnosticsResult unavailable(String status) {
        return new FalixDiagnosticsResult(status, null, null, null);
    }
}
