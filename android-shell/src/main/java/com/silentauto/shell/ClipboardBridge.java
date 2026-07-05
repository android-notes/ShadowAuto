package com.silentauto.shell;

import android.content.ClipData;
import android.content.Context;

final class ClipboardBridge {
    private final android.content.ClipboardManager manager;

    ClipboardBridge() {
        manager = (android.content.ClipboardManager) ShellContext.get().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("clipboard service unavailable");
        }
    }

    String getText() {
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return "";
        }
        CharSequence text = clip.getItemAt(0).coerceToText(ShellContext.get());
        return text == null ? "" : text.toString();
    }

    void setText(String text) {
        manager.setPrimaryClip(ClipData.newPlainText("ShadowAuto", text == null ? "" : text));
    }
}
