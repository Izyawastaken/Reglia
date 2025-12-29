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
 * Handles sending messages from Minecraft to Discord via webhooks.
 * Uses standard Java networking (no external libs).
 */
public class DiscordWebhook {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Send a message to Discord via webhook.
     * Runs asynchronously to avoid blocking the game thread.
     */
    public static boolean sendMessage(String message, String username) {
        if (!Config.hasWebhook() || !Config.bridgeEnabled) {
            return false;
        }

        CompletableFuture.runAsync(() -> {
            try {
                sendPost(Config.webhookUrl, message, username, null);
            } catch (Exception e) {
                LOGGER.error("[Reglia] Webhook error: {}", e.getMessage());
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

        String avatarUrl = "https://crafatar.com/avatars/" + playerName + "?overlay=true";

        CompletableFuture.runAsync(() -> {
            try {
                String content = message;
                // Strip [GIF:url] or [GIF:url:H<height>] tag for clean Discord links
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[GIF:(https?://[^\\]]+?)(?::H\\d+)?\\]");
                java.util.regex.Matcher m = p.matcher(content);
                if (m.find()) {
                    content = m.group(1); // Just the URL
                }

                sendPost(Config.webhookUrl, content, playerName, avatarUrl);
            } catch (Exception e) {
                LOGGER.error("[Reglia] Webhook error: {}", e.getMessage());
            }
        });

        return true;
    }

    private static void sendPost(String webhookUrl, String content, String username, String avatarUrl)
            throws Exception {
        URL url = new URL(webhookUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "Reglia/3.0");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        JsonObject json = new JsonObject();
        json.addProperty("content", content);
        json.addProperty("username", username);
        if (avatarUrl != null) {
            json.addProperty("avatar_url", avatarUrl);
        }

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input);
        }

        int code = conn.getResponseCode();
        if (code != 200 && code != 204) {
            LOGGER.warn("[Reglia] Webhook returned code: {}", code);
        }

        conn.disconnect();
    }
}
