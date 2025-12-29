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
        String urlOrId = detectedUrl[0];
        String url = urlOrId;

        // Resolve ID if present
        if (urlOrId.startsWith("ID:")) {
            try {
                int id = Integer.parseInt(urlOrId.substring(3));
                String resolved = GifRegistry.getUrl(id);
                if (resolved != null)
                    url = resolved;
            } catch (NumberFormatException ignored) {
            }
        }

        // Get current animation frame
        ResourceLocation frame = GifManager.getFrame(url);

        // Only hide text and render if we have a valid frame
        if (frame != null) {
            cir.setReturnValue(x);

            GifManager.GifAnimation anim = GifManager.getAnimation(url);

            // Height of ~3 lines (Current line + 2 newlines reserved)
            int displayHeight = 25;
            int displayWidth = displayHeight;

            int texWidth = 64;
            int texHeight = 64;

            if (anim != null && anim.width > 0 && anim.height > 0) {
                float aspect = (float) anim.width / anim.height;
                displayWidth = (int) (displayHeight * aspect);
                texWidth = anim.width;
                texHeight = anim.height;

                // Cap width
                if (displayWidth > 200) {
                    displayWidth = 200;
                    displayHeight = (int) (displayWidth / aspect);
                }
            }

            GuiGraphics graphics = (GuiGraphics) (Object) this;

            // Render at current Y (top of the line).
            // Since we added newlines in ClientSetup, we have space BELOW this line.
            graphics.blit(frame, x, y, displayWidth, displayHeight, 0.0f, 0.0f, texWidth, texHeight, texWidth,
                    texHeight);
        }
    }
}
