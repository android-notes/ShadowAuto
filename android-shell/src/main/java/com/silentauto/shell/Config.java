package com.silentauto.shell;

import java.util.HashMap;
import java.util.Map;

final class Config {
    final int port;
    final String apiKey;
    final String apiBase;
    final String model;

    Config(int port, String apiKey, String apiBase, String model) {
        this.port = port;
        this.apiKey = apiKey;
        this.apiBase = empty(apiBase) ? "https://api.openai.com/v1" : trimSlash(apiBase);
        this.model = empty(model) ? "gpt-4.1-mini" : model;
    }

    Config withOverrides(String key, String base, String model) {
        return new Config(port, empty(key) ? apiKey : key, empty(base) ? apiBase : base, empty(model) ? this.model : model);
    }

    static Config from(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (String arg : args) {
            if (arg != null && arg.startsWith("--")) {
                int i = arg.indexOf('=');
                if (i > 2) {
                    values.put(arg.substring(2, i), arg.substring(i + 1));
                }
            }
        }
        Map<String, String> env = System.getenv();
        int port = parseInt(first(values.get("port"), env.get("SILENT_AUTO_PORT")), 43110);
        return new Config(
                port,
                first(values.get("api-key"), env.get("OPENAI_API_KEY")),
                first(values.get("api-base"), env.get("OPENAI_BASE_URL")),
                first(values.get("model"), env.get("OPENAI_MODEL"))
        );
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String first(String a, String b) {
        return empty(a) ? b : a;
    }

    private static String trimSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
