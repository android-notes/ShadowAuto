package com.silentauto.shell;

import android.graphics.Bitmap;
import android.media.Image;
import android.os.SystemClock;

import java.nio.ByteBuffer;

final class ScreenCapture {
    private ScreenCapture() {
    }

    static Bitmap latestBitmap(VirtualDisplaySession display, int retries, long delayMs) {
        Image image = null;
        try {
            for (int i = 0; i <= retries; i++) {
                image = display.imageReader.acquireLatestImage();
                if (image != null) {
                    return bitmapFromImage(image);
                }
                SystemClock.sleep(delayMs);
            }
            return null;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    static Bitmap bitmapFromImage(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int bitmapWidth = rowStride / pixelStride;

        Bitmap padded = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }
}
