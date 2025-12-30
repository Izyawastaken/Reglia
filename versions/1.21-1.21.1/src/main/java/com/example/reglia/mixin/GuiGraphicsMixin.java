package com.example.reglia.mixin;

import com.example.reglia.GifRegistry;
import com.example.reglia.client.GifManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept chat text rendering and display GIF previews.
 * Detects [GIF:url] tags and renders textures instead.
 */
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("HEAD"), cancellable = true)
    private void reglia$onDrawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow,
            CallbackInfoReturnable<Integer> cir) {
        // Optimized detection: Scan without full String allocation first
        final String[] detectedUrl = new String[1];

        text.accept(new net.minecraft.util.FormattedCharSink() {
            private int state = 0; // 0=none, 1=[, 2=G, 3=I, 4=F, 5=:
            private StringBuilder buffer = null;

            @Override
            public boolean accept(int index, net.minecraft.network.chat.Style style, int codePoint) {
                char c = (char) codePoint;

                // If we are capturing the URL/ID
                if (state == 5) {
                    if (c == ']') {
                        if (buffer != null)
                            detectedUrl[0] = buffer.toString();
                        return false; // Stop iteration, we found it
                    }
                    if (buffer == null)
                        buffer = new StringBuilder();
                    buffer.append(c);
                    return true;
                }

                // State machine for "[GIF:"
                if (state == 0 && c == '[') {
                    state = 1;
                    return true;
                }
                if (state == 1 && c == 'G') {
                    state = 2;
                    return true;
                }
                if (state == 2 && c == 'I') {
                    state = 3;
                    return true;
                }
                if (state == 3 && c == 'F') {
                    state = 4;
                    return true;
                }
                if (state == 4 && c == ':') {
                    state = 5;
                    return true;
                }

                // Reset if mismatch
                state = (c == '[') ? 1 : 0;
                return true;
            }
        });

        if (detectedUrl[0] == null)
            return;

        String rawTag = detectedUrl[0]; // e.g., "ID:123:W50:H40"
        String url = rawTag;
        int widthOverride = -1;
        int heightOverride = -1;

        // Parse ID, W, and H from tag
        String[] parts = rawTag.split(":");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.equals("ID") && i + 1 < parts.length) {
                try {
                    int id = Integer.parseInt(parts[i + 1]);
                    String resolved = GifRegistry.getUrl(id);
                    if (resolved != null)
                        url = resolved;
                } catch (NumberFormatException ignored) {
                }
            }
            if (part.startsWith("W") && part.length() > 1) {
                try {
                    widthOverride = Integer.parseInt(part.substring(1));
                } catch (NumberFormatException ignored) {
                }
            }
            if (part.startsWith("H") && part.length() > 1) {
                try {
                    heightOverride = Integer.parseInt(part.substring(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Get current animation frame
        ResourceLocation frame = GifManager.getFrame(url);

        if (frame != null) {
            cir.setReturnValue(x);

            int displayWidth = 48;
            int displayHeight = 48;
            int texWidth = 64;
            int texHeight = 64;

            GifManager.GifAnimation anim = GifManager.getAnimation(url);

            if (anim != null && anim.width > 0 && anim.height > 0) {
                texWidth = anim.width;
                texHeight = anim.height;

                // Use override dimensions if present (Smart Embedding)
                if (widthOverride > 0 && heightOverride > 0) {
                    displayWidth = widthOverride;
                    displayHeight = heightOverride;
                } else {
                    // Fallback to legacy auto-scaling
                    displayWidth = anim.width;
                    displayHeight = anim.height;
                    int maxHeight = 40;
                    int maxWidth = 200;

                    if (displayHeight > maxHeight) {
                        float scale = (float) maxHeight / displayHeight;
                        displayHeight = maxHeight;
                        displayWidth = (int) (displayWidth * scale);
                    }
                    if (displayWidth > maxWidth) {
                        float scale = (float) maxWidth / displayWidth;
                        displayWidth = maxWidth;
                        displayHeight = (int) (displayHeight * scale);
                    }
                }
            }

            GuiGraphics graphics = (GuiGraphics) (Object) this;

            // Respect alpha from text color (handles chat opacity/fading)
            float alpha = ((color >> 24) & 0xFF) / 255.0f;
            if (alpha <= 0.05f)
                return; // Don't render if invisible

            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

            // Render at current Y
            graphics.blit(frame, x, y, displayWidth, displayHeight, 0.0f, 0.0f, texWidth, texHeight, texWidth,
                    texHeight);

            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset
        }
    }
}
