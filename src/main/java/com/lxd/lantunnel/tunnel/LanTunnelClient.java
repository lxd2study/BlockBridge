package com.lxd.lantunnel.tunnel;

import com.lxd.lantunnel.LanTunnelMod;
import com.lxd.lantunnel.config.LanTunnelConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class LanTunnelClient implements Runnable {
    private final LanTunnelConfig config;
    private final int localPort;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger activeConnections = new AtomicInteger();

    private volatile Thread thread;
    private volatile Socket controlSocket;
    private volatile boolean connected;
    private volatile int publicPort = -1;
    private volatile String message = "Starting tunnel.";

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
        return new TunnelStatus(running.get(), connected, localPort, publicPort, activeConnections.get(), message);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                connectControl();
            } catch (IOException exception) {
                if (running.get()) {
                    message = "Relay connection failed: " + exception.getMessage();
                    LanTunnelMod.LOGGER.warn("LAN tunnel relay connection failed", exception);
                }
            } finally {
                connected = false;
                publicPort = -1;
                TunnelIo.closeQuietly(controlSocket);
                controlSocket = null;
            }
            if (running.get()) {
                sleepBeforeReconnect();
            }
        }
        message = "Stopped.";
    }

    private void connectControl() throws IOException {
        message = "Connecting to relay.";
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(config.getRelayHost(), config.getRelayControlPort()), 10_000);
        controlSocket = socket;

        Protocol.writeLine(socket.getOutputStream(), "HOST " + config.getToken() + " " + config.getRequestedPublicPort());
        String response = Protocol.readLine(socket.getInputStream(), 4096);
        if (response == null) {
            throw new IOException("relay closed during handshake");
        }
        if (!response.startsWith("OK ")) {
            throw new IOException(response);
        }
        publicPort = parsePublicPort(response);
        connected = true;
        message = "Published. Friends can connect through the relay.";

        while (running.get()) {
            String line = Protocol.readLine(socket.getInputStream(), 4096);
            if (line == null) {
                throw new SocketException("relay closed the control connection");
            }
            if (line.startsWith("OPEN ")) {
                openDataConnection(line.substring(5).trim());
            } else if (line.startsWith("ERR ")) {
                message = line;
            }
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
            relaySocket.connect(new InetSocketAddress(config.getRelayHost(), config.getRelayControlPort()), 10_000);
            Protocol.writeLine(relaySocket.getOutputStream(), "DATA " + config.getToken() + " " + id);

            TunnelIo.bridge(relaySocket, localSocket);
        } catch (IOException exception) {
            if (running.get()) {
                LanTunnelMod.LOGGER.warn("LAN tunnel data connection failed", exception);
            }
        } finally {
            TunnelIo.closeQuietly(relaySocket);
            TunnelIo.closeQuietly(localSocket);
            activeConnections.decrementAndGet();
        }
    }

    private void sleepBeforeReconnect() {
        try {
            Thread.sleep(config.getReconnectDelaySeconds() * 1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
