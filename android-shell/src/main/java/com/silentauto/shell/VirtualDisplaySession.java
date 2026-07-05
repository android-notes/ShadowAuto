package com.silentauto.shell;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.SystemClock;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

final class VirtualDisplaySession {
    final int displayId;
    final int width;
    final int height;
    final int dpi;
    private final ImageReader captureReader;
    private final Surface captureSurface;
    private Surface outputSurface;
    private final VirtualDisplay display;
    private final WindowManagerBridge windowManager;
    private final int previousImePolicy;
    private final boolean localImeEnabled;

    private VirtualDisplaySession(int width, int height, int dpi, ImageReader captureReader, Surface outputSurface, VirtualDisplay display) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.captureReader = captureReader;
        this.captureSurface = captureReader.getSurface();
        this.outputSurface = outputSurface;
        this.display = display;
        this.displayId = display.getDisplay().getDisplayId();
        this.windowManager = new WindowManagerBridge();
        this.previousImePolicy = windowManager.getDisplayImePolicy(displayId);
        this.localImeEnabled = windowManager.setDisplayImePolicy(displayId, WindowManagerBridge.DISPLAY_IME_POLICY_LOCAL);
    }

    boolean localImeEnabled() {
        return localImeEnabled;
    }

    static VirtualDisplaySession create(int width, int height, int dpi, Surface outputSurface) {
        DisplayManager manager = displayManager();
        ImageReader captureReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        int baseFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
        int touchFlag = hiddenFlag("VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH");
        int focusFlag = hiddenFlag("VIRTUAL_DISPLAY_FLAG_OWN_FOCUS");
        int trustedFlag = hiddenFlag("VIRTUAL_DISPLAY_FLAG_TRUSTED");
        int[] candidates = new int[]{
                baseFlags | touchFlag | focusFlag | trustedFlag,
                baseFlags | touchFlag | focusFlag,
                baseFlags | touchFlag,
                baseFlags
        };
        Throwable lastError = null;
        for (int flags : candidates) {
            try {
                VirtualDisplay display = manager.createVirtualDisplay("ShadowAuto", width, height, dpi, outputSurface, flags);
                if (display != null && display.getDisplay() != null) {
                    return new VirtualDisplaySession(width, height, dpi, captureReader, outputSurface, display);
                }
            } catch (Throwable e) {
                lastError = e;
            }
        }
        captureReader.close();
        throw new IllegalStateException("createVirtualDisplay failed", lastError);
    }

    synchronized Bitmap captureBitmap(int retries, long delayMs) {
        Surface restoreSurface = outputSurface;
        Image image = null;
        try {
            drainCaptureReader();
            display.setSurface(captureSurface);
            for (int i = 0; i <= retries; i++) {
                SystemClock.sleep(delayMs);
                image = captureReader.acquireLatestImage();
                if (image != null) {
                    return ScreenCapture.bitmapFromImage(image);
                }
            }
            return null;
        } finally {
            if (image != null) {
                image.close();
            }
            try {
                display.setSurface(restoreSurface);
                outputSurface = restoreSurface;
            } catch (Throwable ignored) {
            }
        }
    }

    private void drainCaptureReader() {
        while (true) {
            Image image = captureReader.acquireLatestImage();
            if (image == null) {
                return;
            }
            image.close();
        }
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
            captureSurface.release();
        } catch (Throwable ignored) {
        }
        try {
            captureReader.close();
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
