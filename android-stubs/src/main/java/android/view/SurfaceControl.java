package android.view;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.IBinder;

public class SurfaceControl {
    public static IBinder getInternalDisplayToken() {
        return null;
    }

    public static ScreenshotHardwareBuffer captureDisplay(DisplayCaptureArgs captureArgs) {
        throw new RuntimeException();
    }

    public static class Transaction {
        public static IBinder getDefaultApplyToken() {
            throw new RuntimeException();
        }
    }

    public static Bitmap screenshot(Rect crop, int displayWidth, int displayHeight, int rotation) {
        return null;
    }

    public static Bitmap screenshot(int screenshotWidth, int screenshotHeight) {
        return null;
    }

    public static class DisplayCaptureArgs {
        public static class Builder {
            public Builder(IBinder displayToken) {
            }

            public DisplayCaptureArgs build() {
                return null;
            }
        }
    }

    public class ScreenshotHardwareBuffer {
        public Bitmap asBitmap() {
            return null;
        }
        public HardwareBuffer getHardwareBuffer() {
            throw new RuntimeException();
        }
    }
}
