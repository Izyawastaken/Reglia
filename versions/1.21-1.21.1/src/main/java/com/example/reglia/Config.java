package com.example.reglia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JSON-based configuration for Reglia.
 * Stores all settings in config/reglia-config.json
 */
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "reglia-config.json";

    private static ConfigData data = new ConfigData();
    private static Path configPath;

    // Configuration data class
    public static class ConfigData {
        public String webhookUrl = "";
        public String botToken = "";
        public String channelId = "";
        public boolean bridgeEnabled = true;
        public boolean sendDeaths = true;
        public boolean sendJoinLeave = true;
        public boolean firstBootShown = false; // Track if setup message was shown
    }

    // Static accessors for easy use
    public static String webhookUrl = "";
    public static String botToken = "";
    public static String channelId = "";
    public static boolean bridgeEnabled = true;
    public static boolean sendDeaths = true;
    public static boolean sendJoinLeave = true;
    public static boolean firstBootShown = false;

    public static void load() {
        try {
            configPath = Paths.get("config").resolve(CONFIG_FILE);

            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                data = GSON.fromJson(json, ConfigData.class);
                if (data == null)
                    data = new ConfigData();
                LOGGER.info("[Reglia] Config loaded from: {}", configPath);
            } else {
                save();
                LOGGER.info("[Reglia] Created default config at: {}", configPath);
            }

            // Sync static fields
            syncFields();
        } catch (Exception e) {
            LOGGER.error("[Reglia] Failed to load config", e);
        }
    }

    public static void save() {
        try {
            if (configPath == null) {
                configPath = Paths.get("config").resolve(CONFIG_FILE);
            }
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(data));
        } catch (Exception e) {
            LOGGER.error("[Reglia] Failed to save config", e);
        }
    }

    private static void syncFields() {
        webhookUrl = data.webhookUrl != null ? data.webhookUrl : "";
        botToken = data.botToken != null ? data.botToken : "";
        channelId = data.channelId != null ? data.channelId : "";
        bridgeEnabled = data.bridgeEnabled;
        sendDeaths = data.sendDeaths;
        sendJoinLeave = data.sendJoinLeave;
        firstBootShown = data.firstBootShown;
    }

    // Setters
    public static void setWebhookUrl(String url) {
        data.webhookUrl = url;
        webhookUrl = url;
        save();
    }

    public static void setBotToken(String token) {
        data.botToken = token;
        botToken = token;
        save();
    }

    public static void setChannelId(String id) {
        data.channelId = id;
        channelId = id;
        save();
    }

    public static void setBridgeEnabled(boolean enabled) {
        data.bridgeEnabled = enabled;
        bridgeEnabled = enabled;
        save();
    }

    public static void setSendDeaths(boolean enabled) {
        data.sendDeaths = enabled;
        sendDeaths = enabled;
        save();
    }

    public static void setSendJoinLeave(boolean enabled) {
        data.sendJoinLeave = enabled;
        sendJoinLeave = enabled;
        save();
    }

    // Helpers
    public static boolean hasWebhook() {
        return webhookUrl != null && !webhookUrl.isEmpty();
    }

    public static boolean hasBotToken() {
        return botToken != null && !botToken.isEmpty();
    }

    public static boolean hasChannelId() {
        return channelId != null && !channelId.isEmpty();
    }

    public static void setFirstBootShown(boolean shown) {
        data.firstBootShown = shown;
        firstBootShown = shown;
        save();
    }

    public static boolean isConfigured() {
        return hasWebhook() || hasBotToken();
    }
}
