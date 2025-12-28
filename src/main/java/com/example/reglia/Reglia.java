package com.example.reglia;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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
 * Two-way bridge between Minecraft chat and Discord.
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
        // Register commands
        ModCommands.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Reglia: Commands registered!");

        // Start Discord bot if token is configured
        DiscordBot.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Clean shutdown of Discord bot
        DiscordBot.stop();

        // Send disconnect message to Discord
        if (Config.hasWebhook()) {
            DiscordWebhook.sendMessage("🔴 Server stopped", "Server");
        }
    }
}
