package com.newsbot;

/** Reads configuration from environment variables (set via Jenkins credentials / job env). */
final class Config {
    private Config() {}

    static String get(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    static String require(String key) {
        String v = get(key, null);
        if (v == null) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return v;
    }

    static int getInt(String key, int def) {
        String v = get(key, null);
        return v == null ? def : Integer.parseInt(v);
    }

    static boolean getBool(String key, boolean def) {
        String v = get(key, null);
        return v == null ? def : Boolean.parseBoolean(v);
    }
}
