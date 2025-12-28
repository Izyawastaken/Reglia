package com.example.reglia;

import com.google.gson.Gson;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public DiscordBot() {
        this.client = HttpClient.newHttpClient();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public static void start(MinecraftServer mcServer) {
        server = mcServer;
        if (!Config.hasBotToken()) {
            LOGGER.info("[Reglia] No bot token found.");
            return;
        }
        if (instance != null) instance.shutdown();
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
        if (server != null) start(server);
    }

    public static boolean isConnected() {
        return instance != null && instance.isConnected.get();
    }

    private void connect() {
        try {
            LOGGER.info("[Reglia] Connecting to Gateway...");
            client.newWebSocketBuilder().buildAsync(URI.create(GATEWAY_URL), this);
        } catch (Exception e) {
            LOGGER.error("[Reglia] Connection failed: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void shutdown() {
        isConnected.set(false);
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutdown");
            webSocket = null;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private void scheduleReconnect() {
        if (scheduler == null || scheduler.isShutdown()) return;
        scheduler.schedule(() -> {
            isReconnecting = true;
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        this.isConnected.set(true);
        LOGGER.info("[Reglia] WebSocket Connected!");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            JsonObject json = JsonParser.parseString(data.toString()).getAsJsonObject();
            int op = json.get("op").getAsInt();
            if (json.has("s") && !json.get("s").isJsonNull()) {
                lastSequence = json.get("s").getAsInt();
            }

            switch (op) {
                case 10 -> handleHello(json);
                case 0 -> handleDispatch(json);
                case 7 -> {
                    LOGGER.info("[Reglia] Reconnect requested.");
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnect");
                }
                case 9 -> {
                    LOGGER.warn("[Reglia] Invalid Session.");
                    sessionId = null;
                    lastSequence = null;
                    sendIdentify();
                }
            }
        } catch (Exception e) {
            LOGGER.error("[Reglia] Error: " + e.getMessage());
        }
        webSocket.request(1);
        return null;
    }
    
    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        LOGGER.error("[Reglia] Error: " + error.getMessage());
        isConnected.set(false);
    }
    
    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        LOGGER.info("[Reglia] Closed: " + statusCode);
        isConnected.set(false);
        if (statusCode != 1000 && statusCode != 4004) scheduleReconnect();
        return null;
    }

    private void handleHello(JsonObject json) {
        int interval = json.getAsJsonObject("d").get("heartbeat_interval").getAsInt();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (this.webSocket != null) {
                    JsonObject heartbeat = new JsonObject();
                    heartbeat.addProperty("op", 1);
                    heartbeat.addProperty("d", lastSequence);
                    this.webSocket.sendText(heartbeat.toString(), true);
                }
            } catch (Exception e) {
                LOGGER.error("Heartbeat error: " + e.getMessage());
            }
        }, 0, interval, TimeUnit.MILLISECONDS);

        if (sessionId != null && isReconnecting) sendResume();
        else sendIdentify();
    }

    private void sendIdentify() {
        JsonObject identify = new JsonObject();
        identify.addProperty("op", 2);
        JsonObject d = new JsonObject();
        d.addProperty("token", Config.botToken);
        d.addProperty("intents", 33280);
        JsonObject p = new JsonObject();
        p.addProperty("os", "linux");
        p.addProperty("browser", "Reglia");
        p.addProperty("device", "Reglia");
        d.add("properties", p);
        identify.add("d", d);
        if (webSocket != null) webSocket.sendText(identify.toString(), true);
    }

    private void sendResume() {
        JsonObject resume = new JsonObject();
        resume.addProperty("op", 6);
        JsonObject d = new JsonObject();
        d.addProperty("token", Config.botToken);
        d.addProperty("session_id", sessionId);
        d.addProperty("seq", lastSequence);
        resume.add("d", d);
        if (webSocket != null) webSocket.sendText(resume.toString(), true);
    }

    private void handleDispatch(JsonObject json) {
        String t = json.has("t") && !json.get("t").isJsonNull() ? json.get("t").getAsString() : "";
        JsonObject d = json.getAsJsonObject("d");

        if ("READY".equals(t)) {
            sessionId = d.get("session_id").getAsString();
            String name = d.getAsJsonObject("user").get("username").getAsString();
            LOGGER.info("[Reglia] Ready! User: " + name);
            isReconnecting = false;
            if (server != null) server.execute(() -> broadcast("§a[Reglia] Connected as: " + name));
        } else if ("RESUMED".equals(t)) {
            LOGGER.info("[Reglia] Resumed.");
            isReconnecting = false;
        } else if ("MESSAGE_CREATE".equals(t)) {
            handleMessageCreate(d);
        }
    }

    private void handleMessageCreate(JsonObject d) {
        String content = d.has("content") ? d.get("content").getAsString() : "";
        JsonObject authorObj = d.getAsJsonObject("author");
        String author = authorObj.get("username").getAsString();
        boolean isBot = authorObj.has("bot") && authorObj.get("bot").getAsBoolean();
        String channelId = d.get("channel_id").getAsString();

        if (isBot) return;
        if (Config.hasChannelId() && !Config.channelId.equals(channelId)) return;

        String gifUrl = findGif(d, content);
        if (content.isEmpty() && gifUrl == null) return;

        final String finalContent = content;
        final String finalGif = gifUrl;

        if (server != null) {
            server.execute(() -> {
                String msg = "§9[Discord] §f" + author + "§7: §f" + finalContent;
                if (finalGif != null) msg += " §6[GIF Preview]";
                broadcast(msg);
            });
        }
    }

    private String findGif(JsonObject d, String content) {
        if (d.has("attachments")) {
            for (com.google.gson.JsonElement a : d.getAsJsonArray("attachments")) {
                JsonObject obj = a.getAsJsonObject();
                String url = obj.get("url").getAsString();
                String type = obj.has("content_type") ? obj.get("content_type").getAsString() : "";
                if (type.equals("image/gif") || url.toLowerCase().endsWith(".gif")) return url;
            }
        }
        if (content.contains("tenor.com") || content.contains("giphy.com")) {
            for (String word : content.split("\\s+")) {
                if (word.startsWith("http")) return word;
            }
        }
        return null;
    }
    
    private void broadcast(String text) {
        if (server == null) return;
        Component msg = Component.literal(text);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
}
