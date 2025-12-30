package com.example.reglia;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Common registry for mapping GIF URLs to short IDs.
 * This avoids sending long URLs in chat which cause wrapping issues and
 * breaking detecting.
 * Safe to access from both Server (DiscordBot) and Client (GifManager) threads.
 */
public class GifRegistry {
    private static final Map<Integer, String> ID_TO_URL = new ConcurrentHashMap<>();

    public static int register(String url) {
        if (url == null)
            return -1;
        int id = Math.abs(url.hashCode());
        ID_TO_URL.put(id, url);
        return id;
    }

    public static String getUrl(int id) {
        return ID_TO_URL.get(id);
    }
}
