package com.example.reglia;

import com.example.reglia.client.GifSelectorScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Reglia.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientSetup {

    public static final KeyMapping OPEN_GIF_MENU = new KeyMapping(
            "key.reglia.open_gif_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.reglia");

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (OPEN_GIF_MENU.consumeClick()) {
            Minecraft.getInstance().setScreen(new GifSelectorScreen());
        }
    }

    @SubscribeEvent
    public static void onClientChatReceived(net.neoforged.neoforge.client.event.ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();

        // Match both [GIF:url] and [GIF:url:H<height>] formats
        // Use non-greedy match for URL to handle the optional :H suffix
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[GIF:(https?://[^\\]]+?)(?::H(\\d+))?\\]");
        java.util.regex.Matcher m = p.matcher(msg);

        if (m.find()) {
            String url = m.group(1);
            String heightHint = m.group(2);
            int id = GifRegistry.register(url);

            // Calculate newlines based on height hint, or default to max
            int height = 90; // Default max for mini-chat visibility
            if (heightHint != null) {
                height = Integer.parseInt(heightHint);
            }
            int lines = (int) Math.ceil(height / 9.0);
            String spacing = "\n".repeat(lines);

            // Replace with short ID and exact spacing
            String newText = msg.replace(m.group(0), "\n[GIF:ID:" + id + "]" + spacing);
            event.setMessage(net.minecraft.network.chat.Component.literal(newText));
        }
    }

    @EventBusSubscriber(modid = Reglia.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_GIF_MENU);
        }
    }
}
