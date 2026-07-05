package com.silentauto.paddlerocr;

import android.graphics.Bitmap;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

final class PaddleOcrNative {
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static File libraryDir;

    private long nativePointer;

    PaddleOcrNative(File libDir, Config config) {
        loadLibrary(libDir);
        nativePointer = init(
                config.detModelPath,
                config.recModelPath,
                config.clsModelPath,
                config.useOpenCl ? 1 : 0,
                config.cpuThreadNum,
                config.cpuPowerMode
        );
        if (nativePointer == 0) {
            throw new IllegalStateException("Paddle OCR native init failed");
        }
    }

    static boolean isAvailable(File libDir) {
        try {
            loadLibrary(libDir);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void loadLibrary(File libDir) {
        if (LOADED.get()) {
            return;
        }
        if (LOADED.compareAndSet(false, true)) {
            try {
                libraryDir = libDir;
                load("libc++_shared.so", true);
                load("libomp.so", true);
                load("libhiai.so", false);
                load("libhiai_ir.so", false);
                load("libhiai_ir_build.so", false);
                load("libNative.so", true);
            } catch (Throwable e) {
                LOADED.set(false);
                throw new IllegalStateException("Load Paddle OCR native libs failed from " + libDir, e);
            }
        }
    }

    private static void load(String name, boolean required) {
        File file = new File(libraryDir, name);
        if (!file.isFile()) {
            if (required) {
                throw new IllegalStateException("Missing native lib: " + file);
            }
            return;
        }
        try {
            System.load(file.getAbsolutePath());
        } catch (Throwable e) {
            if (required) {
                throw new IllegalStateException("Load native lib failed: " + file, e);
            }
        }
    }

    ArrayList<PaddleOcrResult> runImage(Bitmap image, int maxSize, boolean detect, boolean classify, boolean recognize) {
        if (nativePointer == 0) {
            throw new IllegalStateException("Paddle OCR native predictor is released");
        }
        float[] raw = forward(
                nativePointer,
                image,
                maxSize,
                detect ? 1 : 0,
                classify ? 1 : 0,
                recognize ? 1 : 0
        );
        return postprocess(raw);
    }

    void destroy() {
        if (nativePointer != 0) {
            release(nativePointer);
            nativePointer = 0;
        }
    }

    protected native long init(String detModelPath, String recModelPath, String clsModelPath, int useOpenCl, int threadNum, String cpuMode);

    protected native float[] forward(long pointer, Bitmap originalImage, int maxSize, int runDet, int runCls, int runRec);

    protected native void release(long pointer);

    private ArrayList<PaddleOcrResult> postprocess(float[] raw) {
        ArrayList<PaddleOcrResult> results = new ArrayList<>();
        if (raw == null || raw.length == 0) {
            return results;
        }
        int begin = 0;
        while (begin + 4 < raw.length) {
            int pointCount = Math.round(raw[begin]);
            int wordCount = Math.round(raw[begin + 1]);
            int itemLength = 2 + 1 + pointCount * 2 + wordCount + 2;
            if (pointCount < 0 || wordCount < 0 || begin + itemLength > raw.length) {
                break;
            }
            results.add(parse(raw, begin + 2, pointCount, wordCount));
            begin += itemLength;
        }
        return results;
    }

    private PaddleOcrResult parse(float[] raw, int begin, int pointCount, int wordCount) {
        int current = begin;
        PaddleOcrResult result = new PaddleOcrResult();
        result.setConfidence(raw[current]);
        current++;
        for (int i = 0; i < pointCount; i++) {
            result.addPoint(Math.round(raw[current + i * 2]), Math.round(raw[current + i * 2 + 1]));
        }
        current += pointCount * 2;
        for (int i = 0; i < wordCount; i++) {
            result.addWordIndex(Math.round(raw[current + i]));
        }
        current += wordCount;
        result.setClsIndex(Math.round(raw[current]));
        result.setClsConfidence(raw[current + 1]);
        return result;
    }

    static final class Config {
        String detModelPath;
        String recModelPath;
        String clsModelPath;
        boolean useOpenCl;
        int cpuThreadNum = 4;
        String cpuPowerMode = "LITE_POWER_HIGH";
    }
}
