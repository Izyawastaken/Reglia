package com.example.reglia;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * Listens to in-game events and sends them to Discord.
 */
public class ChatListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Listen for chat messages and send to Discord
     */
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!Config.bridgeEnabled || !Config.hasWebhook()) {
            return;
        }

        String playerName = event.getPlayer().getName().getString();
        String message = event.getMessage().getString();

        LOGGER.debug("Sending chat to Discord: <{}> {}", playerName, message);
        DiscordWebhook.sendChatMessage(playerName, message);
    }

    /**
     * Listen for player joins
     */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Config.bridgeEnabled || !Config.hasWebhook() || !Config.sendJoinLeave) {
            return;
        }

        String playerName = event.getEntity().getName().getString();
        DiscordWebhook.sendMessage("📥 **" + playerName + "** joined the game", "Server");
    }

    /**
     * Listen for player leaves
     */
    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!Config.bridgeEnabled || !Config.hasWebhook() || !Config.sendJoinLeave) {
            return;
        }

        String playerName = event.getEntity().getName().getString();
        DiscordWebhook.sendMessage("📤 **" + playerName + "** left the game", "Server");
    }

    /**
     * Listen for player deaths
     */
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!Config.bridgeEnabled || !Config.hasWebhook() || !Config.sendDeaths) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            String deathMessage = event.getSource().getLocalizedDeathMessage(player).getString();
            DiscordWebhook.sendMessage("💀 " + deathMessage, "Server");
        }
    }
}
