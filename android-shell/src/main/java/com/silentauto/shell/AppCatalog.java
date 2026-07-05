package com.silentauto.shell;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class AppCatalog {
    private final LogHub logs;

    AppCatalog(LogHub logs) {
        this.logs = logs;
    }

    List<AppInfo> installedApps() {
        PackageManager pm = ShellContext.get().getPackageManager();
        List<AppInfo> result = new ArrayList<>();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (pm.getLaunchIntentForPackage(app.packageName) == null) {
                continue;
            }
            String label;
            try {
                label = String.valueOf(pm.getApplicationLabel(app));
            } catch (Throwable ignored) {
                label = app.packageName;
            }
            result.add(new AppInfo(label, app.packageName));
        }
        result.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.US)));
        return result;
    }

    String appsText(List<AppInfo> apps) {
        StringBuilder out = new StringBuilder();
        for (AppInfo app : apps) {
            out.append("- ").append(app.name).append(" | ").append(app.packageName).append('\n');
            if (out.length() > 12000) {
                break;
            }
        }
        return out.toString();
    }

    String localPackageGuess(String goal, List<AppInfo> apps) {
        String lower = goal.toLowerCase(Locale.US);
        for (AppInfo app : apps) {
            if (lower.contains(app.name.toLowerCase(Locale.US)) || lower.contains(app.packageName.toLowerCase(Locale.US))) {
                return app.packageName;
            }
        }
        return "";
    }

    void launchPackage(String taskId, String packageName, int displayId) throws Exception {
        PackageManager pm = ShellContext.get().getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent == null) {
            throw new IllegalStateException("no launch intent for " + packageName);
        }
        ComponentName component = intent.getComponent();
        if (component == null) {
            throw new IllegalStateException("no launch component for " + packageName);
        }
        new ProcessBuilder("sh", "-c", "am force-stop " + shellQuote(packageName)).start().waitFor();
        String command = "am start --display " + displayId
                + " -f 0x10008000 -n " + shellQuote(component.flattenToShortString());
        Process process = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        int code = process.waitFor();
        logs.info(taskId, "am start exit=" + code + " " + output.toString().trim());
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    static final class AppInfo {
        final String name;
        final String packageName;

        AppInfo(String name, String packageName) {
            this.name = name == null || name.trim().isEmpty() ? packageName : name;
            this.packageName = packageName;
        }
    }
}
