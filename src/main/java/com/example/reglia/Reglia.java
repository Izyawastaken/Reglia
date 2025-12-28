package com.example.reglia;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Main mod class for Reglia.
 * This is the entry point for the mod where all initializations happen.
 */
@Mod(Reglia.MOD_ID)
public class Reglia {
    // Define the mod id in a common place for everything to reference
    public static final String MOD_ID = "reglia";
    
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // Create a Deferred Register for Creative Mode Tabs
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    /**
     * The constructor for the mod class.
     * @param modEventBus The mod event bus to register events with
     */
    public Reglia(IEventBus modEventBus) {
        // Register the commonSetup method for mod loading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        LOGGER.info("Reglia mod initialized!");
    }

    /**
     * Common setup phase - runs on both client and server
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Reglia common setup complete!");
    }

    /**
     * Add items to creative tabs
     */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Add items to creative tabs here
        // Example: if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) { ... }
    }

    /**
     * Called when the server starts
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Reglia: Server starting!");
    }

    /**
     * Client-side mod events
     */
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Reglia: Client setup!");
            LOGGER.info("Minecraft name: {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
