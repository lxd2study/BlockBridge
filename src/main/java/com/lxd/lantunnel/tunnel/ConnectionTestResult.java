package com.lxd.lantunnel.tunnel;

public record ConnectionTestResult(boolean success, long latencyMillis, String message, String diagnosticCode) {
    public static ConnectionTestResult success(long latencyMillis, String message) {
        return new ConnectionTestResult(true, latencyMillis, message, "");
    }

    public static ConnectionTestResult failure(String message, String diagnosticCode) {
        return new ConnectionTestResult(false, -1, message, diagnosticCode);
    }
}
