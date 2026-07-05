package com.silentauto.controller;

final class AiConfig {
    final String apiKey;
    final String apiBase;
    final String model;

    AiConfig(String apiKey, String apiBase, String model) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiBase = apiBase == null ? "" : apiBase;
        this.model = model == null ? "" : model;
    }

    boolean isConfigured() {
        return !apiKey.trim().isEmpty()
                && !apiBase.trim().isEmpty()
                && !model.trim().isEmpty();
    }
}
