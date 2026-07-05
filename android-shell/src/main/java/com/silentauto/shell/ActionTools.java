package com.silentauto.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Locale;

final class ActionTools {
    private ActionTools() {
    }

    static JsonArray tools() {
        JsonArray tools = new JsonArray();
        tools.add(tool("get_ui_layout", "Return the latest UiAutomation JSON dump for the current virtual display. mode=simple returns compact actionable UI; mode=full returns the complete tree.",
                props(prop("mode", "string", "simple or full"), prop("reason", "string", "why the latest layout is needed")),
                required()));
        tools.add(tool("tap_target", "Click a UI target by targetIndex from the UI dump targets array. This uses Accessibility ACTION_CLICK first and falls back to the target center.",
                props(prop("targetIndex", "integer", "targetIndex from the latest UI targets array"), prop("reason", "string", "why this target should be clicked")),
                required("targetIndex")));
        tools.add(tool("tap", "Tap display-local coordinates on the 720x1280 virtual display only when no targetIndex exists. Use the target node center field from the UI dump.",
                props(prop("x", "integer", "display-local x coordinate, 0..719"), prop("y", "integer", "display-local y coordinate, 0..1279"), prop("reason", "string", "why this tap")),
                required("x", "y")));
        tools.add(tool("long_press", "Long press display-local coordinates. Use only for context menus, selecting text, dragging handles, or UI that explicitly needs press-and-hold.",
                props(prop("x", "integer", "display-local x coordinate, 0..719"), prop("y", "integer", "display-local y coordinate, 0..1279"), prop("durationMs", "integer", "optional press duration; use 0 or omit for default"), prop("reason", "string", "why long press is needed")),
                required("x", "y")));
        tools.add(tool("drag", "Drag from one display-local point to another. Use for sliders, maps, carousels, or drag handles. For normal page scrolling prefer scroll_ui.",
                props(prop("startX", "integer", "start x, 0..719"), prop("startY", "integer", "start y, 0..1279"), prop("endX", "integer", "end x, 0..719"), prop("endY", "integer", "end y, 0..1279"), prop("durationMs", "integer", "optional drag duration; use 0 or omit for default"), prop("reason", "string", "why drag is needed")),
                required("startX", "startY", "endX", "endY")));
        tools.add(tool("scroll_ui", "Scroll the current UI. direction is content direction: down reveals lower content, up reveals upper content, right reveals content to the right, left reveals content to the left.",
                props(propEnum("direction", "content direction, not finger direction", "up", "down", "left", "right"), prop("distance", "integer", "optional display-local pixels; use 0 or omit for default"), prop("reason", "string", "why scrolling is needed")),
                required("direction")));
        tools.add(tool("focus_input", "Focus an input field. Prefer inputIndex from the UI inputs array. If no inputIndex is available, pass query to find an editable field or input-like search box by label, hint, id, or class.",
                props(prop("inputIndex", "integer", "inputIndex from the latest UI inputs array, or 0 if unknown"), prop("query", "string", "optional label or purpose such as search, name, phone, address"), prop("reason", "string", "why this input should be focused")),
                required()));
        tools.add(tool("input_text", "Set the focused input field text to the exact given value. If no field is focused, call focus_input first.",
                props(prop("text", "string", "text to input"), prop("reason", "string", "why this text")),
                required("text")));
        tools.add(tool("set_clipboard", "Copy text into the Android clipboard without pasting it.",
                props(prop("text", "string", "text to copy to clipboard"), prop("reason", "string", "why clipboard is needed")),
                required("text")));
        tools.add(tool("paste_clipboard", "Paste the current clipboard into the focused field.",
                props(prop("reason", "string", "why paste is needed")),
                required()));
        tools.add(tool("copy_selection", "Copy the selected text from the focused UI node to clipboard.",
                props(prop("reason", "string", "why copy is needed")),
                required()));
        tools.add(tool("select_all_text", "Select all text in the focused input field before replacing or deleting it.",
                props(prop("reason", "string", "why all text should be selected")),
                required()));
        tools.add(tool("delete_selection", "Delete the selected text in the focused input field. If no text is selected, delete one character before the cursor.",
                props(prop("reason", "string", "why text should be deleted")),
                required()));
        tools.add(tool("clear_text", "Clear the focused input field. Prefer this over select_all_text plus delete_selection when the whole field should become empty.",
                props(prop("reason", "string", "why the input should be cleared")),
                required()));
        tools.add(tool("press_back", "Press Android back on the virtual display.",
                props(prop("reason", "string", "why back is needed")),
                required()));
        tools.add(tool("press_key", "Press a common hardware or IME key on the virtual display. Use enter or search to submit a focused search/input field.",
                props(propEnum("key", "one of enter, search, tab, dpad_up, dpad_down, dpad_left, dpad_right, dpad_center", "enter", "search", "tab", "dpad_up", "dpad_down", "dpad_left", "dpad_right", "dpad_center"), prop("reason", "string", "why this key is needed")),
                required("key")));
        tools.add(tool("wait", "Wait for UI changes.",
                props(prop("ms", "integer", "milliseconds to wait"), prop("reason", "string", "why waiting is needed")),
                required("ms")));
        tools.add(tool("finish", "Mark the automation goal as complete.",
                props(prop("reason", "string", "completion evidence")),
                required()));
        return tools;
    }

