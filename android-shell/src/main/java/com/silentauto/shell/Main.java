package com.silentauto.shell;

import android.os.Looper;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class Main {
    private static final String PROCESS_MARKER = "com.silentauto.shell.Main";

    public static void main(String[] args) throws Exception {
        try {
            Looper.prepareMainLooper();
        } catch (Throwable ignored) {
        }
        killExistingShellProcesses();
        ShellContext.init();
        Config config = Config.from(args);
        LogHub logs = new LogHub();
        AutomationEngine engine = new AutomationEngine(config, logs);
        logs.info("silent shell listening on 127.0.0.1:" + config.port);
        new RpcServer(config.port, engine, logs).run();
    }

    private static void killExistingShellProcesses() {
        int currentPid = Process.myPid();
        File[] entries = new File("/proc").listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            String name = entry.getName();
            if (!isPid(name)) {
                continue;
            }
            int pid;
            try {
                pid = Integer.parseInt(name);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (pid == currentPid) {
                continue;
            }
            String commandLine = readCommandLine(new File(entry, "cmdline"));
            if (commandLine.isEmpty() || !commandLine.contains(PROCESS_MARKER)) {
                continue;
            }
            try {
                Process.killProcess(pid);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isPid(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String readCommandLine(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int length = input.read(buffer);
            if (length <= 0) {
                return "";
            }
            StringBuilder out = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                byte value = buffer[i];
                out.append(value == 0 ? ' ' : (char) value);
            }
            return out.toString().trim();
        } catch (IOException ignored) {
            return "";
        }
    }
}
