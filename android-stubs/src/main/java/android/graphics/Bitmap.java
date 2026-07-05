package android.graphics;

import java.io.OutputStream;

public class Bitmap {
    public static Bitmap createBitmap(int displayWidth, int displayHeight, Config argb8888) {
        return null;
    }

    public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight,
                                            boolean filter) {
        throw new RuntimeException();
    }

    public void setHasAlpha(boolean b) {

    }

    public int getWidth() {
        throw new RuntimeException("");

    }

    public int getHeight() {
        throw new RuntimeException("");
    }

    public void recycle() {
    }

    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        return false;
    }

    public enum CompressFormat {

        JPEG(0),

        PNG(1),

        @Deprecated
        WEBP(2),

        WEBP_LOSSY(3),

        WEBP_LOSSLESS(4);

        final int nativeInt;

        CompressFormat(int nativeInt) {
            this.nativeInt = nativeInt;
        }
    }

    public enum Config {

        ARGB_8888,
        RGB_565
    }
}
