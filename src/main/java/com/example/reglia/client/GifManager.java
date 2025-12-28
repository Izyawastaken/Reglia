package com.example.reglia.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GifManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private static final Map<String, GifAnimation> CACHE = new ConcurrentHashMap<>();
    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile("<meta property=\"og:image\" content=\"([^\"]+)\"");
    private static final Pattern TENOR_DIRECT_PATTERN = Pattern.compile("\"contentUrl\":\\s*\"([^\"]+\\.gif)\"");

    public static class GifAnimation {
        public List<ResourceLocation> frames = new ArrayList<>();
        public List<Integer> frameDelays = new ArrayList<>();
        public int totalDuration = 0;
        public int width = 0;
        public int height = 0;
        public boolean loading = true;
    }

    public static GifAnimation getAnimation(String url) {
        return url == null ? null : CACHE.get(url);
    }

    public static ResourceLocation getFrame(String url) {
        if (url == null)
            return null;
        GifAnimation anim = CACHE.get(url);
        if (anim == null) {
            anim = new GifAnimation();
            CACHE.put(url, anim);
            LOGGER.info("[Reglia] First request for GIF: " + url);
            downloadAndProcess(url, anim);
            return null;
        }
        if (anim.loading || anim.frames.isEmpty())
            return null;

        long time = System.currentTimeMillis() % Math.max(1, anim.totalDuration);
        int accumulated = 0;
        for (int i = 0; i < anim.frames.size(); i++) {
            accumulated += anim.frameDelays.get(i);
            if (time < accumulated)
                return anim.frames.get(i);
        }
        return anim.frames.get(0);
    }

    private static void downloadAndProcess(String url, GifAnimation anim) {
        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("[Reglia] Resolving GIF URL: " + url);
                String resolvedUrl = resolveUrl(url);
                LOGGER.info("[Reglia] Downloading from: " + resolvedUrl);

                HttpRequest request = HttpRequest.newBuilder(URI.create(resolvedUrl))
                        .header("User-Agent", "Mozilla/5.0 Reglia Mod")
                        .build();

                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                byte[] data = response.body();

                if (data == null || data.length < 10) {
                    throw new Exception("Empty or invalid data received");
                }

                LOGGER.info("[Reglia] Downloaded " + data.length + " bytes. Processing...");
                Minecraft.getInstance().execute(() -> processGif(url, data, anim));
            } catch (Exception e) {
                LOGGER.error("[Reglia] Failed to download GIF: " + url, e);
                anim.loading = false;
            }
        });
    }

    private static String resolveUrl(String url) throws Exception {
        if (url.contains("tenor.com") || url.contains("giphy.com")) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 Reglia Mod")
                    .build();
            String html = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            // Try og:image first
            Matcher matcher = OG_IMAGE_PATTERN.matcher(html);
            if (matcher.find()) {
                String ogImage = unescape(matcher.group(1));
                if (ogImage.contains(".gif") || ogImage.contains("media.tenor.com"))
                    return ogImage;
            }

            // Try specific Tenor pattern
            matcher = TENOR_DIRECT_PATTERN.matcher(html);
            if (matcher.find())
                return unescape(matcher.group(1));
        }
        return unescape(url);
    }

    private static String unescape(String url) {
        if (url == null)
            return null;
        return url.replace("\\u002F", "/")
                .replace("\\u0026", "&")
                .replace("\\u003D", "=")
                .replace("\\u003F", "?")
                .replace("\\/", "/");
    }

    private static void processGif(String originalUrl, byte[] data, GifAnimation anim) {
        try (InputStream is = new ByteArrayInputStream(data);
                ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                LOGGER.error("[Reglia] No GIF reader found for data of size " + data.length);
                anim.loading = false;
                return;
            }

            ImageReader reader = readers.next();
            reader.setInput(iis);
            int count = reader.getNumImages(true);
            LOGGER.info("[Reglia] Found " + count + " frames in GIF");

            for (int i = 0; i < count; i++) {
                BufferedImage bimg = reader.read(i);
                if (i == 0) {
                    anim.width = bimg.getWidth();
                    anim.height = bimg.getHeight();
                }
                DynamicTexture texture = new DynamicTexture(fromBufferedImage(bimg));
                String texturePath = "reglia_gif_" + Math.abs(originalUrl.hashCode()) + "_" + i;
                ResourceLocation loc = Minecraft.getInstance().getTextureManager().register(texturePath, texture);

                anim.frames.add(loc);
                anim.frameDelays.add(100);
                anim.totalDuration += 100;
            }
            anim.loading = false;
            LOGGER.info("[Reglia] GIF processing complete for " + originalUrl);
        } catch (Exception e) {
            LOGGER.error("[Reglia] Error processing GIF: " + originalUrl, e);
            anim.loading = false;
        }
    }

    private static NativeImage fromBufferedImage(BufferedImage bimg) {
        NativeImage nimg = new NativeImage(bimg.getWidth(), bimg.getHeight(), true);
        for (int y = 0; y < bimg.getHeight(); y++) {
            for (int x = 0; x < bimg.getWidth(); x++) {
                int argb = bimg.getRGB(x, y);
                // Convert ARGB to ABGR (NativeImage format)
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                nimg.setPixelRGBA(x, y, abgr);
            }
        }
        return nimg;
    }
}
