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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

public class GifManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private static final Map<String, GifAnimation> CACHE = new ConcurrentHashMap<>();
    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile("<meta property=\"og:image\" content=\"([^\"]+)\"");
    private static final Pattern TENOR_DIRECT_PATTERN = Pattern.compile("\"contentUrl\":\\s*\"([^\"]+\\.gif)\"");

    // Cache configuration
    private static final Path CACHE_DIR = Paths.get("config", "reglia-cache");
    private static final long MAX_CACHE_SIZE_BYTES = 500 * 1024 * 1024; // 500MB

    static {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (Exception e) {
            LOGGER.error("[Reglia] Failed to create cache directory", e);
        }
    }

    // Limit concurrent downloads to prevent lag
    private static final java.util.concurrent.Semaphore DOWNLOAD_LIMITER = new java.util.concurrent.Semaphore(3);

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
                // Check disk cache first
                byte[] cachedData = loadFromDisk(url);
                if (cachedData != null) {
                    LOGGER.info("[Reglia] Loaded GIF from disk cache: " + url);
                    processGifData(url, cachedData, anim);
                    return;
                }

                // Limit concurrent downloads to prevent lag
                DOWNLOAD_LIMITER.acquire();
                try {
                    // Check cache size and clean if needed before downloading new stuff
                    checkCacheSizeAndClean();

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

                    // Save to disk cache
                    saveToDisk(url, data);

                    LOGGER.info("[Reglia] Downloaded " + data.length + " bytes. Processing OFF render thread...");
                    processGifData(url, data, anim);

                } finally {
                    DOWNLOAD_LIMITER.release();
                }
            } catch (Exception e) {
                LOGGER.error("[Reglia] Failed to download GIF: " + url, e);
                anim.loading = false;
            }
        });
    }

    private static void processGifData(String url, byte[] data, GifAnimation anim) {
        try {
            // Process frames on THIS async thread (not render thread!)
            List<NativeImage> processedFrames = processGifFrames(url, data, anim);

            // Only register textures on render thread (lightweight)
            if (!processedFrames.isEmpty()) {
                Minecraft.getInstance().execute(() -> {
                    for (int i = 0; i < processedFrames.size(); i++) {
                        DynamicTexture texture = new DynamicTexture(processedFrames.get(i));
                        String texturePath = "reglia_gif_" + Math.abs(url.hashCode()) + "_" + i;
                        ResourceLocation loc = Minecraft.getInstance().getTextureManager().register(texturePath,
                                texture);
                        anim.frames.add(loc);
                        anim.frameDelays.add(100);
                        anim.totalDuration += 100;
                    }
                    anim.loading = false;
                    LOGGER.info("[Reglia] Registered " + processedFrames.size() + " textures for " + url);
                });
            } else {
                anim.loading = false;
            }
        } catch (Exception e) {
            LOGGER.error("[Reglia] Error processing GIF data for " + url, e);
            anim.loading = false;
        }
    }

    // --- Cache Management ---

    private static String getCacheKey(String url) {
        return Integer.toHexString(url.hashCode()) + ".gif";
    }

    private static void saveToDisk(String url, byte[] data) {
        try {
            Path file = CACHE_DIR.resolve(getCacheKey(url));
            Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("[Reglia] Failed to save to cache", e);
        }
    }

    private static byte[] loadFromDisk(String url) {
        try {
            Path file = CACHE_DIR.resolve(getCacheKey(url));
            if (Files.exists(file)) {
                // Update modification time for LRU cleanup
                Files.setLastModifiedTime(file,
                        java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                return Files.readAllBytes(file);
            }
        } catch (Exception e) {
            LOGGER.warn("[Reglia] Failed to read from cache", e);
        }
        return null;
    }

    public static long getCacheSize() {
        try {
            if (!Files.exists(CACHE_DIR))
                return 0;
            try (Stream<Path> walk = Files.walk(CACHE_DIR)) {
                return walk.filter(p -> p.toFile().isFile())
                        .mapToLong(p -> p.toFile().length())
                        .sum();
            }
        } catch (Exception e) {
            LOGGER.error("[Reglia] Failed to calculate cache size", e);
            return 0;
        }
    }

    public static void clearCache() {
        try {
            if (Files.exists(CACHE_DIR)) {
                try (Stream<Path> walk = Files.walk(CACHE_DIR)) {
                    walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(java.io.File::delete);
                }
                Files.createDirectories(CACHE_DIR);
                LOGGER.info("[Reglia] Cache cleared successfully");
            }
        } catch (Exception e) {
            LOGGER.error("[Reglia] Failed to clear cache", e);
        }
    }

    private static void checkCacheSizeAndClean() {
        if (getCacheSize() > MAX_CACHE_SIZE_BYTES) {
            LOGGER.info("[Reglia] Cache size limit exceeded (>500MB). Clearing cache...");
            clearCache();
        }
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

    private static List<NativeImage> processGifFrames(String originalUrl, byte[] data, GifAnimation anim) {
        List<NativeImage> frames = new ArrayList<>();
        try (InputStream is = new ByteArrayInputStream(data);
                ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                LOGGER.error("[Reglia] No GIF reader found for data of size " + data.length);
                return frames;
            }

            ImageReader reader = readers.next();
            reader.setInput(iis);

            int count = 0;
            try {
                count = reader.getNumImages(true);
            } catch (Exception e) {
                LOGGER.warn("[Reglia] Failed to count images: " + e.getMessage());
                count = 0;
            }

            LOGGER.info("[Reglia] Found " + count + " frames in GIF");

            // Now that processing is off render thread, we can handle more frames!
            int maxFrames = 120; // Increased from 60 for smoother playback
            int frameStep = 1;
            if (count > maxFrames) {
                frameStep = (int) Math.ceil((double) count / maxFrames);
                LOGGER.info("[Reglia] Downsampling from " + count + " to ~" + (count / frameStep) + " frames");
            }

            if (count > 0) {
                // Read first frame to get dimensions
                BufferedImage first = reader.read(0);
                anim.width = first.getWidth();
                anim.height = first.getHeight();

                // Create master canvas for compositing
                BufferedImage master = new BufferedImage(anim.width, anim.height, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2d = master.createGraphics();
                g2d.setBackground(new java.awt.Color(0, 0, 0, 0));

                for (int i = 0; i < count; i += frameStep) {
                    BufferedImage frame = reader.read(i);

                    // Get frame metadata for position and disposal
                    int frameX = 0, frameY = 0;
                    String disposal = "none";
                    try {
                        javax.imageio.metadata.IIOMetadata meta = reader.getImageMetadata(i);
                        org.w3c.dom.Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                        org.w3c.dom.NodeList children = root.getChildNodes();
                        for (int c = 0; c < children.getLength(); c++) {
                            org.w3c.dom.Node node = children.item(c);
                            if ("ImageDescriptor".equals(node.getNodeName())) {
                                org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
                                if (attrs.getNamedItem("imageLeftPosition") != null)
                                    frameX = Integer.parseInt(attrs.getNamedItem("imageLeftPosition").getNodeValue());
                                if (attrs.getNamedItem("imageTopPosition") != null)
                                    frameY = Integer.parseInt(attrs.getNamedItem("imageTopPosition").getNodeValue());
                            }
                            if ("GraphicControlExtension".equals(node.getNodeName())) {
                                org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
                                if (attrs.getNamedItem("disposalMethod") != null)
                                    disposal = attrs.getNamedItem("disposalMethod").getNodeValue();
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    // Handle disposal BEFORE drawing this frame
                    if ("restoreToBackgroundColor".equals(disposal)) {
                        g2d.clearRect(frameX, frameY, frame.getWidth(), frame.getHeight());
                    }

                    // Draw frame at correct position
                    g2d.drawImage(frame, frameX, frameY, null);

                    // Convert to NativeImage (still on async thread - this is the heavy part)
                    frames.add(fromBufferedImage(master));

                    // Handle disposal AFTER capturing (for next frame)
                    if ("restoreToBackgroundColor".equals(disposal)) {
                        g2d.clearRect(frameX, frameY, frame.getWidth(), frame.getHeight());
                    }
                }
                g2d.dispose();
            }
            LOGGER.info("[Reglia] Processed " + frames.size() + " frames for " + originalUrl);
        } catch (Exception e) {
            LOGGER.error("[Reglia] Error processing GIF: " + originalUrl, e);
        }
        return frames;
    }

    // Tenor Public Key (LIVDSRZULELA is the standard public key for integrations)
    private static final String TENOR_KEY = "LIVDSRZULELA";
    private static final String TENOR_TRENDING = "https://g.tenor.com/v1/trending?key=" + TENOR_KEY + "&limit=20";
    private static final String TENOR_SEARCH = "https://g.tenor.com/v1/search?key=" + TENOR_KEY + "&limit=20&q=";

    public record GifEntry(String url, String previewUrl) {
    }

    public static CompletableFuture<List<GifEntry>> getTrending() {
        return fetchTenor(TENOR_TRENDING);
    }

    public static CompletableFuture<List<GifEntry>> searchTenor(String query) {
        String encoded = query.replace(" ", "%20");
        return fetchTenor(TENOR_SEARCH + encoded);
    }

    private static CompletableFuture<List<GifEntry>> fetchTenor(String apiUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                        .header("User-Agent", "Mozilla/5.0 Reglia Mod")
                        .build();
                String json = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

                List<GifEntry> results = new ArrayList<>();
                try {
                    com.google.gson.JsonObject root = new com.google.gson.Gson().fromJson(json,
                            com.google.gson.JsonObject.class);
                    if (root.has("results")) {
                        com.google.gson.JsonArray arr = root.getAsJsonArray("results");
                        for (com.google.gson.JsonElement el : arr) {
                            try {
                                com.google.gson.JsonObject obj = el.getAsJsonObject();
                                if (obj.has("media")) {
                                    com.google.gson.JsonArray mediaArr = obj.getAsJsonArray("media");
                                    if (mediaArr.size() > 0) {
                                        com.google.gson.JsonObject media = mediaArr.get(0).getAsJsonObject();
                                        if (media.has("gif")) {
                                            com.google.gson.JsonObject gif = media.getAsJsonObject("gif");
                                            if (gif.has("url")) {
                                                String url = gif.get("url").getAsString();
                                                results.add(new GifEntry(url, url));
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Skip malformed entry
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("[Reglia] Failed to parse Tenor JSON", e);
                }
                return results;
            } catch (Exception e) {
                LOGGER.error("[Reglia] Tenor API failed", e);
                return Collections.emptyList();
            }
        });
    }

    private static NativeImage fromBufferedImage(BufferedImage bimg) {
        int w = bimg.getWidth();
        int h = bimg.getHeight();
        NativeImage nimg = new NativeImage(w, h, true);

        // Bulk read all pixels at once (much faster than per-pixel getRGB)
        int[] pixels = bimg.getRGB(0, 0, w, h, null, 0, w);

        // Convert ARGB to ABGR in place and write
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int a = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            int abgr = (a << 24) | (b << 16) | (g << 8) | r;

            int x = i % w;
            int y = i / w;
            nimg.setPixelRGBA(x, y, abgr);
        }
        return nimg;
    }
}
