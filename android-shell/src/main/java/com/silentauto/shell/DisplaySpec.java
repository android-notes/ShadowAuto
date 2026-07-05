package com.silentauto.shell;

import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.view.Display;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DisplaySpec {
    static final int FALLBACK_WIDTH = 720;
    static final int FALLBACK_HEIGHT = 1280;
    static final int FALLBACK_DPI = 320;

    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)x(\\d+)");
    private static final Pattern DENSITY_PATTERN = Pattern.compile("(\\d+)");

    final int width;
    final int height;
    final int dpi;

    private DisplaySpec(int width, int height, int dpi) {
        this.width = sane(width, FALLBACK_WIDTH);
        this.height = sane(height, FALLBACK_HEIGHT);
        this.dpi = sane(dpi, FALLBACK_DPI);
    }

    static DisplaySpec current(LogHub logs) {
        DisplaySpec spec = fromWm();
        if (spec == null) {
            spec = fromDisplayManager();
        }
        if (spec == null) {
            spec = new DisplaySpec(FALLBACK_WIDTH, FALLBACK_HEIGHT, FALLBACK_DPI);
        }
        if (logs != null) {
            logs.info("device display spec: " + spec.width + "x" + spec.height + " dpi=" + spec.dpi);
        }
        return spec;
    }

    private static DisplaySpec fromWm() {
        int[] size = parseSize(run("wm size"));
        int density = parseDensity(run("wm density"));
        if (size == null) {
            return null;
        }
        return new DisplaySpec(size[0], size[1], density);
    }

    private static DisplaySpec fromDisplayManager() {
        try {
            DisplayManager manager = (DisplayManager) ShellContext.get().getSystemService(Context.DISPLAY_SERVICE);
            Display display = manager == null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) {
                return null;
            }
            Point point = new Point();
            display.getRealSize(point);
            return new DisplaySpec(point.x, point.y, FALLBACK_DPI);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int[] parseSize(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = SIZE_PATTERN.matcher(value);
        int width = 0;
        int height = 0;
        while (matcher.find()) {
            width = parseInt(matcher.group(1), 0);
            height = parseInt(matcher.group(2), 0);
        }
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new int[]{width, height};
    }

    private static int parseDensity(String value) {
        if (value == null) {
            return FALLBACK_DPI;
        }
        Matcher matcher = DENSITY_PATTERN.matcher(value);
        int density = FALLBACK_DPI;
        while (matcher.find()) {
            density = parseInt(matcher.group(1), density);
        }
        return density;
    }

    private static String run(String command) {
        try {
            Process process = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            process.waitFor();
            return out.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int sane(int value, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return value;
    }
}
