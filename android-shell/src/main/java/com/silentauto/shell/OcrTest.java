package com.silentauto.shell;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import com.silentauto.paddlerocr.PaddleOcrEngine;
import com.silentauto.paddlerocr.PaddleOcrResult;

import java.util.List;

final class OcrTest {
    private static final String TEST_IMAGE = "/data/local/tmp/shadowauto/ocr/test.png";

    private OcrTest() {
    }

    static void run(LogHub logs) {
        Bitmap bitmap = null;
        try {
            logs.logcat(null, "ocr_test_begin");
            logs.logcat(null, "ocr_native_available=" + PaddleOcrEngine.isNativeAvailable());
            bitmap = BitmapFactory.decodeFile(TEST_IMAGE);
            if (bitmap == null) {
                throw new IllegalStateException("Missing OCR test image: " + TEST_IMAGE);
            }
            logs.logcat(null, "ocr_test_image=" + TEST_IMAGE + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
            try (PaddleOcrEngine engine = new PaddleOcrEngine()) {
                logs.logcat(null, "ocr_init_begin");
                engine.init();
                logs.logcat(null, "ocr_init_done");
                List<PaddleOcrResult> results = engine.recognize(bitmap);
                logResults(logs, results);
            }
            logs.logcat(null, "ocr_test_end");
        } catch (Throwable e) {
            logs.error("ocr test failed", e);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    static void logResults(LogHub logs, List<PaddleOcrResult> results) {
        logs.logcat(null, "ocr_result_count=" + results.size());
        for (int i = 0; i < results.size(); i++) {
            PaddleOcrResult result = results.get(i);
            Rect bounds = result.getBounds();
            logs.logcat(null, "ocr[" + i + "] text=" + result.getText()
                    + " confidence=" + result.getConfidence()
                    + " bounds=" + bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom
                    + " cls=" + result.getClsIndex()
                    + " clsConfidence=" + result.getClsConfidence());
        }
    }
}
