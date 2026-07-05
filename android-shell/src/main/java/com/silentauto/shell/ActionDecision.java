package com.silentauto.shell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

final class ActionDecision {
    String packageName = "";
    String action = "wait";
    String reason = "";
    String text = "";
    String query = "";
    String direction = "";
    String key = "";
    int x;
    int y;
    int startX;
    int startY;
    int endX;
    int endY;
    int targetIndex;
    int inputIndex;
    int distance;
    int durationMs;
    int ms = 1000;

    static ActionDecision wait(int ms) {
        return wait(ms, "");
    }

    static ActionDecision wait(int ms, String reason) {
        ActionDecision decision = new ActionDecision();
        decision.action = "wait";
        decision.ms = ms;
        decision.reason = reason == null ? "" : reason;
        return decision;
    }

    static ActionDecision inputText(String text, String reason) {
        ActionDecision decision = new ActionDecision();
        decision.action = "text";
        decision.text = text == null ? "" : text;
        decision.reason = reason == null ? "" : reason;
        return decision;
    }

    static ActionDecision submitSearch(String reason) {
        ActionDecision decision = new ActionDecision();
        decision.action = "submit_search";
        decision.reason = reason == null ? "" : reason;
        return decision;
    }

    static ActionDecision scroll(String direction, int distance, String reason) {
        ActionDecision decision = new ActionDecision();
        decision.action = "scroll";
        decision.direction = direction == null ? "down" : direction;
        decision.distance = distance;
        decision.reason = reason == null ? "" : reason;
        return decision;
    }

    static ActionDecision fromTool(AiClient.ToolCall call) {
        ActionDecision decision = new ActionDecision();
        JsonObject args = call.arguments;
        String name = call.name.toLowerCase(Locale.US);
        decision.reason = string(args, "reason");
        if ("get_ui_layout".equals(name)) {
            decision.action = "wait";
            decision.ms = 300;
        } else if ("tap_target".equals(name)) {
            decision.action = "tap_target";
            decision.targetIndex = integer(args, "targetIndex", 0);
        } else if ("tap".equals(name)) {
            decision.action = "tap";
            decision.x = integer(args, "x", 0);
            decision.y = integer(args, "y", 0);
        } else if ("long_press".equals(name)) {
            decision.action = "long_press";
            decision.x = integer(args, "x", 0);
            decision.y = integer(args, "y", 0);
            decision.durationMs = integer(args, "durationMs", 550);
        } else if ("drag".equals(name)) {
            decision.action = "drag";
            decision.startX = integer(args, "startX", 0);
            decision.startY = integer(args, "startY", 0);
            decision.endX = integer(args, "endX", 0);
            decision.endY = integer(args, "endY", 0);
            decision.durationMs = integer(args, "durationMs", 450);
        } else if ("scroll_ui".equals(name)) {
            decision.action = "scroll";
            decision.direction = string(args, "direction", "down");
            decision.distance = integer(args, "distance", 0);
        } else if ("focus_input".equals(name)) {
            decision.action = "focus_input";
            decision.inputIndex = integer(args, "inputIndex", 0);
            decision.query = string(args, "query");
        } else if ("input_text".equals(name)) {
            decision.action = "text";
            decision.text = string(args, "text");
        } else if ("set_clipboard".equals(name)) {
            decision.action = "set_clipboard";
            decision.text = string(args, "text");
        } else if ("paste_clipboard".equals(name)) {
            decision.action = "paste";
        } else if ("copy_selection".equals(name)) {
            decision.action = "copy";
        } else if ("select_all_text".equals(name)) {
            decision.action = "select_all";
        } else if ("delete_selection".equals(name)) {
            decision.action = "delete";
        } else if ("clear_text".equals(name)) {
            decision.action = "clear_text";
        } else if ("press_back".equals(name)) {
            decision.action = "back";
        } else if ("press_key".equals(name)) {
            decision.action = "key";
            decision.key = string(args, "key");
        } else if ("finish".equals(name)) {
            decision.action = "done";
        } else {
            decision.action = "wait";
            decision.ms = integer(args, "ms", 1000);
        }
        if ("wait".equals(name)) {
            decision.ms = integer(args, "ms", 1000);
        }
        return decision;
    }

    static ActionDecision from(String raw) {
        ActionDecision decision = new ActionDecision();
        try {
            JsonObject object = JsonParser.parseString(jsonObject(raw)).getAsJsonObject();
            decision.packageName = string(object, "packageName");
            decision.action = string(object, "action", decision.action).toLowerCase(Locale.US);
            if ("select_all_text".equals(decision.action)) {
                decision.action = "select_all";
            } else if ("delete_selection".equals(decision.action)) {
                decision.action = "delete";
            } else if ("scroll_ui".equals(decision.action) || "swipe".equals(decision.action)) {
                decision.action = "scroll";
            } else if ("input_text".equals(decision.action)) {
                decision.action = "text";
            } else if ("paste_clipboard".equals(decision.action)) {
                decision.action = "paste";
            } else if ("copy_selection".equals(decision.action)) {
                decision.action = "copy";
            } else if ("press_back".equals(decision.action)) {
                decision.action = "back";
            } else if ("press_key".equals(decision.action)) {
                decision.action = "key";
            } else if ("finish".equals(decision.action)) {
                decision.action = "done";
            }
            decision.reason = string(object, "reason");
            decision.text = string(object, "text");
            decision.direction = string(object, "direction", "down");
            decision.key = string(object, "key");
            decision.x = integer(object, "x", 0);
            decision.y = integer(object, "y", 0);
            decision.startX = integer(object, "startX", 0);
            decision.startY = integer(object, "startY", 0);
            decision.endX = integer(object, "endX", 0);
            decision.endY = integer(object, "endY", 0);
            decision.targetIndex = integer(object, "targetIndex", 0);
            decision.inputIndex = integer(object, "inputIndex", 0);
            decision.query = string(object, "query");
            decision.distance = integer(object, "distance", 0);
            decision.durationMs = integer(object, "durationMs", 0);
            decision.ms = integer(object, "ms", 1000);
        } catch (Throwable ignored) {
            decision.reason = truncate(raw, 160);
        }
        return decision;
    }

    private static String jsonObject(String raw) {
        int start = raw == null ? -1 : raw.indexOf('{');
        int end = raw == null ? -1 : raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "{}";
        }
        return raw.substring(start, end + 1);
    }

    private static String string(JsonObject object, String name) {
        return string(object, name, "");
    }

    private static String string(JsonObject object, String name, String fallback) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : fallback;
    }

    private static int integer(JsonObject object, String name, int fallback) {
        try {
            return object.has(name) ? object.get(name).getAsInt() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
