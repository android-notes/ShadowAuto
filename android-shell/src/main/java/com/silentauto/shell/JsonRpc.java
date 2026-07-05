package com.silentauto.shell;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

final class JsonRpc {
    static final Gson GSON = new Gson();

    private JsonRpc() {
    }

    static JsonElement id(JsonObject request) {
        return request.get("id");
    }

    static JsonObject params(JsonObject request) {
        return request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params") : new JsonObject();
    }

    static String text(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
    }

    static JsonObject result(JsonElement id, String value) {
        JsonObject result = new JsonObject();
        result.addProperty("value", value);
        return result(id, result);
    }

    static JsonObject result(JsonElement id, JsonElement value) {
        JsonObject object = envelope(id);
        object.add("result", value);
        return object;
    }

    static JsonObject error(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message == null ? "error" : message);
        JsonObject object = envelope(id);
        object.add("error", error);
        return object;
    }

    static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonObject envelope(JsonElement id) {
        JsonObject object = new JsonObject();
        object.addProperty("jsonrpc", "2.0");
        if (id != null) {
            object.add("id", id);
        }
        return object;
    }
}
