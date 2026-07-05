package com.silentauto.shell;

import android.annotation.SuppressLint;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.IUiAutomationConnection;
import android.app.UiAutomation;
import android.app.UiAutomationConnection;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.HandlerThread;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.google.gson.Gson;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.silentauto.shell.UiNodeUtils.*;

final class UiBridge {
    static final String MODE_SIMPLE = "simple";
    static final String MODE_FULL = "full";
    private static final Gson GSON = new Gson();

    private final HandlerThread thread = new HandlerThread("ui-automation");
    private final LogHub logs;
    private UiAutomation automation;

    UiBridge(LogHub logs) {
        this.logs = logs;
    }

    @SuppressLint("SoonBlockedPrivateApi")
    void connect() {
        try {
            thread.start();
            automation = UiAutomation.class
                    .getConstructor(android.os.Looper.class, IUiAutomationConnection.class)
                    .newInstance(thread.getLooper(), new UiAutomationConnection());
            try {
                UiAutomation.class.getDeclaredMethod("connect").invoke(automation);
            } catch (NoSuchMethodException ignored) {
                UiAutomation.class.getDeclaredMethod("connect", int.class).invoke(automation, 0);
            }
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
            logs.info("UiAutomation connected");
        } catch (Throwable e) {
            throw new IllegalStateException("UiAutomation connect failed", e);
        }
    }

    String dump(int displayId) {
        return dump(displayId, 0, 0);
    }

    String dump(int displayId, int width, int height) {
        return dump(displayId, width, height, MODE_SIMPLE);
    }

