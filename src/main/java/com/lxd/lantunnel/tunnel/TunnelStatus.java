package com.lxd.lantunnel.tunnel;

public record TunnelStatus(
        boolean running,
        boolean connected,
        int localPort,
        int publicPort,
        int activeConnections,
        String message
) {
}
