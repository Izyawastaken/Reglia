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

    private static final ForgeConfigSpec.ConfigValue<String> WEBHOOK_URL = BUILDER
            .comment("Discord webhook URL for sending chat messages to Discord")
            .define("webhookUrl", "");

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

    public static String webhookUrl = "";
    public static boolean bridgeEnabled = true;
    public static boolean sendDeaths = true;
    public static boolean sendJoinLeave = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        webhookUrl = WEBHOOK_URL.get();
        bridgeEnabled = BRIDGE_ENABLED.get();
        sendDeaths = SEND_DEATHS.get();
        sendJoinLeave = SEND_JOIN_LEAVE.get();
    }

    public static void setWebhookUrl(String url) {
        webhookUrl = url;
        WEBHOOK_URL.set(url);
    }

    public static boolean hasWebhook() {
        return webhookUrl != null && !webhookUrl.isEmpty();
    }
}
