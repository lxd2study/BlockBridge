package com.lxd.lantunnel.tunnel;

public record TunnelStatus(
        boolean running,
        boolean connected,
        int localPort,
        int publicPort,
        int activeConnections,
        long latencyMillis,
        String message,
        String relayNodeName,
        String publicAddress,
        int consecutiveFailures,
        long lastConnectedAtMillis,
        String diagnosticCode
) {
    public TunnelStatus(
            boolean running,
            boolean connected,
            int localPort,
            int publicPort,
            int activeConnections,
            long latencyMillis,
            String message
    ) {
        this(running, connected, localPort, publicPort, activeConnections, latencyMillis, message, "", "", 0, 0, "");
    }
}
