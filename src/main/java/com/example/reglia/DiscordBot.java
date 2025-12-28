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
        this.scheduler = Executors.newScheduledThreadPool(2); // Heartbeat + Reconnect
    }

    public static void start(MinecraftServer mcServer) {
        server = mcServer;
        if (!Config.hasBotToken()) {
            LOGGER.info("[Reglia] No bot token execution. Use /setbottoken");
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
            LOGGER.info("[Reglia] Connecting to Discord Gateway...");
            client.newWebSocketBuilder()
                    .buildAsync(URI.create(GATEWAY_URL), this);
        } catch (Exception e) {
            LOGGER.error("[Reglia] Failed to connect: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void shutdown() {
        isConnected.set(false);
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutting down");
            webSocket = null;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private void scheduleReconnect() {
        if (scheduler.isShutdown()) return;
        LOGGER.info("[Reglia] Reconnecting in 5 seconds...");
        scheduler.schedule(() -> {
            isReconnecting = true;
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        this.isConnected.set(true);
        LOGGER.info("[Reglia] WebSocket Connection Opened!");
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
                case 10: // Hello
                    handleHello(json);
                    break;
                case 0: // Dispatch
                    handleDispatch(json);
                    break;
                case 7: // Reconnect
                    LOGGER.info("[Reglia] Server requested reconnect.");
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnect requested");
                    break;
                case 9: // Invalid Session
                    LOGGER.warn("[Reglia] Invalid Session. Clearing session and identifying.");
                    sessionId = null;  // Cannot update
                    lastSequence = null;
                    sendIdentify(); 
                    break;
                case 11: // Heartbeat ACK
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("[Reglia] Payload Error: " + e.getMessage());
        }
        webSocket.request(1);
        return null;
    }
    
    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        LOGGER.error("[Reglia] WebSocket Error: " + error.getMessage());
        isConnected.set(false);
    }
    
    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        LOGGER.info("[Reglia] Closed: " + statusCode + " - " + reason);
        isConnected.set(false);
        
        // Don't reconnect if intentional shutdown (1000) or auth failure (4004)
        if (statusCode != 1000 && statusCode != 4004) {
            scheduleReconnect();
        }
        return null;
    }

    private void handleHello(JsonObject json) {
        int heartbeatInterval = json.getAsJsonObject("d").get("heartbeat_interval").getAsInt();
        LOGGER.info("[Reglia] Heartbeat interval: " + heartbeatInterval + "ms");
        
        // Heartbeat Loop
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (this.webSocket != null) {
                    JsonObject heartbeat = new JsonObject();
                    heartbeat.addProperty("op", 1);
                    heartbeat.addProperty("d", lastSequence);
                    this.webSocket.sendText(heartbeat.toString(), true);
                }
            } catch (Exception e) {
                LOGGER.error("Heartbeat failed: " + e.getMessage());
            }
        }, 0, heartbeatInterval, TimeUnit.MILLISECONDS);

        // Resume if we have session, otherwise Identify
        if (sessionId != null && isReconnecting) {
            sendResume();
        } else {
            sendIdentify();
        }
    }

    private void sendIdentify() {
        LOGGER.info("[Reglia] Sending Identify...");
        JsonObject identify = new JsonObject();
        identify.addProperty("op", 2);
        
        JsonObject d = new JsonObject();
        d.addProperty("token", Config.botToken);
        d.addProperty("intents", 33280); 
        
        JsonObject properties = new JsonObject();
        properties.addProperty("os", "linux");
        properties.addProperty("browser", "RegliaMod");
        properties.addProperty("device", "RegliaMod");
        d.add("properties", properties);
        identify.add("d", d);
        
        webSocket.sendText(identify.toString(), true);
    }

    private void sendResume() {
        LOGGER.info("[Reglia] Resuming session " + sessionId);
        JsonObject resume = new JsonObject();
        resume.addProperty("op", 6);
        
        JsonObject d = new JsonObject();
        d.addProperty("token", Config.botToken);
        d.addProperty("session_id", sessionId);
        d.addProperty("seq", lastSequence);
        resume.add("d", d);
        
        webSocket.sendText(resume.toString(), true);
    }

    private void handleDispatch(JsonObject json) {
        String t = json.has("t") && !json.get("t").isJsonNull() ? json.get("t").getAsString() : "";
        JsonObject d = json.getAsJsonObject("d");

        if ("READY".equals(t)) {
            sessionId = d.get("session_id").getAsString();
            String username = d.getAsJsonObject("user").get("username").getAsString();
            LOGGER.info("[Reglia] Ready! Session: " + sessionId + " | User: " + username);
            isReconnecting = false;

            if (server != null) {
                server.execute(() -> broadcast("§a[Reglia] Connected as: " + username));
            }
        }
        else if ("RESUMED".equals(t)) {
            LOGGER.info("[Reglia] Session Resumed Successfully!");
            isReconnecting = false;
        }
        else if ("MESSAGE_CREATE".equals(t)) {
            String content = d.has("content") ? d.get("content").getAsString() : "";
            JsonObject authorObj = d.getAsJsonObject("author");
            String author = authorObj.get("username").getAsString();
            
            boolean isBot = authorObj.has("bot") && authorObj.get("bot").getAsBoolean();
            String channelId = d.get("channel_id").getAsString();

            if (isBot) return;
            if (Config.hasChannelId() && !Config.channelId.equals(channelId)) return;
            if (content.isEmpty()) return;

            LOGGER.info("[Discord->MC] " + author + ": " + content);

            if (server != null) {
                server.execute(() -> broadcast("§9[Discord] §f" + author + "§7: §f" + content));
            }
        }
    }
    
    private void broadcast(String text) {
        Component msg = Component.literal(text);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
}
