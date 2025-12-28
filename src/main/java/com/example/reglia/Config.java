package com.example.reglia;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Configuration for Reglia Discord Bridge.
 */
@Mod.EventBusSubscriber(modid = Reglia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Webhook for MC -> Discord
    private static final ForgeConfigSpec.ConfigValue<String> WEBHOOK_URL = BUILDER
            .comment("Discord webhook URL for sending chat TO Discord")
            .define("webhookUrl", "");

    // Bot token for Discord -> MC
    private static final ForgeConfigSpec.ConfigValue<String> BOT_TOKEN = BUILDER
            .comment("Discord bot token for receiving messages FROM Discord")
            .define("botToken", "");

    // Channel ID to listen to
    private static final ForgeConfigSpec.ConfigValue<String> CHANNEL_ID = BUILDER
            .comment("Discord channel ID to listen to (leave empty for all)")
            .define("channelId", "");

    private static final ForgeConfigSpec.BooleanValue BRIDGE_ENABLED = BUILDER
            .comment("Enable or disable the Discord bridge")
            .define("bridgeEnabled", true);

    private static final ForgeConfigSpec.BooleanValue SEND_DEATHS = BUILDER
            .comment("Send player death messages to Discord")
            .define("sendDeaths", true);

    private static final ForgeConfigSpec.BooleanValue SEND_JOIN_LEAVE = BUILDER
            .comment("Send player join/leave messages to Discord")
            .define("sendJoinLeave", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // Runtime values
    public static String webhookUrl = "";
    public static String botToken = "";
    public static String channelId = "";
    public static boolean bridgeEnabled = true;
    public static boolean sendDeaths = true;
    public static boolean sendJoinLeave = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        webhookUrl = WEBHOOK_URL.get();
        botToken = BOT_TOKEN.get();
        channelId = CHANNEL_ID.get();
        bridgeEnabled = BRIDGE_ENABLED.get();
        sendDeaths = SEND_DEATHS.get();
        sendJoinLeave = SEND_JOIN_LEAVE.get();
    }

    public static void setWebhookUrl(String url) {
        webhookUrl = url;
        WEBHOOK_URL.set(url);
    }

    public static void setBotToken(String token) {
        botToken = token;
        BOT_TOKEN.set(token);
    }

    public static void setChannelId(String id) {
        channelId = id;
        CHANNEL_ID.set(id);
    }

    public static boolean hasWebhook() {
        return webhookUrl != null && !webhookUrl.isEmpty();
    }

    public static boolean hasBotToken() {
        return botToken != null && !botToken.isEmpty();
    }

    public static boolean hasChannelId() {
        return channelId != null && !channelId.isEmpty();
    }
}
