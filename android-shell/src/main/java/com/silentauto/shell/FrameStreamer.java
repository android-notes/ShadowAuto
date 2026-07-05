package com.silentauto.shell;

import android.graphics.Bitmap;
import android.media.Image;
import android.media.ImageReader;
import android.os.SystemClock;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

final class FrameStreamer implements Runnable {
    private static final int FRAME_INTERVAL_MS = 500;
    private static final int MAX_SIDE = 540;
    private static final int JPEG_QUALITY = 55;

    private final VirtualDisplaySession display;
    private final LogHub logs;
    private final String taskId;
    private final Thread thread;
    private volatile boolean running = true;
    private long lastFrameAt;

    FrameStreamer(VirtualDisplaySession display, LogHub logs) {
        this(display, logs, "");
    }

    FrameStreamer(VirtualDisplaySession display, LogHub logs, String taskId) {
        this.display = display;
        this.logs = logs;
        this.taskId = taskId == null ? "" : taskId;
        thread = new Thread(this, "frame-streamer");
    }

    void start() {
        thread.start();
    }

    void stop() {
        running = false;
        thread.interrupt();
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        ImageReader reader = display.imageReader;
        while (running) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) {
                    SystemClock.sleep(80);
                    continue;
                }
                long now = SystemClock.uptimeMillis();
                if (now - lastFrameAt >= FRAME_INTERVAL_MS) {
                    lastFrameAt = now;
                    send(image);
                }
            } catch (Throwable ignored) {
                SystemClock.sleep(200);
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    }

    private void send(Image image) {
        Bitmap cropped = ScreenCapture.bitmapFromImage(image);
        Bitmap output = scale(cropped);
        if (output != cropped) {
            cropped.recycle();
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes);
        int outWidth = output.getWidth();
        int outHeight = output.getHeight();
        output.recycle();
        logs.frame(taskId, display.displayId, outWidth, outHeight, Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
    }

    private Bitmap scale(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        if (max <= MAX_SIDE) {
            return bitmap;
        }
        float ratio = (float) MAX_SIDE / max;
        return Bitmap.createScaledBitmap(bitmap, Math.round(width * ratio), Math.round(height * ratio), true);
    }
}