    static boolean isLayoutTool(AiClient.ToolCall call) {
        return "get_ui_layout".equals(call.name.toLowerCase(Locale.US));
    }

    static String layoutMode(JsonObject args) {
        String mode = string(args, "mode", UiBridge.MODE_SIMPLE).toLowerCase(Locale.US);
        if (UiBridge.MODE_FULL.equals(mode)) {
            return UiBridge.MODE_FULL;
        }
        return UiBridge.MODE_SIMPLE;
    }

    static JsonObject toolCallMessage(AiClient.ToolCall call, int index) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.add("content", JsonNull.INSTANCE);
        JsonObject function = new JsonObject();
        function.addProperty("name", call.name);
        function.addProperty("arguments", call.arguments.toString());
        JsonObject item = new JsonObject();
        item.addProperty("id", toolCallId(call, index));
        item.addProperty("type", "function");
        item.add("function", function);
        JsonArray calls = new JsonArray();
        calls.add(item);
        message.add("tool_calls", calls);
        return message;
    }

    static JsonObject toolResultMessage(String callId, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "tool");
        message.addProperty("tool_call_id", callId);
        message.addProperty("content", content);
        return message;
    }

    static String toolCallId(AiClient.ToolCall call, int index) {
        if (!Config.empty(call.id)) {
            return call.id;
        }
        return "call_get_ui_layout_" + index;
    }

    private static JsonObject tool(String name, String description, JsonObject properties, JsonArray required) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", properties);
        parameters.add("required", required);

        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);

        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    private static JsonObject props(JsonObject... props) {
        JsonObject object = new JsonObject();
        for (JsonObject prop : props) {
            object.add(prop.get("name").getAsString(), prop.getAsJsonObject("schema"));
        }
        return object;
    }

    private static JsonObject prop(String name, String type, String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        schema.addProperty("description", description);
        JsonObject object = new JsonObject();
        object.addProperty("name", name);
        object.add("schema", schema);
        return object;
    }

    private static JsonObject propEnum(String name, String description, String... values) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        schema.addProperty("description", description);
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        schema.add("enum", array);
        JsonObject object = new JsonObject();
        object.addProperty("name", name);
        object.add("schema", schema);
        return object;
    }

    private static JsonArray required(String... names) {
        JsonArray array = new JsonArray();
        for (String name : names) {
            array.add(name);
        }
        return array;
    }

    private static String string(JsonObject object, String name, String fallback) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : fallback;
    }
}
