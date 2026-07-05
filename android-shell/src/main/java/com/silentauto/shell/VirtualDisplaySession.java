package com.silentauto.shell;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

final class VirtualDisplaySession {
    final int displayId;
    final int width;
    final int height;
    final int dpi;
    final ImageReader imageReader;
    private final Surface surface;
    private final VirtualDisplay display;
    private final WindowManagerBridge windowManager;
    private final int previousImePolicy;
    private final boolean localImeEnabled;

    private VirtualDisplaySession(int width, int height, int dpi, ImageReader imageReader, Surface surface, VirtualDisplay display) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.imageReader = imageReader;
        this.surface = surface;
        this.display = display;
        this.displayId = display.getDisplay().getDisplayId();
        this.windowManager = new WindowManagerBridge();
        this.previousImePolicy = windowManager.getDisplayImePolicy(displayId);
        this.localImeEnabled = windowManager.setDisplayImePolicy(displayId, WindowManagerBridge.DISPLAY_IME_POLICY_LOCAL);
    }

    boolean localImeEnabled() {
        return localImeEnabled;
    }

    static VirtualDisplaySession create(int width, int height, int dpi) {
        DisplayManager manager = displayManager();
        ImageReader imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        Surface surface = imageReader.getSurface();
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | hiddenFlag("VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH")
                | hiddenFlag("VIRTUAL_DISPLAY_FLAG_OWN_FOCUS")
                | hiddenFlag("VIRTUAL_DISPLAY_FLAG_TRUSTED");
        VirtualDisplay display = manager.createVirtualDisplay("ShadowAuto", width, height, dpi, surface, flags);
        if (display == null || display.getDisplay() == null) {
            throw new IllegalStateException("createVirtualDisplay failed");
        }
        return new VirtualDisplaySession(width, height, dpi, imageReader, surface, display);
    }

    private static DisplayManager displayManager() {
        try {
            Constructor<DisplayManager> ctor = DisplayManager.class.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            return ctor.newInstance(ShellContext.get());
        } catch (Throwable ignored) {
            return (DisplayManager) ShellContext.get().getSystemService(Context.DISPLAY_SERVICE);
        }
    }

    void release() {
        try {
            if (previousImePolicy != -1) {
                windowManager.setDisplayImePolicy(displayId, previousImePolicy);
            }
        } catch (Throwable ignored) {
        }
        try {
            display.release();
        } catch (Throwable ignored) {
        }
        try {
            surface.release();
        } catch (Throwable ignored) {
        }
        try {
            imageReader.close();
        } catch (Throwable ignored) {
        }
    }

    private static int hiddenFlag(String name) {
        try {
            Field field = DisplayManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
