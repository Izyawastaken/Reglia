package com.example.reglia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native Java WebSocket Discord Gateway client.
 * Connects to Discord and relays messages to Minecraft.
 * No external Discord libraries required.
 */
public class DiscordBot implements WebSocket.Listener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json";

    private static DiscordBot instance;
    private static MinecraftServer server;

    private final HttpClient client;
    private WebSocket webSocket;
    private ScheduledExecutorService scheduler;

    private String sessionId;
    private Integer lastSequence = null;
    private boolean isReconnecting = false;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final StringBuilder messageBuffer = new StringBuilder();

    public DiscordBot() {
        this.client = HttpClient.newHttpClient();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public static void start(MinecraftServer mcServer) {
        server = mcServer;
        if (!Config.hasBotToken()) {
            LOGGER.info("[Reglia] No bot token configured - Discord bot disabled");
            return;
        }
        if (instance != null)
            instance.shutdown();
        instance = new DiscordBot();
        instance.connect();
    }

    public static void stop() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public static void restart() {
        stop();
        if (server != null)
            start(server);
    }

    public static boolean isConnected() {
        return instance != null && instance.isConnected.get();
    }

    private void connect() {
        try {
            LOGGER.info("[Reglia] Connecting to Discord Gateway...");
            client.newWebSocketBuilder().buildAsync(URI.create(GATEWAY_URL), this);
        } catch (Exception e) {
            LOGGER.error("[Reglia] Connection failed: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void shutdown() {
        isConnected.set(false);
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutdown");
            } catch (Exception ignored) {
            }
            webSocket = null;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private void scheduleReconnect() {
        if (scheduler == null || scheduler.isShutdown())
            return;
        scheduler.schedule(() -> {
            isReconnecting = true;
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        this.isConnected.set(true);
        LOGGER.info("[Reglia] WebSocket connected!");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);
        if (last) {
            String msg = messageBuffer.toString();
            messageBuffer.setLength(0);
            try {
                JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
                int op = json.get("op").getAsInt();

                if (json.has("s") && !json.get("s").isJsonNull()) {
                    lastSequence = json.get("s").getAsInt();
                }

                switch (op) {
                    case 10 -> handleHello(json);
                    case 0 -> handleDispatch(json);
                    case 7 -> {
                        LOGGER.info("[Reglia] Reconnect requested");
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnect");
                    }
                    case 9 -> {
                        LOGGER.warn("[Reglia] Invalid session");
                        sessionId = null;
                        lastSequence = null;
                        sendIdentify();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[Reglia] Parse error: {}", e.getMessage());
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        LOGGER.error("[Reglia] WebSocket error: {}", error.getMessage());
        isConnected.set(false);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
        LOGGER.info("[Reglia] WebSocket closed: {} - {}", code, reason);
        isConnected.set(false);
        if (code != 1000 && code != 4004)
            scheduleReconnect();
        return null;
    }

    private void handleHello(JsonObject json) {
        int interval = json.getAsJsonObject("d").get("heartbeat_interval").getAsInt();
        scheduler.scheduleAtFixedRate(() -> {
            if (this.webSocket != null) {
                try {
                    JsonObject hb = new JsonObject();
                    hb.addProperty("op", 1);
                    hb.addProperty("d", lastSequence);
                    this.webSocket.sendText(hb.toString(), true);
                } catch (Exception e) {
                    LOGGER.error("[Reglia] Heartbeat error: {}", e.getMessage());
                }
            }
        }, 0, interval, TimeUnit.MILLISECONDS);

        if (sessionId != null && isReconnecting)
            sendResume();
        else
            sendIdentify();
    }

    private void sendIdentify() {
        JsonObject identify = new JsonObject();
        identify.addProperty("op", 2);
        JsonObject d = new JsonObject();
        d.addProperty("token", Config.botToken);
        d.addProperty("intents", 33280); // MESSAGE_CONTENT + GUILD_MESSAGES
        JsonObject p = new JsonObject();
        p.addProperty("os", "minecraft");
        p.addProperty("browser", "Reglia");
        p.addProperty("device", "Reglia");
        d.add("properties", p);
        identify.add("d", d);
        if (webSocket != null)
            webSocket.sendText(identify.toString(), true);
    }

    private void sendResume() {
        JsonObject resume = new JsonObject();
        resume.addProperty("op", 6);
        JsonObject d = new JsonObject();
        d.addProperty("token", Config.botToken);
        d.addProperty("session_id", sessionId);
        d.addProperty("seq", lastSequence);
        resume.add("d", d);
        if (webSocket != null)
            webSocket.sendText(resume.toString(), true);
    }

    private void handleDispatch(JsonObject json) {
        String t = json.has("t") && !json.get("t").isJsonNull() ? json.get("t").getAsString() : "";
        JsonObject d = json.getAsJsonObject("d");

        switch (t) {
            case "READY" -> {
                sessionId = d.get("session_id").getAsString();
                String name = d.getAsJsonObject("user").get("username").getAsString();
                LOGGER.info("[Reglia] Connected as: {}", name);
                isReconnecting = false;
                if (server != null) {
                    server.execute(() -> broadcast("§a[Reglia] Discord bot connected as: " + name));
                }
            }
            case "RESUMED" -> {
                LOGGER.info("[Reglia] Session resumed");
                isReconnecting = false;
            }
            case "MESSAGE_CREATE" -> handleMessageCreate(d);
        }
    }

    private void handleMessageCreate(JsonObject d) {
        String content = d.has("content") ? d.get("content").getAsString() : "";
        JsonObject author = d.getAsJsonObject("author");
        String username = author.get("username").getAsString();
        boolean isBot = author.has("bot") && author.get("bot").getAsBoolean();
        String channelId = d.get("channel_id").getAsString();

        if (isBot)
            return;
        if (Config.hasChannelId() && !Config.channelId.equals(channelId))
            return;

        List<String> gifUrls = findAllGifs(d, content);
        if (content.isEmpty() && gifUrls.isEmpty())
            return;

        final String finalContent = content;
        final List<String> finalGifs = gifUrls;

        if (server != null) {
            server.execute(() -> {
                String msgText = finalContent;
                for (String gif : finalGifs) {
                    msgText = msgText.replace(gif, "").trim();
                }

                String msg = "§9[Discord] §f" + username + "§7: §f" + msgText;
                for (String gif : finalGifs) {
                    // Register URL to get a short ID, preventing chat wrap issues
                    // 9 newlines reserves 81px for max 80px GIF height
                    int id = GifRegistry.register(gif);
                    msg += "\n[GIF:ID:" + id + "]\n\n\n\n\n\n\n\n\n";
                }
                broadcast(msg);
            });
        }
    }

    private List<String> findAllGifs(JsonObject d, String content) {
        List<String> gifs = new ArrayList<>();

        // Check attachments
        if (d.has("attachments")) {
            for (JsonElement a : d.getAsJsonArray("attachments")) {
                JsonObject obj = a.getAsJsonObject();
                if (obj.has("url")) {
                    String url = obj.get("url").getAsString();
                    String type = obj.has("content_type") ? obj.get("content_type").getAsString() : "";
                    if (type.equals("image/gif") || url.toLowerCase().endsWith(".gif")) {
                        if (!gifs.contains(url))
                            gifs.add(url);
                    }
                }
            }
        }

        // Check embeds
        if (d.has("embeds")) {
            for (JsonElement e : d.getAsJsonArray("embeds")) {
                JsonObject embed = e.getAsJsonObject();
                if (embed.has("url")) {
                    String url = embed.get("url").getAsString();
                    if (url.contains("tenor.com") || url.contains("giphy.com") || url.toLowerCase().endsWith(".gif")) {
                        if (!gifs.contains(url))
                            gifs.add(url);
                    }
                }
                if (embed.has("thumbnail")) {
                    String thumb = embed.getAsJsonObject("thumbnail").get("url").getAsString();
                    if (thumb.toLowerCase().endsWith(".gif") && !gifs.contains(thumb)) {
                        gifs.add(thumb);
                    }
                }
            }
        }

        // Check content for links
        if (content.contains("http")) {
            for (String word : content.split("\\s+")) {
                if (word.startsWith("http")) {
                    if (word.contains("tenor.com") || word.contains("giphy.com")
                            || word.toLowerCase().endsWith(".gif")) {
                        if (!gifs.contains(word))
                            gifs.add(word);
                    }
                }
            }
        }

        return gifs;
    }

    private void broadcast(String text) {
        if (server == null)
            return;
        Component msg = Component.literal(text);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
}
