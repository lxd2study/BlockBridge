package com.lxd.lantunnel.config;

import com.lxd.lantunnel.LanTunnelMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.StringJoiner;

public final class LanTunnelConfig {
    private static LanTunnelConfig current = defaults(null);

    private Path path;
    private boolean enabled;
    private boolean autoStart;
    private boolean allowOfflinePlayers;
    private boolean showLatencyOverlay;
    private boolean autoSelectNode;
    private String relayHost;
    private int relayControlPort;
    private List<RelayNode> relayNodes;
    private String token;
    private int requestedPublicPort;
    private int reconnectDelaySeconds;
    private int connectionTestTimeoutSeconds;

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
                config.showLatencyOverlay = parseBoolean(properties.getProperty("showLatencyOverlay"), config.showLatencyOverlay);
                config.autoSelectNode = parseBoolean(properties.getProperty("autoSelectNode"), config.autoSelectNode);
                config.relayHost = properties.getProperty("relayHost", config.relayHost).trim();
                config.relayControlPort = parseInt(properties.getProperty("relayControlPort"), config.relayControlPort);
                config.relayNodes = parseRelayNodes(properties.getProperty("relayNodes"));
                config.token = properties.getProperty("token", config.token).trim();
                config.requestedPublicPort = parseInt(properties.getProperty("requestedPublicPort"), config.requestedPublicPort);
                config.reconnectDelaySeconds = parseInt(properties.getProperty("reconnectDelaySeconds"), config.reconnectDelaySeconds);
                config.connectionTestTimeoutSeconds = parseInt(properties.getProperty("connectionTestTimeoutSeconds"), config.connectionTestTimeoutSeconds);
            } catch (IOException exception) {
                LanTunnelMod.LOGGER.warn("Failed to load LAN tunnel config, using defaults", exception);
            }
        }
        config.syncLegacyNode();
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
        config.showLatencyOverlay = true;
        config.autoSelectNode = true;
        config.relayHost = "";
        config.relayControlPort = 25566;
        config.relayNodes = new ArrayList<>();
        config.token = "";
        config.requestedPublicPort = 25565;
        config.reconnectDelaySeconds = 5;
        config.connectionTestTimeoutSeconds = 5;
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
        properties.setProperty("showLatencyOverlay", Boolean.toString(showLatencyOverlay));
        properties.setProperty("autoSelectNode", Boolean.toString(autoSelectNode));
        properties.setProperty("relayHost", relayHost);
        properties.setProperty("relayControlPort", Integer.toString(relayControlPort));
        properties.setProperty("relayNodes", formatRelayNodes(relayNodes));
        properties.setProperty("token", token);
        properties.setProperty("requestedPublicPort", Integer.toString(requestedPublicPort));
        properties.setProperty("reconnectDelaySeconds", Integer.toString(reconnectDelaySeconds));
        properties.setProperty("connectionTestTimeoutSeconds", Integer.toString(connectionTestTimeoutSeconds));
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "LAN Tunnel client configuration");
        }
    }

    public LanTunnelConfig copy() {
        LanTunnelConfig copy = new LanTunnelConfig(path);
        copy.enabled = enabled;
        copy.autoStart = autoStart;
        copy.allowOfflinePlayers = allowOfflinePlayers;
        copy.showLatencyOverlay = showLatencyOverlay;
        copy.autoSelectNode = autoSelectNode;
        copy.relayHost = relayHost;
        copy.relayControlPort = relayControlPort;
        copy.relayNodes = new ArrayList<>(relayNodes);
        copy.token = token;
        copy.requestedPublicPort = requestedPublicPort;
        copy.reconnectDelaySeconds = reconnectDelaySeconds;
        copy.connectionTestTimeoutSeconds = connectionTestTimeoutSeconds;
        return copy;
    }

    public String validate() {
        if (relayHost == null) {
            relayHost = "";
        }
        if (token == null) {
            token = "";
        }
        syncLegacyNode();
        if (enabled && relayHost.isBlank()) {
            return "必须填写中转服务器地址。";
        }
        if (!isPort(relayControlPort)) {
            return "控制端口必须在 1 到 65535 之间。";
        }
        if (requestedPublicPort != 0 && !isPort(requestedPublicPort)) {
            return "公网端口必须为 0，或在 1 到 65535 之间。";
        }
        if (enabled && token.isBlank()) {
            return "必须填写访问令牌。";
        }
        if (token.chars().anyMatch(Character::isWhitespace)) {
            return "访问令牌不能包含空格。";
        }
        if (reconnectDelaySeconds < 1 || reconnectDelaySeconds > 300) {
            return "重连间隔必须在 1 到 300 秒之间。";
        }
        if (connectionTestTimeoutSeconds < 1 || connectionTestTimeoutSeconds > 60) {
            return "连接测试超时必须在 1 到 60 秒之间。";
        }
        for (RelayNode node : relayNodes) {
            if (node.host().isBlank()) {
                return "节点地址不能为空。";
            }
            if (!isPort(node.controlPort())) {
                return "节点控制端口必须在 1 到 65535 之间。";
            }
        }
        return null;
    }

    public boolean hasSameConnectionSettings(LanTunnelConfig other) {
        return other != null
                && Objects.equals(relayHost, other.relayHost)
                && relayControlPort == other.relayControlPort
                && Objects.equals(relayNodes, other.relayNodes)
                && autoSelectNode == other.autoSelectNode
                && Objects.equals(token, other.token)
                && requestedPublicPort == other.requestedPublicPort
                && reconnectDelaySeconds == other.reconnectDelaySeconds
                && connectionTestTimeoutSeconds == other.connectionTestTimeoutSeconds;
    }

    public List<RelayNode> effectiveRelayNodes() {
        syncLegacyNode();
        return List.copyOf(relayNodes);
    }

    public RelayNode primaryRelayNode() {
        syncLegacyNode();
        return relayNodes.isEmpty()
                ? new RelayNode("default", relayHost, relayControlPort, false)
                : relayNodes.get(0);
    }

    public void setSingleRelayNode(String host, int controlPort) {
        this.relayHost = host == null ? "" : host.trim();
        this.relayControlPort = controlPort;
        this.relayNodes = new ArrayList<>();
        if (!this.relayHost.isBlank()) {
            this.relayNodes.add(new RelayNode("default", this.relayHost, this.relayControlPort, false));
        }
    }

    private void syncLegacyNode() {
        if (relayNodes == null) {
            relayNodes = new ArrayList<>();
        }
        relayNodes = new ArrayList<>(relayNodes.stream()
                .filter(node -> node != null && !node.host().isBlank())
                .toList());
        if (relayNodes.isEmpty() && relayHost != null && !relayHost.isBlank()) {
            relayNodes.add(new RelayNode("default", relayHost, relayControlPort, false));
        }
        if (!relayNodes.isEmpty()) {
            RelayNode first = relayNodes.get(0);
            relayHost = first.host();
            relayControlPort = first.controlPort();
        }
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

    private static List<RelayNode> parseRelayNodes(String raw) {
        List<RelayNode> nodes = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return nodes;
        }
        String[] entries = raw.split(";");
        for (String entry : entries) {
            String[] parts = entry.split(",", -1);
            if (parts.length < 3) {
                continue;
            }
            String name = decode(parts[0]);
            String host = decode(parts[1]);
            int port = parseInt(parts[2], -1);
            boolean official = parts.length >= 4 && Boolean.parseBoolean(parts[3].trim());
            if (!host.isBlank() && isPort(port)) {
                nodes.add(new RelayNode(name, host, port, official));
            }
        }
        return nodes;
    }

    private static String formatRelayNodes(List<RelayNode> nodes) {
        StringJoiner joiner = new StringJoiner(";");
        if (nodes != null) {
            for (RelayNode node : nodes) {
                if (node != null && !node.host().isBlank()) {
                    joiner.add(encode(node.name()) + "," + encode(node.host()) + "," + node.controlPort() + "," + node.official());
                }
            }
        }
        return joiner.toString();
    }

    private static String encode(String value) {
        return value == null ? "" : value.replace("%", "%25").replace(",", "%2C").replace(";", "%3B");
    }

    private static String decode(String value) {
        return value == null ? "" : value.replace("%3B", ";").replace("%2C", ",").replace("%25", "%").trim();
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

    public boolean isShowLatencyOverlay() {
        return showLatencyOverlay;
    }

    public void setShowLatencyOverlay(boolean showLatencyOverlay) {
        this.showLatencyOverlay = showLatencyOverlay;
    }

    public boolean isAutoSelectNode() {
        return autoSelectNode;
    }

    public void setAutoSelectNode(boolean autoSelectNode) {
        this.autoSelectNode = autoSelectNode;
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

    public int getConnectionTestTimeoutSeconds() {
        return connectionTestTimeoutSeconds;
    }

    public void setConnectionTestTimeoutSeconds(int connectionTestTimeoutSeconds) {
        this.connectionTestTimeoutSeconds = connectionTestTimeoutSeconds;
    }
}
