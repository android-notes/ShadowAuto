package com.silentauto.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class AiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String JSON_SYSTEM_PROMPT = """
            You are a precise Android automation planner. Output valid JSON only.
            """.trim();
    private static final String TOOL_SYSTEM_PROMPT = """
            You operate Android only by calling exactly one provided tool. Do not describe the action.
            """.trim();

    private final Config config;
    private final LogHub logs;
    private final String taskId;
    private volatile Call call;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build();

    AiClient(Config config, LogHub logs) {
        this(config, logs, "");
    }

    AiClient(Config config, LogHub logs, String taskId) {
        this.config = config;
        this.logs = logs;
        this.taskId = taskId == null ? "" : taskId;
    }

    String complete(String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model);
        body.addProperty("stream", true);
        JsonArray messages = new JsonArray();
        messages.add(message("system", JSON_SYSTEM_PROMPT));
        messages.add(message("user", prompt));
        body.add("messages", messages);
        return stream(body).content;
    }

    ToolResult callTools(String prompt, JsonArray tools) throws Exception {
        JsonArray messages = new JsonArray();
        messages.add(message("system", TOOL_SYSTEM_PROMPT));
        messages.add(message("user", prompt));
        return callTools(messages, tools);
    }

    ToolResult callTools(JsonArray messages, JsonArray tools) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model);
        body.addProperty("stream", true);
        body.addProperty("tool_choice", "auto");
        body.add("messages", messages);
        body.add("tools", tools);
        return stream(body);
    }

    private ToolResult stream(JsonObject body) throws Exception {
        Request request = new Request.Builder()
                .url(config.apiBase + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.apiKey)
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        StringBuilder full = new StringBuilder();
        Map<Integer, ToolCallBuilder> tools = new LinkedHashMap<>();
        call = http.newCall(request);
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("AI HTTP " + response.code() + " " + (response.body() == null ? "" : response.body().string()));
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("AI stream cancelled");
                    }
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String token = delta(data, tools);
                    if (!token.isEmpty()) {
                        full.append(token);
                        logs.ai(taskId, token);
                    }
                }
            }
        }
        finally {
            call = null;
        }
        logs.ai(taskId, "\n");
        ToolResult result = new ToolResult();
        result.content = full.toString();
        for (ToolCallBuilder builder : tools.values()) {
            result.toolCalls.add(builder.build());
        }
        return result;
    }

    void cancel() {
        Call active = call;
        if (active != null) {
            active.cancel();
        }
    }

    static JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content);
        return object;
    }

    private static String delta(String data, Map<Integer, ToolCallBuilder> tools) {
        try {
            JsonObject root = JsonParser.parseString(data).getAsJsonObject();
            JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
            if (choice.has("delta")) {
                JsonObject delta = choice.getAsJsonObject("delta");
                readToolCalls(delta.get("tool_calls"), tools);
                return delta.has("content") && !delta.get("content").isJsonNull() ? delta.get("content").getAsString() : "";
            }
            if (choice.has("message")) {
                JsonObject message = choice.getAsJsonObject("message");
                readToolCalls(message.get("tool_calls"), tools);
                return message.has("content") ? message.get("content").getAsString() : "";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static void readToolCalls(JsonElement element, Map<Integer, ToolCallBuilder> tools) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonObject item = array.get(i).getAsJsonObject();
            int index = item.has("index") ? item.get("index").getAsInt() : i;
            ToolCallBuilder builder = tools.get(index);
            if (builder == null) {
                builder = new ToolCallBuilder();
                tools.put(index, builder);
            }
            if (item.has("id") && !item.get("id").isJsonNull()) {
                builder.id = item.get("id").getAsString();
            }
            if (item.has("function") && item.get("function").isJsonObject()) {
                JsonObject fn = item.getAsJsonObject("function");
                if (fn.has("name") && !fn.get("name").isJsonNull()) {
                    builder.name.append(fn.get("name").getAsString());
                }
                if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                    builder.arguments.append(fn.get("arguments").getAsString());
                }
            }
        }
    }

    static final class ToolCall {
        final String id;
        final String name;
        final JsonObject arguments;

        ToolCall(String id, String name, JsonObject arguments) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.arguments = arguments == null ? new JsonObject() : arguments;
        }
    }

    static final class ToolResult {
        String content = "";
        final List<ToolCall> toolCalls = new ArrayList<>();
    }

    private static final class ToolCallBuilder {
        String id = "";
        final StringBuilder name = new StringBuilder();
        final StringBuilder arguments = new StringBuilder();

        ToolCall build() {
            JsonObject args = new JsonObject();
            try {
                String raw = arguments.toString().trim();
                if (!raw.isEmpty()) {
                    args = JsonParser.parseString(raw).getAsJsonObject();
                }
            } catch (Throwable ignored) {
            }
            return new ToolCall(id, name.toString(), args);
        }
    }
}
