package com.silentauto.shell;

import android.media.MediaFormat;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
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

    void videoConfig(String taskId, MediaFormat format) {
        JsonObject params = new JsonObject();
        if (taskId != null && !taskId.isEmpty()) {
            params.addProperty("taskId", taskId);
        }
        params.addProperty("type", "video.config");
        params.addProperty("mime", format.getString(MediaFormat.KEY_MIME));
        params.addProperty("width", format.getInteger(MediaFormat.KEY_WIDTH));
        params.addProperty("height", format.getInteger(MediaFormat.KEY_HEIGHT));
        addBuffer(params, format, "csd-0", "csd0");
        addBuffer(params, format, "csd-1", "csd1");
        emit(params);
    }

    void videoSample(String taskId, long ptsUs, int flags, String h264Base64) {
        JsonObject params = new JsonObject();
        if (taskId != null && !taskId.isEmpty()) {
            params.addProperty("taskId", taskId);
        }
        params.addProperty("type", "video.sample");
        params.addProperty("ptsUs", ptsUs);
        params.addProperty("flags", flags);
        params.addProperty("data", h264Base64);
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

    private static void addBuffer(JsonObject params, MediaFormat format, String key, String name) {
        try {
            if (!format.containsKey(key)) {
                return;
            }
            ByteBuffer buffer = format.getByteBuffer(key);
            if (buffer == null) {
                return;
            }
            ByteBuffer copy = buffer.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            params.addProperty(name, Base64.encodeToString(bytes, Base64.NO_WRAP));
        } catch (Throwable ignored) {
        }
    }
}
