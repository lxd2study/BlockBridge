package com.lxd.lantunnel.config;

import com.lxd.lantunnel.LanTunnelMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

public final class LanTunnelConfig {
    private static LanTunnelConfig current = defaults(null);

    private Path path;
    private boolean enabled;
    private boolean autoStart;
    private boolean allowOfflinePlayers;
    private String relayHost;
    private int relayControlPort;
    private String token;
    private int requestedPublicPort;
    private int reconnectDelaySeconds;

    private LanTunnelConfig(Path path) {
        this.path = path;
    }

    public static synchronized void load(Path path) {
        LanTunnelConfig config = defaults(path);
        if (Files.isRegularFile(path)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
                config.enabled = parseBoolean(properties.getProperty("enabled"), config.enabled);
                config.autoStart = parseBoolean(properties.getProperty("autoStart"), config.autoStart);
                config.allowOfflinePlayers = parseBoolean(properties.getProperty("allowOfflinePlayers"), config.allowOfflinePlayers);
                config.relayHost = properties.getProperty("relayHost", config.relayHost).trim();
                config.relayControlPort = parseInt(properties.getProperty("relayControlPort"), config.relayControlPort);
                config.token = properties.getProperty("token", config.token).trim();
                config.requestedPublicPort = parseInt(properties.getProperty("requestedPublicPort"), config.requestedPublicPort);
                config.reconnectDelaySeconds = parseInt(properties.getProperty("reconnectDelaySeconds"), config.reconnectDelaySeconds);
            } catch (IOException exception) {
                LanTunnelMod.LOGGER.warn("Failed to load LAN tunnel config, using defaults", exception);
            }
        }
        current = config;
    }

    public static synchronized LanTunnelConfig get() {
        return current;
    }

    public static synchronized void replace(LanTunnelConfig next) {
        Path path = current.path;
        current = next.copy();
        current.path = path;
    }

    private static LanTunnelConfig defaults(Path path) {
        LanTunnelConfig config = new LanTunnelConfig(path);
        config.enabled = false;
        config.autoStart = true;
        config.allowOfflinePlayers = false;
        config.relayHost = "";
        config.relayControlPort = 25566;
        config.token = "";
        config.requestedPublicPort = 25565;
        config.reconnectDelaySeconds = 5;
        return config;
    }

    public synchronized void save() throws IOException {
        if (path == null) {
            return;
        }
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("autoStart", Boolean.toString(autoStart));
        properties.setProperty("allowOfflinePlayers", Boolean.toString(allowOfflinePlayers));
        properties.setProperty("relayHost", relayHost);
        properties.setProperty("relayControlPort", Integer.toString(relayControlPort));
        properties.setProperty("token", token);
        properties.setProperty("requestedPublicPort", Integer.toString(requestedPublicPort));
        properties.setProperty("reconnectDelaySeconds", Integer.toString(reconnectDelaySeconds));
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "LAN Tunnel client configuration");
        }
    }

    public LanTunnelConfig copy() {
        LanTunnelConfig copy = new LanTunnelConfig(path);
        copy.enabled = enabled;
        copy.autoStart = autoStart;
        copy.allowOfflinePlayers = allowOfflinePlayers;
        copy.relayHost = relayHost;
        copy.relayControlPort = relayControlPort;
        copy.token = token;
        copy.requestedPublicPort = requestedPublicPort;
        copy.reconnectDelaySeconds = reconnectDelaySeconds;
        return copy;
    }

    public String validate() {
        if (relayHost == null) {
            relayHost = "";
        }
        if (token == null) {
            token = "";
        }
        if (enabled && relayHost.isBlank()) {
            return "Relay host is required.";
        }
        if (!isPort(relayControlPort)) {
            return "Relay control port must be between 1 and 65535.";
        }
        if (requestedPublicPort != 0 && !isPort(requestedPublicPort)) {
            return "Public port must be 0 or between 1 and 65535.";
        }
        if (enabled && token.isBlank()) {
            return "Relay token is required.";
        }
        if (token.chars().anyMatch(Character::isWhitespace)) {
            return "Relay token cannot contain whitespace.";
        }
        if (reconnectDelaySeconds < 1 || reconnectDelaySeconds > 300) {
            return "Reconnect delay must be between 1 and 300 seconds.";
        }
        return null;
    }

    public boolean hasSameConnectionSettings(LanTunnelConfig other) {
        return other != null
                && Objects.equals(relayHost, other.relayHost)
                && relayControlPort == other.relayControlPort
                && Objects.equals(token, other.token)
                && requestedPublicPort == other.requestedPublicPort
                && reconnectDelaySeconds == other.reconnectDelaySeconds;
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean isPort(int port) {
        return port >= 1 && port <= 65535;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isAllowOfflinePlayers() {
        return allowOfflinePlayers;
    }

    public void setAllowOfflinePlayers(boolean allowOfflinePlayers) {
        this.allowOfflinePlayers = allowOfflinePlayers;
    }

    public String getRelayHost() {
        return relayHost;
    }

    public void setRelayHost(String relayHost) {
        this.relayHost = relayHost;
    }

    public int getRelayControlPort() {
        return relayControlPort;
    }

    public void setRelayControlPort(int relayControlPort) {
        this.relayControlPort = relayControlPort;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getRequestedPublicPort() {
        return requestedPublicPort;
    }

    public void setRequestedPublicPort(int requestedPublicPort) {
        this.requestedPublicPort = requestedPublicPort;
    }

    public int getReconnectDelaySeconds() {
        return reconnectDelaySeconds;
    }

    public void setReconnectDelaySeconds(int reconnectDelaySeconds) {
        this.reconnectDelaySeconds = reconnectDelaySeconds;
    }
}
