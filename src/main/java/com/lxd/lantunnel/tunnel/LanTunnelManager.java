package com.lxd.lantunnel.tunnel;

import com.lxd.lantunnel.config.LanTunnelConfig;

public final class LanTunnelManager {
    private static LanTunnelManager instance;

    private final Object lock = new Object();
    private volatile LanTunnelClient client;
    private volatile int lanPort = -1;
    private volatile TunnelStatus idleStatus = new TunnelStatus(false, false, -1, -1, 0, -1, "等待开启局域网。");

    private LanTunnelManager() {
    }

    public static synchronized void initialize(LanTunnelConfig ignored) {
        if (instance == null) {
            instance = new LanTunnelManager();
        }
    }

    public static LanTunnelManager get() {
        return instance;
    }

    public void onLanOpen(int port) {
        lanPort = port;
        LanTunnelConfig config = LanTunnelConfig.get().copy();
        if (!config.isEnabled()) {
            stopWithMessage("穿透未启用。");
            return;
        }
        if (!config.isAutoStart()) {
            idleStatus = new TunnelStatus(false, false, lanPort, -1, 0, -1, "局域网已开启，自动启动已关闭。");
            return;
        }
        startIfNeeded(port, config);
    }

    public void onLanClosed() {
        lanPort = -1;
        stopWithMessage("等待开启局域网。");
    }

    public void onConfigChanged() {
        LanTunnelConfig config = LanTunnelConfig.get().copy();
        if (!config.isEnabled()) {
            stopWithMessage("穿透未启用。");
            return;
        }
        if (lanPort > 0 && config.isAutoStart()) {
            startIfNeeded(lanPort, config);
        } else {
            stopWithMessage(lanPort > 0 ? "局域网已开启，点击启动发布。" : "等待开启局域网。");
        }
    }

    public void startManually() {
        LanTunnelConfig config = LanTunnelConfig.get().copy();
        if (!config.isEnabled()) {
            idleStatus = new TunnelStatus(false, false, lanPort, -1, 0, -1, "请先启用穿透。");
            return;
        }
        if (lanPort <= 0) {
            idleStatus = new TunnelStatus(false, false, -1, -1, 0, -1, "请先对局域网开放世界。");
            return;
        }
        startIfNeeded(lanPort, config);
    }

    public void stopManually() {
        stopWithMessage(lanPort > 0 ? "已停止，局域网仍处于开启状态。" : "已停止。");
    }

    public boolean isRunning() {
        LanTunnelClient current = client;
        return current != null && current.isRunning();
    }

    public TunnelStatus getStatus() {
        LanTunnelClient current = client;
        return current != null ? current.getStatus() : idleStatus;
    }

    private void startIfNeeded(int port, LanTunnelConfig config) {
        String validationError = config.validate();
        if (validationError != null) {
            stopWithMessage(validationError);
            return;
        }
        synchronized (lock) {
            LanTunnelClient current = client;
            if (current != null && current.matches(port, config)) {
                return;
            }
            stopLocked();
            LanTunnelClient next = new LanTunnelClient(config, port);
            client = next;
            next.start();
        }
    }

    private void stopWithMessage(String message) {
        synchronized (lock) {
            stopLocked();
            idleStatus = new TunnelStatus(false, false, lanPort, -1, 0, -1, message);
        }
    }

    private void stopLocked() {
        LanTunnelClient current = client;
        client = null;
        if (current != null) {
            current.stop();
        }
    }
}
