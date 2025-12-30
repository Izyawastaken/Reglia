package com.example.reglia;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Reglia - Discord Bridge with GIF Previews for Minecraft
 */
@Mod(Reglia.MOD_ID)
public class Reglia {
    public static final String MOD_ID = "reglia";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Reglia(IEventBus modEventBus, ModContainer modContainer) {
        // Lifecycle events
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);

        // Server events
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerChat);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);

        LOGGER.info("[Reglia] Initializing v3.5 for NeoForge 1.21.x");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        Config.load();
        LOGGER.info("[Reglia] Configuration loaded");
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("[Reglia] Client setup complete");
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Reglia] Server starting, initializing Discord bot...");
        DiscordBot.start(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[Reglia] Server stopping, disconnecting Discord bot...");
        DiscordBot.stop();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        LOGGER.info("[Reglia] Commands registered");
    }

    /**
     * Handle player login - show first-boot message if not configured
     */
    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        // Only show once, and only if not configured
        if (!Config.firstBootShown && !Config.isConfigured()) {
            // Only show to ops
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§6[Reglia] §fNot configured! Run ")
                        .append(Component.literal("§b§n/reglia")
                                .withStyle(style -> style.withClickEvent(
                                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reglia"))))
                        .append(Component.literal("§f to set up.")));

                // Mark as shown so we don't bother again
                Config.setFirstBootShown(true);
            }
        }
    }

    /**
     * Handle player chat and send to Discord
     */
    private void onServerChat(ServerChatEvent event) {
        String playerName = event.getPlayer().getName().getString();
        String message = event.getMessage().getString();

        LOGGER.debug("[Reglia] Chat: {} -> {}", playerName, message);
        DiscordWebhook.sendChatMessage(playerName, message);
    }
}
