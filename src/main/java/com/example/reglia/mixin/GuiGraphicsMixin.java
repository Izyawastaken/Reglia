package com.example.reglia.mixin;

import com.example.reglia.client.GifManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {
    private static boolean isRenderingGif = false;

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("HEAD"), cancellable = true)
    private void onDrawString(Font font, FormattedCharSequence sequence, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (isRenderingGif) return;

        StringBuilder sb = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            sb.append((char) codePoint);
            return true;
        });

        String text = sb.toString();
        if (text.contains("[GIF:")) {
            int start = text.indexOf("[GIF:");
            int end = text.indexOf("]", start);
            if (end != -1) {
                String url = text.substring(start + 5, end);
                
                // Hide the technical tag Line
                cir.setReturnValue(x);

                ResourceLocation frame = GifManager.getFrame(url);
                if (frame != null) {
                    GifManager.GifAnimation anim = GifManager.getAnimation(url);
                    int lineHeight = 9;
                    int reservedLines = 4;
                    int totalHeight = lineHeight * reservedLines;
                    
                    int displayHeight = totalHeight - 2; // Slight padding
                    int displayWidth = displayHeight; // Default square
                    
                    if (anim != null && anim.width > 0 && anim.height > 0) {
                        float aspect = (float) anim.width / anim.height;
                        displayWidth = (int) (displayHeight * aspect);
                        
                        // Cap width to avoid covering chat entirely
                        if (displayWidth > 200) {
                            displayWidth = 200;
                            displayHeight = (int) (displayWidth / aspect);
                        }
                    }
                    
                    GuiGraphics graphics = (GuiGraphics) (Object) this;
                    // Render in the space of the lines ABOVE the Current tag
                    // The tag is on the last of the 4 lines.
                    int renderY = y - (reservedLines - 1) * lineHeight + 1;
                    
                    if (anim != null && anim.width > 0) {
                         // blit(texture, x, y, width, height, u0, v0, uWidth, vHeight, texWidth, texHeight)
                         // This overload ENSURES scaling of the entire source image
                         graphics.blit(frame, x, renderY, displayWidth, displayHeight, 0, 0, anim.width, anim.height, anim.width, anim.height);
                    } else {
                         graphics.blit(frame, x, renderY, displayWidth, displayHeight, 0, 0, displayWidth, displayHeight, displayWidth, displayHeight);
                    }
                }
            }
        }
    }
}
