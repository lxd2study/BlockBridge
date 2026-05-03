package com.lxd.lantunnel.config;

public record RelayNode(String name, String host, int controlPort, boolean official) {
    public RelayNode {
        name = name == null || name.isBlank() ? "default" : name.trim();
        host = host == null ? "" : host.trim();
    }

    public String displayName() {
        return name.isBlank() ? host : name;
    }
}
