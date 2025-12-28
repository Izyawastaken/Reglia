package com.example.reglia;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Reglia - Discord Bridge Mod
 * Sends in-game chat to Discord via webhooks.
 */
@Mod(Reglia.MOD_ID)
public class Reglia {
    public static final String MOD_ID = "reglia";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Reglia() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ChatListener());

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        LOGGER.info("Reglia Discord Bridge initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Reglia: Discord Bridge ready!");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ModCommands.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Reglia: Commands registered!");
    }
}
