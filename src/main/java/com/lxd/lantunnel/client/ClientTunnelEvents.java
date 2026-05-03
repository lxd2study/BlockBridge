package com.lxd.lantunnel.client;

import com.lxd.lantunnel.config.LanTunnelConfig;
import com.lxd.lantunnel.tunnel.LanTunnelManager;
import com.lxd.lantunnel.tunnel.TunnelStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.server.IntegratedServer;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
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

    @SubscribeEvent
    public void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || !LanTunnelConfig.get().isShowLatencyOverlay()) {
            return;
        }

        TunnelStatus status = LanTunnelManager.get().getStatus();
        if (!status.connected()) {
            return;
        }

        drawLatency(event.getGuiGraphics(), minecraft.font, event.getWindow().getGuiScaledWidth(), status.latencyMillis());
    }

    private static void applyOfflinePlayerMode(IntegratedServer server) {
        boolean useAuthentication = !LanTunnelConfig.get().isAllowOfflinePlayers();
        if (server.usesAuthentication() != useAuthentication) {
            server.setUsesAuthentication(useAuthentication);
        }
    }

    private static void drawLatency(GuiGraphics graphics, Font font, int screenWidth, long latencyMillis) {
        String text = latencyMillis >= 0 ? "Relay: " + latencyMillis + " ms" : "Relay: -- ms";
        int width = font.width(text);
        int x = screenWidth - width - 6;
        int y = 6;
        int color = latencyMillis < 0 ? 0xAAAAAA : latencyColor(latencyMillis);

        graphics.fill(x - 4, y - 3, screenWidth - 2, y + 10, 0x66000000);
        graphics.drawString(font, text, x, y, color, true);
    }

    private static int latencyColor(long latencyMillis) {
        if (latencyMillis <= 80) {
            return 0x55FF55;
        }
        if (latencyMillis <= 180) {
            return 0xFFFF55;
        }
        return 0xFF5555;
    }
}
