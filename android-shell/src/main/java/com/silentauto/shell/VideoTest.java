package com.silentauto.shell;

import android.graphics.Bitmap;
import android.os.SystemClock;

import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicInteger;

final class VideoTest {
    private VideoTest() {
    }

    static void run(LogHub logs) throws Exception {
        AtomicInteger configs = new AtomicInteger();
        AtomicInteger samples = new AtomicInteger();
        LogHub.Sink sink = object -> {
            JsonObject params = object.getAsJsonObject("params");
            String type = params == null ? "" : JsonRpc.text(params, "type");
            if ("video.config".equals(type)) {
                configs.incrementAndGet();
            } else if ("video.sample".equals(type)) {
                samples.incrementAndGet();
            }
        };
        logs.add(sink);
        VideoStreamer streamer = null;
        VirtualDisplaySession display = null;
        try {
            logs.logcat(null, "video_test_begin");
            DisplaySpec spec = DisplaySpec.current(logs);
            streamer = new VideoStreamer(logs, "video-test", spec.width, spec.height);
            display = VirtualDisplaySession.create(streamer.width(), streamer.height(), spec.dpi, streamer.inputSurface());
            streamer.start();
            logs.logcat(null, "video_test_display=" + display.displayId + " size=" + display.width + "x" + display.height + " dpi=" + display.dpi);
            try {
                new AppCatalog(logs).launchPackage("video-test", "com.android.settings", display.displayId);
            } catch (Throwable e) {
                logs.error("video-test", "video test launch settings failed", e);
            }
            SystemClock.sleep(3500);
            Bitmap bitmap = display.captureBitmap(8, 120);
            if (bitmap != null) {
                logs.logcat(null, "video_test_capture=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                bitmap.recycle();
            } else {
                logs.logcat(null, "video_test_capture=null");
            }
            logs.logcat(null, "video_test_config_count=" + configs.get());
            logs.logcat(null, "video_test_sample_count=" + samples.get());
        } finally {
            if (streamer != null) {
                streamer.stop();
            }
            if (display != null) {
                display.release();
            }
            logs.remove(sink);
            logs.logcat(null, "video_test_end");
        }
    }
}
