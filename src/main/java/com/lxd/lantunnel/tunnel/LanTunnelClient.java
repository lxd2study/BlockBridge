package com.lxd.lantunnel.tunnel;

import com.lxd.lantunnel.LanTunnelMod;
import com.lxd.lantunnel.config.LanTunnelConfig;
import com.lxd.lantunnel.config.RelayNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class LanTunnelClient implements Runnable {
    private static final String CLIENT_VERSION = "0.2";
    private static final long PING_INTERVAL_MILLIS = 5_000L;
    private static final long PING_TIMEOUT_MILLIS = 10_000L;

    private final LanTunnelConfig config;
    private final int localPort;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final Object controlWriteLock = new Object();

    private volatile Thread thread;
    private volatile Socket controlSocket;
    private volatile boolean connected;
    private volatile int publicPort = -1;
    private volatile long latencyMillis = -1;
    private volatile long lastPingSentAt;
    private volatile long lastPingToken;
    private volatile long nextPingAt;
    private volatile boolean legacyPingMode;
    private volatile int consecutiveFailures;
    private volatile long lastConnectedAtMillis;
    private volatile String diagnosticCode = "STARTING";
    private volatile RelayNode activeNode;
    private volatile String publicAddress = "";
    private volatile String message = "正在启动穿透。";

    public LanTunnelClient(LanTunnelConfig config, int localPort) {
        this.config = config.copy();
        this.localPort = localPort;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, "lan-tunnel-control");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void stop() {
        running.set(false);
        TunnelIo.closeQuietly(controlSocket);
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean matches(int lanPort, LanTunnelConfig otherConfig) {
        return running.get() && localPort == lanPort && config.hasSameConnectionSettings(otherConfig);
    }

    public TunnelStatus getStatus() {
        RelayNode node = activeNode != null ? activeNode : config.primaryRelayNode();
        return new TunnelStatus(
                running.get(),
                connected,
                localPort,
                publicPort,
                activeConnections.get(),
                latencyMillis,
                message,
                node.displayName(),
                publicAddress,
                consecutiveFailures,
                lastConnectedAtMillis,
                diagnosticCode
        );
    }

    public static ConnectionTestResult testConnection(LanTunnelConfig config) {
        LanTunnelConfig copy = config.copy();
        String validationError = copy.validate();
        if (validationError != null) {
            return ConnectionTestResult.failure(validationError, "CONFIG_INVALID");
        }
        RelayNode node = selectBestNode(copy, true);
        if (node == null || node.host().isBlank()) {
            return ConnectionTestResult.failure("没有可用的中转节点。", "NO_NODE");
        }

        long startedAt = System.currentTimeMillis();
        try (Socket socket = connectRelaySocket(node, copy.getConnectionTestTimeoutSeconds() * 1000)) {
            int timeoutMillis = copy.getConnectionTestTimeoutSeconds() * 1000;
            socket.setSoTimeout(timeoutMillis);
            if (!negotiateHelloForTest(socket)) {
                return testLegacyConnection(copy, node, startedAt);
            }
            Protocol.writeLine(socket.getOutputStream(), "TEST " + copy.getToken() + " " + CLIENT_VERSION);
            String response = Protocol.readLine(socket.getInputStream(), 4096);
            if (response == null) {
                return ConnectionTestResult.failure("节点未返回测试结果。", "NODE_CLOSED");
            }
            if (response.startsWith("OK ")) {
                return ConnectionTestResult.success(System.currentTimeMillis() - startedAt,
                        "连接测试成功：" + node.displayName() + "，" + (System.currentTimeMillis() - startedAt) + " ms。");
            }
            return ConnectionTestResult.failure("连接测试失败：" + humanRelayError(response), parseErrorCode(response));
        } catch (SocketTimeoutException exception) {
            return ConnectionTestResult.failure("连接测试超时。", "NODE_TIMEOUT");
        } catch (IOException exception) {
            return ConnectionTestResult.failure("连接测试失败：" + exception.getMessage(), "NODE_UNREACHABLE");
        }
    }

    private static ConnectionTestResult testLegacyConnection(LanTunnelConfig config, RelayNode node, long startedAt) {
        try (Socket socket = connectRelaySocket(node, config.getConnectionTestTimeoutSeconds() * 1000)) {
            Protocol.writeLine(socket.getOutputStream(), "TEST " + config.getToken() + " " + CLIENT_VERSION);
            String response = Protocol.readLine(socket.getInputStream(), 4096);
            if (response == null) {
                return ConnectionTestResult.failure("节点不支持连接测试，请升级中转站。", "PROTOCOL_UNSUPPORTED");
            }
            if (response.startsWith("OK ")) {
                return ConnectionTestResult.success(System.currentTimeMillis() - startedAt,
                        "连接测试成功：" + node.displayName() + "，" + (System.currentTimeMillis() - startedAt) + " ms。");
            }
            return ConnectionTestResult.failure("连接测试失败：" + humanRelayError(response), parseErrorCode(response));
        } catch (SocketTimeoutException exception) {
            return ConnectionTestResult.failure("连接测试超时。", "NODE_TIMEOUT");
        } catch (IOException exception) {
            return ConnectionTestResult.failure("连接测试失败：" + exception.getMessage(), "NODE_UNREACHABLE");
        }
    }

    private static boolean negotiateHelloForTest(Socket socket) throws IOException {
        Protocol.writeLine(socket.getOutputStream(), "HELLO " + CLIENT_VERSION);
        String response = Protocol.readLine(socket.getInputStream(), 4096);
        if (response == null) {
            return false;
        }
        if (response.startsWith("OK ")) {
            return true;
        }
        if (response.startsWith("ERR BAD_REQUEST")) {
            return false;
        }
        throw new IOException(response);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                connectControl();
            } catch (IOException exception) {
                if (running.get()) {
                    consecutiveFailures++;
                    message = "中转连接失败：" + humanRelayError(exception.getMessage());
                    if (diagnosticCode.isBlank() || "CONNECTED".equals(diagnosticCode)) {
                        diagnosticCode = classifyException(exception);
                    }
                    LanTunnelMod.LOGGER.warn("LAN tunnel relay connection failed", exception);
                }
            } finally {
                connected = false;
                publicPort = -1;
                latencyMillis = -1;
                lastPingSentAt = 0;
                lastPingToken = 0;
                nextPingAt = 0;
                legacyPingMode = false;
                publicAddress = "";
                TunnelIo.closeQuietly(controlSocket);
                controlSocket = null;
            }
            if (running.get()) {
                sleepBeforeReconnect();
            }
        }
        message = "已停止。";
        diagnosticCode = "STOPPED";
    }

    private void connectControl() throws IOException {
        RelayNode node = selectBestNode(config, false);
        if (node == null || node.host().isBlank()) {
            diagnosticCode = "NO_NODE";
            throw new IOException("没有可用的中转节点");
        }
        activeNode = node;
        message = "正在连接中转节点：" + node.displayName();
        diagnosticCode = "CONNECTING";
        Socket socket = connectRelaySocket(node, 10_000);
        if (!negotiateHello(socket)) {
            TunnelIo.closeQuietly(socket);
            socket = connectRelaySocket(node, 10_000);
        }
        controlSocket = socket;

        Protocol.writeLine(socket.getOutputStream(), "HOST " + config.getToken() + " " + config.getRequestedPublicPort() + " " + CLIENT_VERSION);
        String response = Protocol.readLine(socket.getInputStream(), 4096);
        if (response == null) {
            diagnosticCode = "NODE_CLOSED";
            throw new IOException("节点在握手阶段断开");
        }
        if (!response.startsWith("OK ")) {
            diagnosticCode = parseErrorCode(response);
            throw new IOException(response);
        }
        publicPort = parsePublicPort(response);
        publicAddress = node.host() + ":" + publicPort;
        connected = true;
        consecutiveFailures = 0;
        lastConnectedAtMillis = System.currentTimeMillis();
        latencyMillis = -1;
        lastPingSentAt = 0;
        lastPingToken = 0;
        legacyPingMode = false;
        nextPingAt = System.currentTimeMillis();
        diagnosticCode = "CONNECTED";
        message = "已发布，好友可通过中转地址加入。";
        startLatencyPinger(socket);

        while (running.get()) {
            String line = Protocol.readLine(socket.getInputStream(), 4096);
            if (line == null) {
                diagnosticCode = "NODE_CLOSED";
                throw new SocketException("节点关闭了控制连接");
            }
            if (line.startsWith("OPEN ")) {
                openDataConnection(line.substring(5).trim());
            } else if (line.startsWith("ERR ")) {
                diagnosticCode = parseErrorCode(line);
                message = humanRelayError(line);
            } else if (line.startsWith("PONG")) {
                recordPong(line);
            }
        }
    }

    private static Socket connectRelaySocket(RelayNode node, int timeoutMillis) throws IOException {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(node.host(), node.controlPort()), timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        return socket;
    }

    private boolean negotiateHello(Socket socket) throws IOException {
        Protocol.writeLine(socket.getOutputStream(), "HELLO " + CLIENT_VERSION);
        String response = Protocol.readLine(socket.getInputStream(), 4096);
        if (response == null) {
            return false;
        }
        if (response.startsWith("OK ")) {
            socket.setSoTimeout(0);
            return true;
        }
        if (response.startsWith("ERR BAD_REQUEST")) {
            return false;
        }
        diagnosticCode = parseErrorCode(response);
        throw new IOException(response);
    }

    private void startLatencyPinger(Socket socket) {
        Thread pingThread = new Thread(() -> runLatencyPinger(socket), "lan-tunnel-ping");
        pingThread.setDaemon(true);
        pingThread.start();
    }

    private void runLatencyPinger(Socket socket) {
        while (running.get() && connected && controlSocket == socket) {
            try {
                sendPingIfDue(socket);
                Thread.sleep(500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException exception) {
                if (running.get() && connected) {
                    LanTunnelMod.LOGGER.debug("LAN tunnel latency ping failed", exception);
                }
                return;
            }
        }
    }

    private void sendPingIfDue(Socket socket) throws IOException {
        long now = System.currentTimeMillis();
        if (lastPingSentAt > 0) {
            if (now - lastPingSentAt <= PING_TIMEOUT_MILLIS) {
                return;
            }
            latencyMillis = -1;
            lastPingSentAt = 0;
            lastPingToken = 0;
            legacyPingMode = true;
            if (connected) {
                diagnosticCode = "PING_TIMEOUT";
                message = "中转心跳超时，等待下一次响应。";
            }
        }
        if (now < nextPingAt) {
            return;
        }
        long token = now;
        synchronized (controlWriteLock) {
            Protocol.writeLine(socket.getOutputStream(), legacyPingMode ? "PING" : "PING " + token);
        }
        lastPingSentAt = now;
        lastPingToken = legacyPingMode ? 0 : token;
        nextPingAt = now + PING_INTERVAL_MILLIS;
    }

    private void recordPong(String line) {
        if (lastPingSentAt <= 0) {
            return;
        }
        String[] parts = line.split(" ", 2);
        if (parts.length == 2) {
            try {
                long token = Long.parseLong(parts[1].trim());
                if (lastPingToken > 0 && token != lastPingToken) {
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        latencyMillis = Math.max(0, System.currentTimeMillis() - lastPingSentAt);
        lastPingSentAt = 0;
        lastPingToken = 0;
        if (connected) {
            diagnosticCode = "CONNECTED";
            message = "已发布，好友可通过中转地址加入。";
        }
    }

    private int parsePublicPort(String response) throws IOException {
        String[] parts = response.split(" ", 3);
        if (parts.length < 2) {
            throw new IOException("bad relay response: " + response);
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw new IOException("bad public port in relay response: " + response, exception);
        }
    }

    private void openDataConnection(String id) {
        if (id.isEmpty()) {
            return;
        }
        Thread dataThread = new Thread(() -> runDataConnection(id), "lan-tunnel-data-" + id);
        dataThread.setDaemon(true);
        dataThread.start();
    }

    private void runDataConnection(String id) {
        activeConnections.incrementAndGet();
        Socket localSocket = null;
        Socket relaySocket = null;
        try {
            localSocket = new Socket(InetAddress.getLoopbackAddress(), localPort);
            localSocket.setTcpNoDelay(true);

            relaySocket = new Socket();
            relaySocket.setTcpNoDelay(true);
            RelayNode node = activeNode != null ? activeNode : config.primaryRelayNode();
            relaySocket.connect(new InetSocketAddress(node.host(), node.controlPort()), 10_000);
            Protocol.writeLine(relaySocket.getOutputStream(), "DATA " + config.getToken() + " " + id);

            TunnelIo.bridge(relaySocket, localSocket);
        } catch (IOException exception) {
            if (running.get()) {
                diagnosticCode = "DATA_FAILED";
                message = "数据连接失败：" + exception.getMessage();
                LanTunnelMod.LOGGER.warn("LAN tunnel data connection failed", exception);
            }
        } finally {
            TunnelIo.closeQuietly(relaySocket);
            TunnelIo.closeQuietly(localSocket);
            activeConnections.decrementAndGet();
        }
    }

    private static RelayNode selectBestNode(LanTunnelConfig config, boolean testOnly) {
        List<RelayNode> nodes = config.effectiveRelayNodes();
        if (nodes.isEmpty()) {
            return config.primaryRelayNode();
        }
        if (!config.isAutoSelectNode() || nodes.size() == 1) {
            return nodes.get(0);
        }
        RelayNode best = null;
        long bestLatency = Long.MAX_VALUE;
        int timeoutMillis = Math.max(1000, config.getConnectionTestTimeoutSeconds() * 1000);
        for (RelayNode node : nodes) {
            long startedAt = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(node.host(), node.controlPort()), timeoutMillis);
                long latency = System.currentTimeMillis() - startedAt;
                if (latency < bestLatency) {
                    bestLatency = latency;
                    best = node;
                }
            } catch (IOException exception) {
                if (testOnly) {
                    LanTunnelMod.LOGGER.debug("Relay node test failed for {}", node.host(), exception);
                }
            }
        }
        return best != null ? best : nodes.get(0);
    }

    private static String parseErrorCode(String response) {
        if (response == null || response.isBlank()) {
            return "UNKNOWN";
        }
        String[] parts = response.split(" ", 3);
        if (parts.length >= 2 && "ERR".equals(parts[0]) && parts[1].chars().allMatch(ch -> Character.isUpperCase(ch) || ch == '_')) {
            return parts[1];
        }
        if (response.toLowerCase().contains("token")) {
            return "TOKEN_REJECTED";
        }
        if (response.toLowerCase().contains("bind") || response.toLowerCase().contains("port")) {
            return "PORT_BIND_FAILED";
        }
        return "RELAY_ERROR";
    }

    private static String humanRelayError(String response) {
        if (response == null || response.isBlank()) {
            return "未知错误";
        }
        String[] parts = response.split(" ", 3);
        if (parts.length >= 3 && "ERR".equals(parts[0])) {
            return switch (parts[1]) {
                case "TOKEN_REJECTED" -> "Token 错误或已停用";
                case "PORT_BIND_FAILED" -> "公网端口被占用或不在允许范围";
                case "LIMIT_EXCEEDED" -> "节点连接数达到上限";
                case "BAD_REQUEST" -> "协议请求格式错误";
                case "NODE_UNAVAILABLE" -> "节点当前不可用";
                default -> parts[2];
            };
        }
        if (response.startsWith("ERR ")) {
            return response.substring(4);
        }
        return response;
    }

    private static String classifyException(IOException exception) {
        if (exception instanceof SocketTimeoutException) {
            return "NODE_TIMEOUT";
        }
        String message = exception.getMessage();
        if (message == null) {
            return "NETWORK_ERROR";
        }
        if (message.contains("Connection refused")) {
            return "NODE_UNREACHABLE";
        }
        if (message.contains("Address already in use")) {
            return "PORT_BIND_FAILED";
        }
        return "NETWORK_ERROR";
    }

    private void sleepBeforeReconnect() {
        try {
            Thread.sleep(config.getReconnectDelaySeconds() * 1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
