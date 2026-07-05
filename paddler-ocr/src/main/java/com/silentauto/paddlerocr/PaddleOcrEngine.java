package com.silentauto.paddlerocr;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PaddleOcrEngine implements AutoCloseable {
    public static final String DEFAULT_ROOT = "/data/local/tmp/shadowauto/ocr";
    public static final String DEFAULT_MODEL_DIR = DEFAULT_ROOT + "/models/ch_PP-OCRv2";
    public static final String DEFAULT_LABEL_PATH = DEFAULT_ROOT + "/labels/ppocr_keys_v1.txt";
    public static final String DEFAULT_LIB_DIR = DEFAULT_ROOT + "/lib/arm64-v8a";

    private final ArrayList<String> labels = new ArrayList<>();
    private PaddleOcrNative nativeOcr;
    private int maxSize = 960;

    public static boolean isNativeAvailable() {
        return PaddleOcrNative.isAvailable(new File(DEFAULT_LIB_DIR));
    }

    public synchronized void init() throws Exception {
        init(new Config());
    }

    public synchronized void init(Config config) throws Exception {
        close();
        Config resolved = config == null ? new Config() : config;
        maxSize = resolved.detLongSize;
        File modelDir = requiredDirectory(resolved.modelDir);
        File labelFile = requiredFile(resolved.labelPath);
        File libDir = requiredDirectory(resolved.libDir);
        loadLabels(labelFile);
        PaddleOcrNative.Config nativeConfig = new PaddleOcrNative.Config();
        nativeConfig.detModelPath = requiredFile(new File(modelDir, "det_db.nb").getAbsolutePath()).getAbsolutePath();
        nativeConfig.recModelPath = requiredFile(new File(modelDir, "rec_crnn.nb").getAbsolutePath()).getAbsolutePath();
        nativeConfig.clsModelPath = requiredFile(new File(modelDir, "cls.nb").getAbsolutePath()).getAbsolutePath();
        nativeConfig.cpuThreadNum = resolved.cpuThreadNum;
        nativeConfig.cpuPowerMode = resolved.cpuPowerMode;
        nativeConfig.useOpenCl = resolved.useOpenCl;
        nativeOcr = new PaddleOcrNative(libDir, nativeConfig);
    }

    public synchronized List<PaddleOcrResult> recognize(Bitmap bitmap) {
        if (nativeOcr == null) {
            throw new IllegalStateException("Paddle OCR is not initialized");
        }
        Bitmap input = bitmap;
        boolean recycleInput = false;
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            input = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            recycleInput = true;
        }
        try {
            ArrayList<PaddleOcrResult> results = nativeOcr.runImage(input, maxSize, true, true, true);
            for (PaddleOcrResult result : results) {
                result.setText(decodeText(result.getWordIndices()));
            }
            return results;
        } finally {
            if (recycleInput) {
                input.recycle();
            }
        }
    }

    @Override
    public synchronized void close() {
        if (nativeOcr != null) {
            nativeOcr.destroy();
            nativeOcr = null;
        }
    }

    private String decodeText(List<Integer> indices) {
        StringBuilder out = new StringBuilder();
        for (int index : indices) {
            if (index >= 0 && index < labels.size()) {
                out.append(labels.get(index));
            }
        }
        return out.toString();
    }

    private void loadLabels(File file) throws Exception {
        labels.clear();
        labels.add("blank");
        String text = new String(readFile(file), StandardCharsets.UTF_8);
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String value = line.trim();
            if (!value.isEmpty()) {
                labels.add(value);
            }
        }
        labels.add(" ");
    }

    private byte[] readFile(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        }
    }

    private File requiredDirectory(String path) {
        File file = new File(path);
        if (!file.isDirectory()) {
            throw new IllegalStateException("Missing Paddle OCR directory: " + file);
        }
        return file;
    }

    private File requiredFile(String path) {
        File file = new File(path);
        if (!file.isFile()) {
            throw new IllegalStateException("Missing Paddle OCR file: " + file);
        }
        return file;
    }

    public static final class Config {
        public String modelDir = DEFAULT_MODEL_DIR;
        public String labelPath = DEFAULT_LABEL_PATH;
        public String libDir = DEFAULT_LIB_DIR;
        public int detLongSize = 960;
        public int cpuThreadNum = 4;
        public String cpuPowerMode = "LITE_POWER_HIGH";
        public boolean useOpenCl;
    }
}