    String dump(int displayId, int width, int height, String mode) {
        DumpContext context = new DumpContext(MODE_FULL.equals(mode) ? MODE_FULL : MODE_SIMPLE);
        WindowSnapshot snapshot = windowSnapshot(displayId);
        Map<String, Object> out = object();
        out.put("displayId", displayId);
        out.put("width", width);
        out.put("height", height);
        out.put("mode", context.mode);
        out.put("windowSource", snapshot.source);
        out.put("windowError", snapshot.error);
        out.put("availableDisplays", snapshot.availableDisplays);
        out.put("coordinateSpace", "display-local");

        ArrayList<Object> windows = new ArrayList<>();
        try {
            int windowIndex = 0;
            for (AccessibilityWindowInfo window : orderedWindows(snapshot.windows, true)) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) {
                    windowIndex++;
                    continue;
                }
                windows.add(windowMap(window, root, context, windowIndex));
                windowIndex++;
            }
        } catch (Throwable e) {
            Map<String, Object> error = object();
            error.put("error", e.getMessage());
            windows.add(error);
        }
        out.put("windows", windows);
        out.put("inputs", inputMaps(context.inputs));
        out.put("targets", targetMaps(context.targets));
        return GSON.toJson(out);
    }

    FocusResult focusInput(int displayId, int inputIndex, String query) {
        try {
            FocusResult result = inputIndex > 0 ? findInputByIndex(displayId, inputIndex) : null;
            if (result == null && !empty(query)) {
                result = findInputByQuery(displayId, query);
            }
            if (result == null) {
                result = findCurrentOrFirstInput(displayId);
            }
            if (result == null) {
                result = findInputLikeTarget(displayId, query);
            }
            if (result != null) {
                result.accessibility = performInputFocus(result.node);
            }
            return result;
        } catch (Throwable e) {
            logs.info("focus input by accessibility failed: " + e.getMessage());
            return null;
        }
    }

    ClickResult clickTarget(int displayId, int targetIndex) {
        try {
            TargetCounter counter = new TargetCounter(targetIndex);
            int windowIndex = 0;
            for (AccessibilityWindowInfo window : orderedWindows(displayId, true)) {
                AccessibilityNodeInfo root = window.getRoot();
                ClickResult result = findTarget(root, counter, "w" + windowIndex);
                if (result != null) {
                    result.clicked = performTargetClick(result.node);
                    return result;
                }
                windowIndex++;
            }
        } catch (Throwable e) {
            logs.info("tap target by accessibility failed: " + e.getMessage());
        }
        return null;
    }

    ClickResult clickBestSubmitTarget(int displayId, int width, int height) {
        try {
            SubmitCandidate best = null;
            int windowIndex = 0;
            for (AccessibilityWindowInfo window : orderedWindows(displayId, true)) {
                AccessibilityNodeInfo root = window.getRoot();
                SubmitCandidate candidate = bestSubmitTarget(root, "w" + windowIndex, width, height, window.getType());
                if (candidate != null && (best == null || candidate.score > best.score)) {
                    best = candidate;
                }
                windowIndex++;
            }
            if (best == null) {
                return null;
            }
            ClickResult result = new ClickResult(-1, best.x, best.y, best.label, best.path, best.node);
            result.clicked = performTargetClick(best.node);
            return result;
        } catch (Throwable e) {
            logs.info("click submit target by accessibility failed: " + e.getMessage());
            return null;
        }
    }

    boolean hasInputMethodWindow(int displayId) {
        try {
            for (AccessibilityWindowInfo window : orderedWindows(displayId, true)) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return true;
                }
            }
        } catch (Throwable e) {
            logs.info("detect input method window failed: " + e.getMessage());
        }
        return false;
    }

    boolean setText(int displayId, String text) {
        try {
            AccessibilityNodeInfo editable = findEditable(displayId);
            if (editable == null) {
                return false;
            }
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text == null ? "" : text);
            return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Throwable e) {
            logs.info("set text by accessibility failed: " + e.getMessage());
            return false;
        }
    }

    boolean paste(int displayId) {
        try {
            AccessibilityNodeInfo editable = findEditable(displayId);
            if (editable == null) {
                return false;
            }
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            return editable.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        } catch (Throwable e) {
            logs.info("paste by accessibility failed: " + e.getMessage());
            return false;
        }
    }

    boolean copySelection(int displayId) {
        try {
            AccessibilityNodeInfo editable = findEditable(displayId);
            if (editable != null && editable.performAction(AccessibilityNodeInfo.ACTION_COPY)) {
                return true;
            }
            for (AccessibilityWindowInfo window : orderedWindows(displayId, false)) {
                AccessibilityNodeInfo root = window.getRoot();
                if (copySelection(root)) {
                    return true;
                }
            }
        } catch (Throwable e) {
            logs.info("copy by accessibility failed: " + e.getMessage());
        }
        return false;
    }

    boolean selectAll(int displayId) {
        try {
            AccessibilityNodeInfo editable = findEditable(displayId);
            if (editable == null) {
                return false;
            }
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            CharSequence text = editable.getText();
            int length = text == null ? 0 : text.length();
            if (length == 0) {
                return true;
            }
            return setSelection(editable, 0, length);
        } catch (Throwable e) {
            logs.info("select all by accessibility failed: " + e.getMessage());
            return false;
        }
    }

    boolean deleteSelection(int displayId) {
        try {
            AccessibilityNodeInfo editable = findEditable(displayId);
            if (editable == null) {
                return false;
            }
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            CharSequence value = editable.getText();
            String text = value == null ? "" : value.toString();
            if (text.isEmpty()) {
                return true;
            }
            int start = editable.getTextSelectionStart();
            int end = editable.getTextSelectionEnd();
            if (start < 0 || end < 0 || start == end) {
                return false;
            }
            int from = Math.max(0, Math.min(start, end));
            int to = Math.min(text.length(), Math.max(start, end));
            Bundle args = new Bundle();
            String updated = text.substring(0, from) + text.substring(to);
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
            if (!editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return false;
            }
            setSelection(editable, from, from);
            return true;
        } catch (Throwable e) {
            logs.info("delete selection by accessibility failed: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private WindowSnapshot windowSnapshot(int displayId) {
        WindowSnapshot snapshot = new WindowSnapshot();
        try {
            Method method = UiAutomation.class.getMethod("getWindowsOnAllDisplays");
            SparseArray<List<AccessibilityWindowInfo>> all = (SparseArray<List<AccessibilityWindowInfo>>) method.invoke(automation);
            snapshot.source = "getWindowsOnAllDisplays";
            for (int i = 0; i < all.size(); i++) {
                snapshot.availableDisplays.add(all.keyAt(i));
            }
            List<AccessibilityWindowInfo> result = all.get(displayId);
            if (result != null) {
                snapshot.windows.addAll(result);
                return snapshot;
            }
            snapshot.error = "no windows for display " + displayId;
            return snapshot;
        } catch (NoSuchMethodException e) {
            snapshot.source = "getWindowsFiltered";
        } catch (Throwable e) {
            snapshot.source = "getWindowsOnAllDisplays";
            snapshot.error = e.getMessage();
            logs.info("getWindowsOnAllDisplays failed: " + e.getMessage());
            return snapshot;
        }

        try {
            for (AccessibilityWindowInfo window : automation.getWindows()) {
                int windowDisplayId = windowDisplayId(window);
                if (windowDisplayId >= 0) {
                    addUnique(snapshot.availableDisplays, windowDisplayId);
                }
                if (windowDisplayId == displayId) {
                    snapshot.windows.add(window);
                }
            }
            if (snapshot.windows.isEmpty()) {
                snapshot.error = "no verified windows for display " + displayId + "; unfiltered getWindows was ignored";
                logs.info(snapshot.error);
            }
        } catch (Throwable e) {
            snapshot.error = e.getMessage();
            logs.info("getWindows fallback failed: " + e.getMessage());
        }
        return snapshot;
    }

    private List<AccessibilityWindowInfo> orderedWindows(int displayId, boolean includeIme) {
        return orderedWindows(windowSnapshot(displayId).windows, includeIme);
    }

    private List<AccessibilityWindowInfo> orderedWindows(List<AccessibilityWindowInfo> windows, boolean includeIme) {
        ArrayList<AccessibilityWindowInfo> list = new ArrayList<>();
        for (AccessibilityWindowInfo window : windows) {
            if (includeIme || window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                list.add(window);
            }
        }
        list.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        return list;
    }

    private int rank(AccessibilityWindowInfo window) {
        int type = window.getType();
        if (type == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return 0;
        }
        if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            return 2;
        }
        return 1;
    }

    private AccessibilityNodeInfo findEditable(int displayId) throws Exception {
        AccessibilityNodeInfo fallback = null;
        for (AccessibilityWindowInfo window : orderedWindows(displayId, false)) {
            AccessibilityNodeInfo root = window.getRoot();
            AccessibilityNodeInfo focused = findEditable(root, true);
            if (focused != null) {
                return focused;
            }
            if (fallback == null) {
                fallback = findEditable(root, false);
            }
        }
        return fallback;
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node, boolean requireFocus) {
        if (node == null) {
            return null;
        }
        if (isInputNode(node) && (!requireFocus || node.isFocused())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditable(node.getChild(i), requireFocus);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private FocusResult findInputByIndex(int displayId, int inputIndex) throws Exception {
        InputCounter counter = new InputCounter(inputIndex);
        int windowIndex = 0;
        for (AccessibilityWindowInfo window : orderedWindows(displayId, false)) {
            AccessibilityNodeInfo root = window.getRoot();
            FocusResult result = findInputByIndex(root, counter, "w" + windowIndex);
            if (result != null) {
                return result;
            }
            windowIndex++;
        }
        return null;
    }

    private FocusResult findInputByIndex(AccessibilityNodeInfo node, InputCounter counter, String path) {
        if (node == null) {
            return null;
        }
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (isInputNode(node)) {
            counter.current++;
            if (counter.current == counter.inputIndex) {
                return new FocusResult(counter.current, -1, centerX(rect), centerY(rect), label(node), path, true, node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            FocusResult result = findInputByIndex(node.getChild(i), counter, path + "/" + i);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private FocusResult findInputByQuery(int displayId, String query) throws Exception {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return null;
        }
        InputCounter counter = new InputCounter(0);
        int windowIndex = 0;
        for (AccessibilityWindowInfo window : orderedWindows(displayId, false)) {
            AccessibilityNodeInfo root = window.getRoot();
            FocusResult result = findInputByQuery(root, counter, "w" + windowIndex, normalized);
            if (result != null) {
                return result;
            }
            windowIndex++;
        }
        return null;
    }

    private FocusResult findInputByQuery(AccessibilityNodeInfo node, InputCounter counter, String path, String query) {
        if (node == null) {
            return null;
        }
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (isInputNode(node)) {
            counter.current++;
            if (matchesInputQuery(node, query)) {
                return new FocusResult(counter.current, -1, centerX(rect), centerY(rect), label(node), path, true, node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            FocusResult result = findInputByQuery(node.getChild(i), counter, path + "/" + i, query);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private FocusResult findCurrentOrFirstInput(int displayId) throws Exception {
        AccessibilityNodeInfo fallback = null;
        Rect fallbackRect = null;
        String fallbackPath = "";
        int fallbackIndex = -1;
        InputCounter counter = new InputCounter(0);
        int windowIndex = 0;
        for (AccessibilityWindowInfo window : orderedWindows(displayId, false)) {
            AccessibilityNodeInfo root = window.getRoot();
            FocusResult focused = findCurrentOrFirstInput(root, counter, "w" + windowIndex, new FirstInput());
            if (focused != null && focused.focused) {
                return focused;
            }
            if (focused != null && fallback == null) {
                fallback = focused.node;
                fallbackRect = new Rect(focused.x, focused.y, focused.x, focused.y);
                fallbackPath = focused.path;
                fallbackIndex = focused.inputIndex;
            }
            windowIndex++;
        }
        if (fallback == null) {
            return null;
        }
        return new FocusResult(fallbackIndex, -1, fallbackRect.left, fallbackRect.top, label(fallback), fallbackPath, true, fallback);
    }

    private FocusResult findCurrentOrFirstInput(AccessibilityNodeInfo node, InputCounter counter, String path, FirstInput first) {
        if (node == null) {
            return null;
        }
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (isInputNode(node)) {
            counter.current++;
            FocusResult current = new FocusResult(counter.current, -1, centerX(rect), centerY(rect), label(node), path, true, node);
            current.focused = node.isFocused();
            if (node.isFocused()) {
                return current;
            }
            if (first.result == null) {
                first.result = current;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            FocusResult result = findCurrentOrFirstInput(node.getChild(i), counter, path + "/" + i, first);
            if (result != null && result.focused) {
                return result;
            }
        }
        return first.result;
    }

    private FocusResult findInputLikeTarget(int displayId, String query) throws Exception {
        String normalized = normalize(query);
        TargetCounter counter = new TargetCounter(0);
        int windowIndex = 0;
        for (AccessibilityWindowInfo window : orderedWindows(displayId, false)) {
            AccessibilityNodeInfo root = window.getRoot();
            FocusResult result = findInputLikeTarget(root, counter, "w" + windowIndex, normalized);
            if (result != null) {
                return result;
            }
            windowIndex++;
        }
        return null;
    }

    private FocusResult findInputLikeTarget(AccessibilityNodeInfo node, TargetCounter counter, String path, String query) {
        if (node == null) {
            return null;
        }
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (isTarget(node)) {
            counter.current++;
            if (looksLikeInputLauncher(node, query)) {
                return new FocusResult(-1, counter.current, centerX(rect), centerY(rect), label(node), path, false, node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            FocusResult result = findInputLikeTarget(node.getChild(i), counter, path + "/" + i, query);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private boolean copySelection(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (node.isFocused() && node.performAction(AccessibilityNodeInfo.ACTION_COPY)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (copySelection(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean setSelection(AccessibilityNodeInfo node, int start, int end) {
        Bundle args = new Bundle();
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start);
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
    }

    private Map<String, Object> windowMap(AccessibilityWindowInfo window, AccessibilityNodeInfo root, DumpContext context, int windowIndex) {
        Rect rect = new Rect();
        window.getBoundsInScreen(rect);
        Map<String, Object> out = object();
        out.put("index", windowIndex);
        out.put("displayId", windowDisplayId(window));
        out.put("type", window.getType());
        out.put("layer", window.getLayer());
        out.put("active", window.isActive());
        out.put("focused", window.isFocused());
        out.put("title", text(window.getTitle()));
        out.put("bounds", boundsValue(rect));
        out.put("root", nodeMap(root, context, "w" + windowIndex));
        return out;
    }

    private Map<String, Object> nodeMap(AccessibilityNodeInfo node, DumpContext context, String path) {
        if (node == null) {
            return null;
        }

        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        Target target = null;
        if (isTarget(node)) {
            target = context.addTarget(node, rect, path);
        }
        InputTarget input = null;
        if (isInputNode(node)) {
            input = context.addInput(node, rect, path);
        }

        if (context.full()) {
            Map<String, Object> out = fullNodeMap(node, rect, target, input);
            ArrayList<Object> children = new ArrayList<>();
            for (int i = 0; i < node.getChildCount(); i++) {
                children.add(nodeMap(node.getChild(i), context, path + "/" + i));
            }
            out.put("children", children);
            return out;
        }

        ArrayList<Map<String, Object>> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            Map<String, Object> child = nodeMap(node.getChild(i), context, path + "/" + i);
            if (child != null) {
                children.add(child);
            }
        }

        if (target == null && !hasUsefulContent(node) && children.isEmpty()) {
            return null;
        }

        Map<String, Object> out = simpleNodeMap(node, rect, target, input);
        out.put("children", children);
        return out;
    }

    private Map<String, Object> fullNodeMap(AccessibilityNodeInfo node, Rect rect, Target target, InputTarget input) {
        Map<String, Object> out = commonNodeMap(node, rect, target, input);
        out.put("package", text(node.getPackageName()));
        out.put("longClickable", node.isLongClickable());
        out.put("focusable", node.isFocusable());
        out.put("selected", node.isSelected());
        out.put("checked", node.isChecked());
        out.put("checkable", node.isCheckable());
        out.put("password", node.isPassword());
        out.put("childCount", node.getChildCount());
        out.put("selectionStart", node.getTextSelectionStart());
        out.put("selectionEnd", node.getTextSelectionEnd());
        out.put("actions", actionList(node));
        return out;
    }

    private Map<String, Object> simpleNodeMap(AccessibilityNodeInfo node, Rect rect, Target target, InputTarget input) {
        return commonNodeMap(node, rect, target, input);
    }

    private Map<String, Object> commonNodeMap(AccessibilityNodeInfo node, Rect rect, Target target, InputTarget input) {
        Map<String, Object> out = object();
        out.put("class", text(node.getClassName()));
        out.put("label", label(node));
        out.put("text", text(node.getText()));
        out.put("desc", text(node.getContentDescription()));
        out.put("hint", text(hint(node)));
        out.put("id", text(node.getViewIdResourceName()));
        out.put("clickable", node.isClickable());
        out.put("enabled", node.isEnabled());
        out.put("editable", isEditable(node));
        out.put("focused", node.isFocused());
        out.put("scrollable", node.isScrollable());
        out.put("visible", node.isVisibleToUser());
        out.put("bounds", boundsValue(rect));
        out.put("center", centerValue(rect));
        out.put("targetIndex", target == null ? -1 : target.index);
        out.put("inputIndex", input == null ? -1 : input.index);
        return out;
    }

    private ArrayList<Map<String, Object>> inputMaps(ArrayList<InputTarget> inputs) {
        ArrayList<Map<String, Object>> out = new ArrayList<>();
        for (InputTarget input : inputs) {
            Map<String, Object> item = object();
            item.put("inputIndex", input.index);
            item.put("path", input.path);
            item.put("label", input.label);
            item.put("class", input.className);
            item.put("id", input.id);
            item.put("text", input.text);
            item.put("hint", input.hint);
            item.put("focused", input.focused);
            item.put("password", input.password);
            item.put("selectionStart", input.selectionStart);
            item.put("selectionEnd", input.selectionEnd);
            item.put("bounds", boundsValue(input.bounds));
            item.put("center", input.x + "," + input.y);
            out.add(item);
        }
        return out;
    }

    private ArrayList<Map<String, Object>> targetMaps(ArrayList<Target> targets) {
        ArrayList<Map<String, Object>> out = new ArrayList<>();
        for (Target target : targets) {
            Map<String, Object> item = object();
            item.put("targetIndex", target.index);
            item.put("path", target.path);
            item.put("label", target.label);
            item.put("class", target.className);
            item.put("id", target.id);
            item.put("bounds", boundsValue(target.bounds));
            item.put("center", target.x + "," + target.y);
            item.put("actions", target.actions);
            out.add(item);
        }
        return out;
    }

    private ClickResult findTarget(AccessibilityNodeInfo node, TargetCounter counter, String path) {
        if (node == null) {
            return null;
        }
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (isTarget(node)) {
            counter.current++;
            if (counter.current == counter.targetIndex) {
                return new ClickResult(counter.current, centerX(rect), centerY(rect), label(node), path, node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            ClickResult result = findTarget(node.getChild(i), counter, path + "/" + i);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private SubmitCandidate bestSubmitTarget(AccessibilityNodeInfo node, String path, int width, int height, int windowType) {
        if (node == null) {
            return null;
        }
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        SubmitCandidate best = null;
        if (isTarget(node)) {
            int score = submitScore(node, rect, width, height, windowType);
            if (score > 0) {
                best = new SubmitCandidate(score, centerX(rect), centerY(rect), label(node), path, node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            SubmitCandidate child = bestSubmitTarget(node.getChild(i), path + "/" + i, width, height, windowType);
            if (child != null && (best == null || child.score > best.score)) {
                best = child;
            }
        }
        return best;
    }

    private int submitScore(AccessibilityNodeInfo node, Rect rect, int width, int height, int windowType) {
        if (isEditable(node)) {
            return -1;
        }
        String label = normalize(label(node));
        String haystack = normalize(label + " " + node.getViewIdResourceName() + " " + node.getClassName() + " " + actionList(node));
        if (haystack.isEmpty()) {
            return -1;
        }
        if (haystack.contains("cancel") || haystack.contains("clear") || haystack.contains("delete") || haystack.contains("back")
                || haystack.contains("取消") || haystack.contains("清除") || haystack.contains("删除") || haystack.contains("返回")) {
            return -1;
        }
        if ((haystack.contains("search bar") || haystack.contains("search field") || haystack.contains("搜索栏") || haystack.contains("输入框"))
                && !exactSubmitLabel(label)) {
            return -1;
        }
        int score = 0;
        if (exactSubmitLabel(label)) {
            score += 120;
        }
        if (containsSubmitWord(haystack)) {
            score += 70;
        }
        if (windowType == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            score += 30;
        }
        int centerX = centerX(rect);
        int centerY = centerY(rect);
        if (width > 0 && centerX > width * 3 / 5) {
            score += 12;
        }
        if (height > 0 && centerY > height * 7 / 10) {
            score += 18;
        }
        return score >= 70 ? score : -1;
    }

    private boolean exactSubmitLabel(String value) {
        return "search".equals(value)
                || "go".equals(value)
                || "done".equals(value)
                || "enter".equals(value)
                || "submit".equals(value)
                || "搜索".equals(value)
                || "查找".equals(value)
                || "查询".equals(value)
                || "提交".equals(value)
                || "确定".equals(value)
                || "完成".equals(value);
    }

    private boolean containsSubmitWord(String value) {
        return value.contains("search")
                || value.contains("submit")
                || value.contains("go")
                || value.contains("done")
                || value.contains("ime_action")
                || value.contains("搜索")
                || value.contains("查找")
                || value.contains("查询")
                || value.contains("提交")
                || value.contains("确定")
                || value.contains("完成");
    }

    private boolean performTargetClick(AccessibilityNodeInfo node) {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        if (isEditable(node) && node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            return true;
        }
        return node.isFocusable() && node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    }

    private boolean performInputFocus(AccessibilityNodeInfo node) {
        boolean focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (focused || clicked) {
            return true;
        }
        return node.isFocusable() && node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    }

    static final class ClickResult {
        final int targetIndex;
        final int x;
        final int y;
        final String label;
        final String path;
        private final AccessibilityNodeInfo node;
        boolean clicked;

        ClickResult(int targetIndex, int x, int y, String label, String path, AccessibilityNodeInfo node) {
            this.targetIndex = targetIndex;
            this.x = x;
            this.y = y;
            this.label = label == null ? "" : label;
            this.path = path == null ? "" : path;
            this.node = node;
        }
    }

    static final class FocusResult {
        final int inputIndex;
        final int targetIndex;
        final int x;
        final int y;
        final String label;
        final String path;
        final boolean editable;
        private final AccessibilityNodeInfo node;
        boolean accessibility;
        boolean focused;

        FocusResult(int inputIndex, int targetIndex, int x, int y, String label, String path, boolean editable, AccessibilityNodeInfo node) {
            this.inputIndex = inputIndex;
            this.targetIndex = targetIndex;
            this.x = x;
            this.y = y;
            this.label = label == null ? "" : label;
            this.path = path == null ? "" : path;
            this.editable = editable;
            this.node = node;
        }
    }

    private static final class SubmitCandidate {
        final int score;
        final int x;
        final int y;
        final String label;
        final String path;
        final AccessibilityNodeInfo node;

        SubmitCandidate(int score, int x, int y, String label, String path, AccessibilityNodeInfo node) {
            this.score = score;
            this.x = x;
            this.y = y;
            this.label = label == null ? "" : label;
            this.path = path == null ? "" : path;
            this.node = node;
        }
    }

    private final class DumpContext {
        final String mode;
        final ArrayList<Target> targets = new ArrayList<>();
        final ArrayList<InputTarget> inputs = new ArrayList<>();

        DumpContext(String mode) {
            this.mode = mode;
        }

        boolean full() {
            return MODE_FULL.equals(mode);
        }

        Target addTarget(AccessibilityNodeInfo node, Rect rect, String path) {
            Target target = new Target(
                    targets.size() + 1,
                    path,
                    label(node),
                    node.getClassName() == null ? "" : node.getClassName().toString(),
                    node.getViewIdResourceName(),
                    rect,
                    centerX(rect),
                    centerY(rect),
                    targetActions(node)
            );
            targets.add(target);
            return target;
        }

        InputTarget addInput(AccessibilityNodeInfo node, Rect rect, String path) {
            InputTarget input = new InputTarget(
                    inputs.size() + 1,
                    path,
                    label(node),
                    node.getClassName() == null ? "" : node.getClassName().toString(),
                    node.getViewIdResourceName(),
                    node.getText() == null ? "" : node.getText().toString(),
                    hint(node) == null ? "" : hint(node).toString(),
                    node.isFocused(),
                    node.isPassword(),
                    node.getTextSelectionStart(),
                    node.getTextSelectionEnd(),
                    rect,
                    centerX(rect),
                    centerY(rect)
            );
            inputs.add(input);
            return input;
        }
    }

    private static final class TargetCounter {
        final int targetIndex;
        int current;

        TargetCounter(int targetIndex) {
            this.targetIndex = targetIndex;
        }
    }

    private static final class InputCounter {
        final int inputIndex;
        int current;

        InputCounter(int inputIndex) {
            this.inputIndex = inputIndex;
        }
    }

    private static final class FirstInput {
        FocusResult result;
    }

    private static final class WindowSnapshot {
        String source = "";
        String error = "";
        final ArrayList<Integer> availableDisplays = new ArrayList<>();
        final ArrayList<AccessibilityWindowInfo> windows = new ArrayList<>();
    }

    private static final class Target {
        final int index;
        final String path;
        final String label;
        final String className;
        final String id;
        final Rect bounds;
        final int x;
        final int y;
        final ArrayList<String> actions;

        Target(int index, String path, String label, String className, String id, Rect bounds, int x, int y, ArrayList<String> actions) {
            this.index = index;
            this.path = path == null ? "" : path;
            this.label = label == null ? "" : label;
            this.className = className == null ? "" : className;
            this.id = id == null ? "" : id;
            this.bounds = new Rect(bounds);
            this.x = x;
            this.y = y;
            this.actions = actions;
        }
    }

    private static final class InputTarget {
        final int index;
        final String path;
        final String label;
        final String className;
        final String id;
        final String text;
        final String hint;
        final boolean focused;
        final boolean password;
        final int selectionStart;
        final int selectionEnd;
        final Rect bounds;
        final int x;
        final int y;

        InputTarget(int index, String path, String label, String className, String id, String text, String hint, boolean focused, boolean password, int selectionStart, int selectionEnd, Rect bounds, int x, int y) {
            this.index = index;
            this.path = path == null ? "" : path;
            this.label = label == null ? "" : label;
            this.className = className == null ? "" : className;
            this.id = id == null ? "" : id;
            this.text = text == null ? "" : text;
            this.hint = hint == null ? "" : hint;
            this.focused = focused;
            this.password = password;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            this.bounds = new Rect(bounds);
            this.x = x;
            this.y = y;
        }
    }

}
