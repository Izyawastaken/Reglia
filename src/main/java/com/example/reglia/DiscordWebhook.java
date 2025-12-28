package com.example.reglia;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Handles sending messages to Discord webhooks.
 */
public class DiscordWebhook {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Send a message to the configured Discord webhook.
     * Runs asynchronously to not block the game thread.
     *
     * @param message  The message content
     * @param username The username to display (player name or "Server")
     * @return true if the message was queued successfully
     */
    public static boolean sendMessage(String message, String username) {
        if (!Config.hasWebhook() || !Config.bridgeEnabled) {
            return false;
        }

        // Run async to not block game thread
        CompletableFuture.runAsync(() -> {
            try {
                sendWebhookMessage(Config.webhookUrl, message, username);
            } catch (Exception e) {
                LOGGER.error("Failed to send Discord message: {}", e.getMessage());
            }
        });

        return true;
    }

    /**
     * Send a chat message with player avatar.
     */
    public static boolean sendChatMessage(String playerName, String message) {
        if (!Config.hasWebhook() || !Config.bridgeEnabled) {
            return false;
        }

        CompletableFuture.runAsync(() -> {
            try {
                sendWebhookWithAvatar(Config.webhookUrl, message, playerName);
            } catch (Exception e) {
                LOGGER.error("Failed to send Discord chat message: {}", e.getMessage());
            }
        });

        return true;
    }

    /**
     * Internal method to send webhook message.
     */
    private static void sendWebhookMessage(String webhookUrl, String content, String username) throws Exception {
        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "Reglia-DiscordBridge");
        connection.setDoOutput(true);

        // Build JSON payload
        JsonObject json = new JsonObject();
        json.addProperty("content", content);
        json.addProperty("username", username);

        String jsonPayload = json.toString();

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 204 && responseCode != 200) {
            LOGGER.warn("Discord webhook returned code: {}", responseCode);
        }

        connection.disconnect();
    }

    /**
     * Send webhook with player head avatar.
     */
    private static void sendWebhookWithAvatar(String webhookUrl, String content, String playerName) throws Exception {
        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "Reglia-DiscordBridge");
        connection.setDoOutput(true);

        // Build JSON payload with avatar
        JsonObject json = new JsonObject();
        json.addProperty("content", content);
        json.addProperty("username", playerName);
        // Use Crafatar for player head avatars
        json.addProperty("avatar_url", "https://crafatar.com/avatars/" + playerName + "?overlay=true");

        String jsonPayload = json.toString();

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 204 && responseCode != 200) {
            LOGGER.warn("Discord webhook returned code: {}", responseCode);
        }

        connection.disconnect();
    }
}
