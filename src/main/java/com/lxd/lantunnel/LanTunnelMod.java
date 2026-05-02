package com.lxd.lantunnel;

import com.lxd.lantunnel.client.ClientTunnelEvents;
import com.lxd.lantunnel.client.LanTunnelConfigScreen;
import com.lxd.lantunnel.config.LanTunnelConfig;
import com.lxd.lantunnel.tunnel.LanTunnelManager;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

@Mod(LanTunnelMod.MOD_ID)
public final class LanTunnelMod {
    public static final String MOD_ID = "lan_tunnel";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LanTunnelMod() {
        LanTunnelConfig.load(FMLPaths.CONFIGDIR.get().resolve(MOD_ID + ".properties"));
        LanTunnelManager.initialize(LanTunnelConfig.get());

        MinecraftForge.EVENT_BUS.register(new ClientTunnelEvents());
        MinecraftForge.registerConfigScreen(parent -> new LanTunnelConfigScreen(parent));
    }
}
