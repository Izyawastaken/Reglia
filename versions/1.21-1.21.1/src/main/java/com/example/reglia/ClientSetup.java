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
        // Match [GIF:url:W<width>:H<height>] (Smart Embedding) or legacy formats
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[GIF:(.*?)(?::W(\\d+))?(?::H(\\d+))?\\]");
        java.util.regex.Matcher m = p.matcher(msg);

        if (m.find()) {
            String url = m.group(1);
            String widthStr = m.group(2);
            String heightStr = m.group(3);

            int id = GifRegistry.register(url);

            // Default fallback
            int height = 40;
            int width = 40;

            // Parse smart dimensions if present
            if (heightStr != null) {
                try {
                    height = Integer.parseInt(heightStr);
                } catch (NumberFormatException ignored) {
                }
            }
            if (widthStr != null) {
                try {
                    width = Integer.parseInt(widthStr);
                } catch (NumberFormatException ignored) {
                }
            }

            // Calculate precise lines needed (height / 9px per line)
            // +1 line padding is usually sufficient if height is exact
            int lines = (int) Math.ceil(height / 9.0) + 1;
            String spacing = "\n".repeat(lines);

            // Reconstruct message with ID and dimensions for the renderer to use
            // Format: [GIF:ID:<id>:W<w>:H<h>]
            String newTag = "[GIF:ID:" + id + ":W" + width + ":H" + height + "]";
            String newText = msg.replace(m.group(0), "\n" + newTag + spacing);
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
