
package android.window;


import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.IBinder;


/**
 * Handles display and layer captures for the system.
 *
 * @hide
 */
public class ScreenCapture {


    public static ScreenshotHardwareBuffer captureDisplay(
            DisplayCaptureArgs captureArgs) {
        throw new RuntimeException();
    }


    /**
     * A wrapper around HardwareBuffer that contains extra information about how to
     * interpret the screenshot HardwareBuffer.
     *
     * @hide
     */
    public static class ScreenshotHardwareBuffer {

        public HardwareBuffer getHardwareBuffer() {
            throw new RuntimeException();
        }
        public Bitmap asBitmap() {
            throw new RuntimeException();
        }
    }


    public static class DisplayCaptureArgs {

        private DisplayCaptureArgs(Builder builder) {

        }

        /**
         * The Builder class used to construct {@link DisplayCaptureArgs}
         */
        public static class Builder {

            public DisplayCaptureArgs build() {
                throw new RuntimeException();
            }

            public Builder(IBinder displayToken) {

            }

            public Builder setSize(int width, int height) {

                return this;
            }

        }
    }

    public static class CaptureArgs {
    }

    public static class ScreenCaptureListener {
    }

    public abstract static class SynchronousScreenCaptureListener extends ScreenCaptureListener {
        public ScreenshotHardwareBuffer getBuffer() {
            throw new RuntimeException();
        }
    }

    public static SynchronousScreenCaptureListener createSyncCaptureListener() {
        throw new RuntimeException();
    }
}