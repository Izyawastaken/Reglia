package com.example.reglia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.awt.Desktop;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Embedded HTTP server for the Reglia config panel.
 * Serves the config UI and provides REST API for config operations.
 */
public class ConfigServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_PORT = 25580;

    private static HttpServer server;
    private static boolean running = false;

    /**
     * Start the config server and open browser
     */
    public static void startAndOpenBrowser() {
        if (running) {
            openBrowser();
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("localhost", DEFAULT_PORT), 0);

            // Serve the config HTML page
            server.createContext("/", new StaticHandler());

            // API endpoints
            server.createContext("/api/config", new ConfigApiHandler());
            server.createContext("/api/status", new StatusApiHandler());
            server.createContext("/api/players", new PlayersApiHandler());
            server.createContext("/api/logs", new LogsApiHandler());
            server.createContext("/api/cache/clear", new CacheClearHandler());
            server.createContext("/api/cache/size", new CacheSizeHandler());

            server.setExecutor(null);
            server.start();
            running = true;

            LOGGER.info("[Reglia] Config server started on http://localhost:{}", DEFAULT_PORT);
            openBrowser();

        } catch (IOException e) {
            LOGGER.error("[Reglia] Failed to start config server", e);
        }
    }

    /**
     * Stop the config server
     */
    public static void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            LOGGER.info("[Reglia] Config server stopped");
        }
    }

    /**
     * Open the config page in default browser
     */
    private static void openBrowser() {
        String url = "http://localhost:" + DEFAULT_PORT;

        try {
            // Try Desktop.browse first
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                LOGGER.info("[Reglia] Opened browser via Desktop API");
                return;
            }
        } catch (Exception e) {
            LOGGER.debug("[Reglia] Desktop.browse failed: {}", e.getMessage());
        }

        // Fallback: use OS-specific commands
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }

            pb.start();
            LOGGER.info("[Reglia] Opened browser via system command");
        } catch (Exception e) {
            LOGGER.warn("[Reglia] Could not auto-open browser. Visit: {}", url);
        }
    }

    public static String getUrl() {
        return "http://localhost:" + DEFAULT_PORT;
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * Handler for serving the static HTML config page
     */
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if ("/".equals(path) || "/index.html".equals(path)) {
                serveHtml(exchange);
            } else {
                send404(exchange);
            }
        }

        private void serveHtml(HttpExchange exchange) throws IOException {
            try (InputStream is = getClass().getResourceAsStream("/assets/reglia/config.html")) {
                if (is == null) {
                    LOGGER.error("[Reglia] config.html not found in resources");
                    send500(exchange, "Config page not found");
                    return;
                }

                byte[] content = is.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            }
        }

        private void send404(HttpExchange exchange) throws IOException {
            String response = "Not Found";
            exchange.sendResponseHeaders(404, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }

        private void send500(HttpExchange exchange, String message) throws IOException {
            exchange.sendResponseHeaders(500, message.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(message.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Handler for config API (GET/POST)
     */
    static class ConfigApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Enable CORS
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                handleGetConfig(exchange);
            } else if ("POST".equals(exchange.getRequestMethod())) {
                handlePostConfig(exchange);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private void handleGetConfig(HttpExchange exchange) throws IOException {
            ConfigResponse response = new ConfigResponse();
            response.webhookUrl = Config.webhookUrl;
            // SECURITY: Never expose the actual bot token in API response
            // Only indicate if a token is set, show masked value
            response.botToken = Config.hasBotToken() ? "••••••••••••••••" : "";
            response.channelId = Config.channelId;
            response.bridgeEnabled = Config.bridgeEnabled;
            response.sendDeaths = Config.sendDeaths;
            response.sendJoinLeave = Config.sendJoinLeave;

            sendJson(exchange, response);
        }

        private void handlePostConfig(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                ConfigRequest req = GSON.fromJson(body, ConfigRequest.class);

                if (req.webhookUrl != null)
                    Config.setWebhookUrl(req.webhookUrl);
                if (req.botToken != null)
                    Config.setBotToken(req.botToken);
                if (req.channelId != null)
                    Config.setChannelId(req.channelId);
                if (req.bridgeEnabled != null)
                    Config.setBridgeEnabled(req.bridgeEnabled);
                if (req.sendDeaths != null)
                    Config.setSendDeaths(req.sendDeaths);
                if (req.sendJoinLeave != null)
                    Config.setSendJoinLeave(req.sendJoinLeave);

                // Restart bot if token changed
                if (req.botToken != null && !req.botToken.isEmpty()) {
                    DiscordBot.restart();
                }

                sendJson(exchange, new SuccessResponse("Configuration saved"));
                LOGGER.info("[Reglia] Config updated via web panel");
            } catch (Exception e) {
                LOGGER.error("[Reglia] Error parsing config request", e);
                sendJson(exchange, new ErrorResponse("Invalid request: " + e.getMessage()));
            }
        }

        private void sendJson(HttpExchange exchange, Object obj) throws IOException {
            String json = GSON.toJson(obj);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Handler for status API
     */
    static class StatusApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            StatusResponse status = new StatusResponse();
            status.webhookConfigured = Config.hasWebhook();
            status.botConnected = DiscordBot.isConnected();
            status.bridgeEnabled = Config.bridgeEnabled;

            String json = GSON.toJson(status);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // Request/Response DTOs
    static class ConfigRequest {
        String webhookUrl;
        String botToken;
        String channelId;
        Boolean bridgeEnabled;
        Boolean sendDeaths;
        Boolean sendJoinLeave;
    }

    static class ConfigResponse {
        String webhookUrl;
        String botToken;
        String channelId;
        boolean bridgeEnabled;
        boolean sendDeaths;
        boolean sendJoinLeave;
    }

    static class StatusResponse {
        boolean webhookConfigured;
        boolean botConnected;
        boolean bridgeEnabled;
    }

    static class SuccessResponse {
        String message;
        boolean success = true;

        SuccessResponse(String msg) {
            this.message = msg;
        }
    }

    static class ErrorResponse {
        String error;
        boolean success = false;

        ErrorResponse(String err) {
            this.error = err;
        }
    }

    /**
     * Handler for players API - returns online players
     */
    static class PlayersApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            java.util.List<String> players = new java.util.ArrayList<>();
            if (DiscordBot.getServer() != null) {
                DiscordBot.getServer().getPlayerList().getPlayers().forEach(p -> players.add(p.getName().getString()));
            }

            String json = GSON.toJson(java.util.Map.of("players", players, "count", players.size()));
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Handler for logs API - returns recent Reglia logs
     */
    static class LogsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            String json = GSON.toJson(java.util.Map.of("logs", logBuffer));
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    // Log buffer for UI
    private static final java.util.List<LogEntry> logBuffer = java.util.Collections.synchronizedList(
            new java.util.ArrayList<>());
    private static final int MAX_LOG_SIZE = 100;

    public static void addLog(String level, String message) {
        // SECURITY: Never log actual token values
        String sanitized = message.replaceAll("token[=:]\\s*\\S+", "token=••••••••");

        logBuffer.add(new LogEntry(level, sanitized));
        if (logBuffer.size() > MAX_LOG_SIZE) {
            logBuffer.remove(0);
        }
    }

    static class LogEntry {
        String time;
        String level;
        String message;

        LogEntry(String level, String message) {
            this.time = java.time.LocalTime.now().toString().substring(0, 8);
            this.level = level;
            this.message = message;
        }
    }

    /**
     * Handler for clearing GIF cache
     */
    static class CacheClearHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if ("POST".equals(exchange.getRequestMethod())) {
                com.example.reglia.client.GifManager.clearCache();
                String json = GSON.toJson(new SuccessResponse("Cache cleared"));
                exchange.sendResponseHeaders(200, json.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    /**
     * Handler for getting cache size
     */
    static class CacheSizeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            long size = com.example.reglia.client.GifManager.getCacheSize();
            String json = GSON.toJson(java.util.Map.of("size", size));

            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
