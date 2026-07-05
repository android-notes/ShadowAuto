package com.silentauto.shell;

import android.graphics.Bitmap;
import android.media.Image;

import java.nio.ByteBuffer;

final class ScreenCapture {
    private ScreenCapture() {
    }

    static Bitmap latestBitmap(VirtualDisplaySession display, int retries, long delayMs) {
        return display.captureBitmap(retries, delayMs);
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
