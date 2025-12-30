package com.example.reglia.client;

import com.example.reglia.client.GifManager.GifEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class GifSelectorScreen extends Screen {
    private EditBox searchBox;
    private List<GifEntry> gifs = new ArrayList<>();
    private String lastQuery = "";
    private long lastTypeTime = 0;

    // Layout
    private float scrollAmount = 0;
    private int modalWidth, modalHeight, modalX, modalY;
    private int contentHeight = 0;

    public GifSelectorScreen() {
        super(Component.literal("Select GIF"));
    }

    @Override
    protected void init() {
        super.init();

        // Modal 70% width, 80% height
        this.modalWidth = (int) (this.width * 0.7);
        this.modalHeight = (int) (this.height * 0.8);
        this.modalX = (this.width - modalWidth) / 2;
        this.modalY = (this.height - modalHeight) / 2;

        int searchWidth = Math.min(400, modalWidth - 100);
        this.searchBox = new EditBox(this.font, centerX() - searchWidth / 2, modalY + 50, searchWidth, 18,
                Component.literal("Search"));
        this.searchBox.setBordered(false); // Custom rendering
        this.searchBox.setMaxLength(50);
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);
        this.searchBox.setFocused(true);

        // Load trending initially
        refreshGifs("");
    }

    private int centerX() {
        return this.width / 2;
    }

    @Override
    public void tick() {
        super.tick();

        String query = this.searchBox.getValue();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            lastTypeTime = System.currentTimeMillis();
        }

        // Debounce search (500ms)
        if (lastTypeTime > 0 && System.currentTimeMillis() - lastTypeTime > 500) {
            refreshGifs(lastQuery);
            lastTypeTime = 0;
            scrollAmount = 0; // Reset scroll
        }
    }

    private void refreshGifs(String query) {
        if (query.isEmpty()) {
            GifManager.getTrending().thenAccept(results -> this.gifs = results);
        } else {
            GifManager.searchTenor(query).thenAccept(results -> this.gifs = results);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 0. Reset Render State (Fixes potential blur/bleeding from other screens)
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 1. Custom Dark Background (Bottom Layer)
        graphics.fill(0, 0, this.width, this.height, 0xEE000000); // 93% black overlay

        // 2. Modal Window
        graphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF111111); // Main BG
        graphics.renderOutline(modalX, modalY, modalWidth, modalHeight, 0xFF333333); // Border

        // 3. Header
        graphics.drawCenteredString(this.font, "GIF Library", centerX(), modalY + 20, 0xFFFFFFFF);

        // Close Button (X)
        int closeX = modalX + modalWidth - 30;
        int closeY = modalY + 15;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 20 && mouseY >= closeY && mouseY <= closeY + 20;
        graphics.fill(closeX, closeY, closeX + 20, closeY + 20, hoverClose ? 0xFFFF4444 : 0xFF333333);
        graphics.drawCenteredString(this.font, "X", closeX + 10, closeY + 6, 0xFFFFFFFF);

        // 4. Search Box Styling
        int searchX = this.searchBox.getX();
        int searchY = this.searchBox.getY();
        int searchW = this.searchBox.getWidth();
        int searchH = this.searchBox.getHeight();

        // Search BG
        // Search BG
        graphics.fill(searchX - 5, searchY - 5, searchX + searchW + 5, searchY + searchH + 5, 0xFF1A1A1A);
        graphics.renderOutline(searchX - 5, searchY - 5, searchW + 10, searchH + 10,
                this.searchBox.isFocused() ? 0xFFFFFFFF : 0xFF333333);

        // Explicitly render Search Widget (No Z-translation, No super.render recursion)
        this.searchBox.render(graphics, mouseX, mouseY, partialTick);

        // 5. Grid View Area
        int startY = modalY + 90;
        int endY = modalY + modalHeight - 20;
        int viewWidth = modalWidth - 40;
        int viewX = modalX + 20;

        // Scissor for scrolling view
        graphics.enableScissor(viewX, startY, viewX + viewWidth, endY);

        // Grid Logic
        int tileSize = 80;
        int gap = 12;
        int cols = Math.max(1, viewWidth / (tileSize + gap));
        int totalWidth = cols * tileSize + (cols - 1) * gap;
        int startX = viewX + (viewWidth - totalWidth) / 2; // Center grid

        int gridY = startY - (int) scrollAmount;

        for (int i = 0; i < gifs.size(); i++) {
            GifEntry gif = gifs.get(i);
            int row = i / cols;
            int col = i % cols;

            int x = startX + col * (tileSize + gap);
            int y = gridY + row * (tileSize + gap);

            // Culling (optimization)
            if (y > endY || y + tileSize < startY)
                continue;

            // Render Tile
            renderGifTile(graphics, gif, x, y, tileSize, mouseX, mouseY);
        }

        contentHeight = (int) Math.ceil((double) gifs.size() / cols) * (tileSize + gap);
        graphics.disableScissor();

        // 6. Scrollbar
        if (contentHeight > (endY - startY)) {
            int extra = contentHeight - (endY - startY);
            float ratio = (float) (endY - startY) / contentHeight;
            int barHeight = Math.max(30, (int) ((endY - startY) * ratio));
            int barTop = startY + (int) ((endY - startY - barHeight) * (scrollAmount / extra));

            int barX = modalX + modalWidth - 10;
            graphics.fill(barX, startY, barX + 4, endY, 0xFF222222); // Track
            graphics.fill(barX, barTop, barX + 4, barTop + barHeight, 0xFF666666); // Handle
        }
    }

    private void renderGifTile(GuiGraphics graphics, GifEntry gif, int x, int y, int size, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;

        // Hover Scale Effect
        if (hover) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + size / 2f, y + size / 2f, 0);
            graphics.pose().scale(1.05f, 1.05f, 1f);
            graphics.pose().translate(-(x + size / 2f), -(y + size / 2f), 0);
        }

        // BG
        graphics.fill(x, y, x + size, y + size, 0xFF222222);

        // Image
        GifManager.GifAnimation anim = GifManager.getAnimation(gif.url());
        ResourceLocation frame = GifManager.getFrame(gif.url());

        if (frame != null && anim != null && anim.width > 0 && anim.height > 0) {
            RenderSystem.setShaderTexture(0, frame);
            graphics.blit(frame, x, y, size, size, 0, 0, anim.width, anim.height, anim.width, anim.height);
        } else {
            // Skeleton Loading Animation
            long time = System.currentTimeMillis();
            float pulse = (Mth.sin(time * 0.005f) + 1) * 0.5f; // 0 to 1
            int c = (int) (34 + pulse * 20);
            int color = 0xFF000000 | (c << 16) | (c << 8) | c;
            graphics.fill(x, y, x + size, y + size, color);
        }

        // Border (Glow on hover)
        if (hover) {
            graphics.renderOutline(x, y, size, size, 0xFFFFFFFF);
            graphics.pose().popPose(); // Restore scale
        } else {
            graphics.renderOutline(x, y, size, size, 0xFF333333);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (contentHeight > (modalHeight - 110)) {
            scrollAmount -= scrollY * 20; // Scroll speed
            scrollAmount = Mth.clamp(scrollAmount, 0, Math.max(0, contentHeight - (modalHeight - 110)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Close Button
        int closeX = modalX + modalWidth - 30;
        int closeY = modalY + 15;
        if (mouseX >= closeX && mouseX <= closeX + 20 && mouseY >= closeY && mouseY <= closeY + 20) {
            this.onClose();
            return true;
        }

        // Search Focus
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Grid Clicks
        int startY = modalY + 90;
        int endY = modalY + modalHeight - 20;
        int viewWidth = modalWidth - 40;
        int viewX = modalX + 20;

        if (mouseY >= startY && mouseY <= endY && mouseX >= viewX && mouseX <= viewX + viewWidth) {
            int tileSize = 80;
            int gap = 12;
            int cols = Math.max(1, viewWidth / (tileSize + gap));
            int totalWidth = cols * tileSize + (cols - 1) * gap;
            int startX = viewX + (viewWidth - totalWidth) / 2;

            float relativeY = (float) mouseY - startY + scrollAmount;

            for (int i = 0; i < gifs.size(); i++) {
                int row = i / cols;
                int col = i % cols;

                int x = startX + col * (tileSize + gap);
                int y = row * (tileSize + gap); // Relative Y

                if (mouseX >= x && mouseX <= x + tileSize && relativeY >= y && relativeY <= y + tileSize) {
                    sendGif(gifs.get(i).url());
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendGif(String url) {
        if (this.minecraft != null && this.minecraft.player != null) {
            GifManager.GifAnimation anim = GifManager.getAnimation(url);

            // Default dimensions
            int height = 40;
            int width = height; // Square default

            if (anim != null && anim.width > 0 && anim.height > 0) {
                // Determine target dimensions (max 40px height)
                int maxHeight = 40;
                int maxWidth = 200; // Cap width too

                height = anim.height;
                width = anim.width;

                // Scale down if too tall
                if (height > maxHeight) {
                    float scale = (float) maxHeight / height;
                    height = maxHeight;
                    width = (int) (width * scale);
                }

                // Scale down if too wide (unlikely with 40px height, but good safety)
                if (width > maxWidth) {
                    float scale = (float) maxWidth / width;
                    width = maxWidth;
                    height = (int) (height * scale);
                }
            }

            // Send with Smart Embedding protocol: [GIF:url:W<width>:H<height>]
            String msg = "[GIF:" + url + ":W" + width + ":H" + height + "]";
            this.minecraft.player.connection.sendChat(msg);
            this.onClose();
        }
    }
}
