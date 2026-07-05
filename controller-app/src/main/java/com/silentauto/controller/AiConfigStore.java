package com.silentauto.controller;

import android.content.Context;
import android.content.SharedPreferences;

final class AiConfigStore {
    private static final String PREFS = "ai_config";

    private final Context context;

    AiConfigStore(Context context) {
        this.context = context.getApplicationContext();
    }

    AiConfig load() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new AiConfig(
                prefs.getString("apiKey", ""),
                prefs.getString("apiBase", ""),
                prefs.getString("model", "")
        );
    }

    AiConfig save(String key, String base, String selectedModel) {
        AiConfig config = new AiConfig(key.trim(), trimSlash(base), selectedModel.trim());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("apiKey", config.apiKey)
                .putString("apiBase", config.apiBase)
                .putString("model", config.model)
                .apply();
        return config;
    }

    static String trimSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
