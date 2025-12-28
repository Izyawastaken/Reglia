package com.example.reglia;

import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

/**
 * Discord bot that listens for messages and forwards them to Minecraft.
 */
public class DiscordBot extends ListenerAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static JDA jda = null;
    private static MinecraftServer server = null;
    private static boolean isConnected = false;

    /**
     * Start the Discord bot with the configured token.
     */
    public static void start(MinecraftServer mcServer) {
        server = mcServer;

        if (!Config.hasBotToken()) {
            LOGGER.warn(
                    "[Reglia] No bot token configured. Use /setbottoken <token> to enable Discord->Minecraft messages.");
            return;
        }

        try {
            LOGGER.info("[Reglia] Starting Discord bot...");

            jda = JDABuilder.createDefault(Config.botToken)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordBot())
                    .build();

            jda.awaitReady();
            isConnected = true;
            LOGGER.info("[Reglia] Discord bot connected successfully!");

        } catch (Exception e) {
            LOGGER.error("[Reglia] Failed to start Discord bot: {}", e.getMessage());
            isConnected = false;
        }
    }

    /**
     * Stop the Discord bot.
     */
    public static void stop() {
        if (jda != null) {
            LOGGER.info("[Reglia] Shutting down Discord bot...");
            jda.shutdown();
            jda = null;
            isConnected = false;
        }
    }

    /**
     * Check if bot is connected.
     */
    public static boolean isConnected() {
        return isConnected && jda != null;
    }

    /**
     * Restart the bot (used when token changes).
     */
    public static void restart() {
        stop();
        if (server != null) {
            start(server);
        }
    }

    /**
     * Called when a message is received in Discord.
     */
    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        // Ignore bot messages (including our own)
        if (event.getAuthor().isBot()) {
            return;
        }

        // Check if this is from the configured channel
        if (Config.hasChannelId()) {
            if (!event.getChannel().getId().equals(Config.channelId)) {
                return; // Not from the linked channel
            }
        }

        // Get message details
        String username = event.getAuthor().getName();
        String message = event.getMessage().getContentDisplay();

        // Skip empty messages
        if (message.isEmpty()) {
            return;
        }

        LOGGER.debug("[Reglia] Discord message from {}: {}", username, message);

        // Send to all players in Minecraft
        if (server != null) {
            server.execute(() -> {
                Component chatMessage = Component.literal("§9[Discord] §f" + username + "§7: §f" + message);

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.sendSystemMessage(chatMessage);
                }

                // Also log to server console
                LOGGER.info("[Discord] {}: {}", username, message);
            });
        }
    }
}
