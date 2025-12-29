package com.example.reglia.client;

import com.example.reglia.client.GifManager.GifEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class GifSelectorScreen extends Screen {
    private EditBox searchBox;
    private List<GifEntry> gifs = new ArrayList<>();
    private String lastQuery = "";
    private long lastTypeTime = 0;

    public GifSelectorScreen() {
        super(Component.literal("Select GIF"));
    }

    @Override
    protected void init() {
        super.init();
        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 20, 200, 20, Component.literal("Search"));
        this.addRenderableWidget(this.searchBox);

        // Load trending initially
        refreshGifs("");
    }

    @Override
    public void tick() {
        super.tick();
        // this.searchBox.tick();

        String query = this.searchBox.getValue();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            lastTypeTime = System.currentTimeMillis();
        }

        // Debounce search (500ms)
        if (lastTypeTime > 0 && System.currentTimeMillis() - lastTypeTime > 500) {
            refreshGifs(lastQuery);
            lastTypeTime = 0;
        }
    }

    private void refreshGifs(String query) {
        if (query.isEmpty()) {
            GifManager.getTrending().thenAccept(results -> {
                this.gifs = results;
            });
        } else {
            GifManager.searchTenor(query).thenAccept(results -> {
                this.gifs = results;
            });
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int startY = 50;
        int gridX = 20;
        int size = 64;
        int gap = 10;
        int cols = (this.width - 40) / (size + gap);

        if (cols < 1)
            cols = 1;

        for (int i = 0; i < gifs.size(); i++) {
            GifEntry gif = gifs.get(i);
            int row = i / cols;
            int col = i % cols;

            int x = gridX + col * (size + gap);
            int y = startY + row * (size + gap);

            // Render box
            graphics.fill(x, y, x + size, y + size, 0x88000000);

            // Render GIF preview
            GifManager.GifAnimation anim = GifManager.getAnimation(gif.url());
            ResourceLocation frame = GifManager.getFrame(gif.url());

            if (frame != null && anim != null && anim.width > 0 && anim.height > 0) {
                RenderSystem.setShaderTexture(0, frame);
                // Correct scaling:
                // blit(texture, x, y, width, height, u, v, uWidth, vHeight, textureWidth,
                // textureHeight)
                // - Destination: size x size (64x64)
                // - Source: 0, 0, anim.width, anim.height
                // - Texture Size: anim.width, anim.height
                graphics.blit(frame, x, y, size, size, 0, 0, anim.width, anim.height, anim.width, anim.height);
            } else {
                graphics.drawCenteredString(this.font, "Loading...", x + size / 2, y + size / 2 - 4, 0xAAAAAA);
            }

            // Hover effect
            if (mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size) {
                graphics.fill(x, y, x + size, y + size, 0x44FFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button))
            return true;

        int startY = 50;
        int gridX = 20;
        int size = 64;
        int gap = 10;
        int cols = (this.width - 40) / (size + gap);

        if (cols < 1)
            cols = 1;

        for (int i = 0; i < gifs.size(); i++) {
            int row = i / cols;
            int col = i % cols;

            int x = gridX + col * (size + gap);
            int y = startY + row * (size + gap);

            if (mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size) {
                sendGif(gifs.get(i).url());
                return true;
            }
        }
        return false;
    }

    private void sendGif(String url) {
        if (this.minecraft != null && this.minecraft.player != null) {
            // Send full URL so server/Discord bridge receives the link.
            // Client-side wrapping protection will be handled by ClientChatReceivedEvent.
            String msg = "[GIF:" + url + "]";
            this.minecraft.player.connection.sendChat(msg);
            this.onClose();
        }
    }
}
