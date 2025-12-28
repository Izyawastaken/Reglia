package com.example.reglia;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration handler for Reglia mod.
 * Define all configurable options here.
 */
@Mod.EventBusSubscriber(modid = Reglia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Example configuration options
    private static final ModConfigSpec.BooleanValue ENABLE_FEATURES = BUILDER
            .comment("Enable or disable all Reglia features")
            .define("enableFeatures", true);

    private static final ModConfigSpec.IntValue EXAMPLE_VALUE = BUILDER
            .comment("An example integer configuration value")
            .defineInRange("exampleValue", 100, 0, 1000);

    static final ModConfigSpec SPEC = BUILDER.build();

    // Runtime cached values
    public static boolean enableFeatures;
    public static int exampleValue;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enableFeatures = ENABLE_FEATURES.get();
        exampleValue = EXAMPLE_VALUE.get();
    }
}
