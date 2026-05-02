package com.lxd.lantunnel.tunnel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

public final class TunnelIo {
    private static final int BUFFER_SIZE = 16 * 1024;

    private TunnelIo() {
    }

    public static void bridge(Socket left, Socket right) {
        CountDownLatch done = new CountDownLatch(2);
        Thread leftToRight = new Thread(() -> pipe(left, right, done), "lan-tunnel-pipe-left");
        Thread rightToLeft = new Thread(() -> pipe(right, left, done), "lan-tunnel-pipe-right");
        leftToRight.setDaemon(true);
        rightToLeft.setDaemon(true);
        leftToRight.start();
        rightToLeft.start();
        try {
            done.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            closeQuietly(left);
            closeQuietly(right);
        }
    }

    private static void pipe(Socket source, Socket target, CountDownLatch done) {
        try {
            InputStream input = source.getInputStream();
            OutputStream output = target.getOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                output.flush();
            }
        } catch (IOException ignored) {
            // Socket shutdown is the normal way a Minecraft client leaves the tunnel.
        } finally {
            closeQuietly(source);
            closeQuietly(target);
            done.countDown();
        }
    }

    public static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
