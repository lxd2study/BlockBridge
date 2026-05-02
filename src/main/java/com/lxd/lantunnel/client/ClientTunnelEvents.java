package com.lxd.lantunnel.client;

import com.lxd.lantunnel.config.LanTunnelConfig;
import com.lxd.lantunnel.tunnel.LanTunnelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ClientTunnelEvents {
    private int ticks;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks < 20) {
            return;
        }
        ticks = 0;

        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            applyOfflinePlayerMode(server);
        }
        if (server != null && server.isPublished() && server.getPort() > 0) {
            LanTunnelManager.get().onLanOpen(server.getPort());
        } else {
            LanTunnelManager.get().onLanClosed();
        }
    }

    private static void applyOfflinePlayerMode(IntegratedServer server) {
        boolean useAuthentication = !LanTunnelConfig.get().isAllowOfflinePlayers();
        if (server.usesAuthentication() != useAuthentication) {
            server.setUsesAuthentication(useAuthentication);
        }
    }
}
