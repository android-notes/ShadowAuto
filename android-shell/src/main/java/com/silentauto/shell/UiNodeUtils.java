package com.silentauto.shell;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class UiNodeUtils {
    private UiNodeUtils() {
    }

    static boolean isEditable(AccessibilityNodeInfo node) {
        CharSequence className = node.getClassName();
        return node.isEditable() || (className != null && className.toString().contains("EditText"));
    }

    static boolean isInputNode(AccessibilityNodeInfo node) {
        return node.isVisibleToUser() && node.isEnabled() && isEditable(node);
    }

    static boolean isTarget(AccessibilityNodeInfo node) {
        if (!node.isVisibleToUser() || !node.isEnabled()) {
            return false;
        }
        return node.isClickable()
                || node.isLongClickable()
                || isEditable(node)
                || hasAction(node, AccessibilityNodeInfo.ACTION_CLICK);
    }

    static boolean hasUsefulContent(AccessibilityNodeInfo node) {
        return !empty(node.getText())
                || !empty(node.getContentDescription())
                || !empty(hint(node))
                || !empty(node.getViewIdResourceName())
                || isEditable(node)
                || node.isFocused()
                || node.isSelected()
                || node.isChecked();
    }

    static boolean hasAction(AccessibilityNodeInfo node, int action) {
        return (node.getActions() & action) == action;
    }

    static ArrayList<String> actionList(AccessibilityNodeInfo node) {
        ArrayList<String> result = new ArrayList<>();
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
            result.add(actionName(action));
        }
        return result;
    }

    static String actionName(AccessibilityNodeInfo.AccessibilityAction action) {
        int id = action.getId();
        if (id == AccessibilityNodeInfo.ACTION_CLICK) {
            return "click";
        }
        if (id == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
            return "long_click";
        }
        if (id == AccessibilityNodeInfo.ACTION_FOCUS) {
            return "focus";
        }
        if (id == AccessibilityNodeInfo.ACTION_SET_TEXT) {
            return "set_text";
        }
        if (id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            return "scroll_forward";
        }
        if (id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            return "scroll_backward";
        }
        CharSequence label = action.getLabel();
        if (!empty(label)) {
            return label.toString();
        }
        return String.valueOf(id);
    }

    static ArrayList<String> targetActions(AccessibilityNodeInfo node) {
        ArrayList<String> result = new ArrayList<>();
        if (node.isClickable() || hasAction(node, AccessibilityNodeInfo.ACTION_CLICK)) {
            result.add("click");
        }
        if (node.isLongClickable()) {
            result.add("long_click");
        }
        if (isEditable(node)) {
            result.add("input");
        }
        if (node.isFocusable()) {
            result.add("focus");
        }
        return result;
    }

    static String label(AccessibilityNodeInfo node) {
        StringBuilder out = new StringBuilder();
        appendText(out, node.getText());
        appendText(out, node.getContentDescription());
        appendText(out, hint(node));
        if (out.length() == 0) {
            collectDescendantText(node, out, 180);
        }
        return out.toString();
    }

    static void collectDescendantText(AccessibilityNodeInfo node, StringBuilder out, int limit) {
        if (node == null || out.length() >= limit) {
            return;
        }
        appendText(out, node.getText());
        appendText(out, node.getContentDescription());
        appendText(out, hint(node));
        for (int i = 0; i < node.getChildCount() && out.length() < limit; i++) {
            collectDescendantText(node.getChild(i), out, limit);
        }
        if (out.length() > limit) {
            out.setLength(limit);
        }
    }

    static void appendText(StringBuilder out, CharSequence value) {
        if (empty(value)) {
            return;
        }
        if (out.length() > 0) {
            out.append(' ');
        }
        out.append(value);
    }

    static boolean matchesInputQuery(AccessibilityNodeInfo node, String query) {
        return normalize(label(node)).contains(query)
                || normalize(node.getViewIdResourceName()).contains(query)
                || normalize(node.getClassName()).contains(query);
    }

    static boolean looksLikeInputLauncher(AccessibilityNodeInfo node, String query) {
        String haystack = normalize(label(node) + " " + node.getViewIdResourceName() + " " + node.getClassName());
        if (!query.isEmpty() && haystack.contains(query)) {
            return true;
        }
        return haystack.contains("search")
                || haystack.contains("query")
                || haystack.contains("keyword")
                || haystack.contains("input")
                || haystack.contains("edit")
                || haystack.contains("搜索")
                || haystack.contains("搜")
                || haystack.contains("输入")
                || haystack.contains("查找");
    }

    static String normalize(CharSequence value) {
        return value == null ? "" : value.toString().trim().toLowerCase();
    }

    static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    static String boundsValue(Rect rect) {
        return rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom;
    }

    static String centerValue(Rect rect) {
        return centerX(rect) + "," + centerY(rect);
    }

    static int windowDisplayId(AccessibilityWindowInfo window) {
        try {
            Method method = AccessibilityWindowInfo.class.getMethod("getDisplayId");
            Object value = method.invoke(window);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    static void addUnique(ArrayList<Integer> values, int value) {
        for (Integer existing : values) {
            if (existing == value) {
                return;
            }
        }
        values.add(value);
    }

    static CharSequence hint(AccessibilityNodeInfo node) {
        try {
            return node.getHintText();
        } catch (Throwable ignored) {
            return "";
        }
    }

    static int centerX(Rect rect) {
        return rect.left + Math.max(0, rect.width()) / 2;
    }

    static int centerY(Rect rect) {
        return rect.top + Math.max(0, rect.height()) / 2;
    }

    static boolean empty(CharSequence value) {
        return value == null || value.toString().trim().isEmpty();
    }
}
