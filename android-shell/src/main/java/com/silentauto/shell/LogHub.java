package com.silentauto.shell;

import android.util.Log;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class LogHub {
    private static final String TAG = "ShadowAutoShell";

    interface Sink {
        void send(JsonObject object);
    }

    private final Set<Sink> sinks = new CopyOnWriteArraySet<>();
    private final List<String> ring = new ArrayList<>();

    void add(Sink sink) {
        sinks.add(sink);
    }

    void remove(Sink sink) {
        sinks.remove(sink);
    }

    synchronized List<String> snapshot() {
        return new ArrayList<>(ring);
    }

    void info(String message) {
        info(null, message);
    }

    void info(String taskId, String message) {
        event(taskId, "log", "info", message);
    }

    void error(String message, Throwable t) {
        error(null, message, t);
    }

    void error(String taskId, String message, Throwable t) {
        event(taskId, "log", "error", message + (t == null ? "" : ": " + t.getMessage()));
    }

    void ai(String token) {
        ai(null, token);
    }

    void ai(String taskId, String token) {
        event(taskId, "ai.delta", "info", token);
    }

    void task(String message) {
        task(null, message);
    }

    void task(String taskId, String message) {
        event(taskId, "task", "info", message);
    }

    void logcat(String taskId, String message) {
        String prefix = taskId == null || taskId.isEmpty() ? "" : "[" + taskId + "] ";
        String line = "[info] " + prefix + message;
        System.out.println(line);
        Log.i(TAG, line);
    }

    void frame(int displayId, int width, int height, String jpegBase64) {
        frame(null, displayId, width, height, jpegBase64);
    }

    void frame(String taskId, int displayId, int width, int height, String jpegBase64) {
        JsonObject params = new JsonObject();
        if (taskId != null && !taskId.isEmpty()) {
            params.addProperty("taskId", taskId);
        }
        params.addProperty("type", "frame");
        params.addProperty("displayId", displayId);
        params.addProperty("width", width);
        params.addProperty("height", height);
        params.addProperty("format", "jpeg");
        params.addProperty("data", jpegBase64);
        emit(params);
    }

    private void event(String taskId, String type, String level, String message) {
        String prefix = taskId == null || taskId.isEmpty() ? "" : "[" + taskId + "] ";
        String line = "[" + level + "] " + prefix + message;
        synchronized (this) {
            ring.add(line);
            while (ring.size() > 200) {
                ring.remove(0);
            }
        }
        System.out.println(line);
        if ("error".equals(level)) {
            Log.e(TAG, line);
        } else {
            Log.i(TAG, line);
        }
        JsonObject params = new JsonObject();
        if (taskId != null && !taskId.isEmpty()) {
            params.addProperty("taskId", taskId);
        }
        params.addProperty("type", type);
        params.addProperty("level", level);
        params.addProperty("message", message);
        emit(params);
    }

    private void emit(JsonObject params) {
        JsonObject event = new JsonObject();
        event.addProperty("jsonrpc", "2.0");
        event.addProperty("method", "event");
        event.add("params", params);
        for (Sink sink : sinks) {
            sink.send(event);
        }
    }
}
