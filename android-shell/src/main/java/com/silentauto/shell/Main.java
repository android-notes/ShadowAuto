package com.silentauto.shell;

import android.os.Looper;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class Main {
    private static final String PROCESS_MARKER = "com.silentauto.shell.Main";

    static {
        installGlobalExceptionLogger();
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable e) {
            printFatal(Thread.currentThread(), e);
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Config config = Config.from(args);
        LogHub logs = new LogHub();
        if (hasFlag(args, "--ocr-test")) {
            OcrTest.run(logs);
            return;
        }
        prepareLooper();
        killExistingShellProcesses();
        ShellContext.init();
        if (hasFlag(args, "--video-test")) {
            VideoTest.run(logs);
            return;
        }
        AutomationEngine engine = new AutomationEngine(config, logs);
        logs.info("silent shell listening on 127.0.0.1:" + config.port);
        Thread rpcThread = new Thread(() -> runRpcServer(config, logs, engine), "rpc-server");
        rpcThread.start();
        Looper.loop();
    }

    private static void installGlobalExceptionLogger() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            printFatal(thread, error);
            Process.killProcess(Process.myPid());
            System.exit(1);
        });
    }

    private static void printFatal(Thread thread, Throwable error) {
        synchronized (System.err) {
            String name = thread == null ? "unknown" : thread.getName();
            System.err.println("[fatal] uncaught exception in thread: " + name);
            if (error != null) {
                error.printStackTrace(System.err);
            }
            System.err.flush();
        }
    }

    private static void prepareLooper() {
        if (Looper.myLooper() != null) {
            return;
        }
        try {
            Looper.prepareMainLooper();
        } catch (Throwable ignored) {
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
        }
    }

    private static void runRpcServer(Config config, LogHub logs, AutomationEngine engine) {
        try {
            new RpcServer(config.port, engine, logs).run();
        } catch (Throwable e) {
            logs.error("rpc server failed", e);
            printFatal(Thread.currentThread(), e);
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
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
            if (commandLine.isEmpty() || !isShellMainProcess(commandLine)) {
                continue;
            }
            try {
                Process.killProcess(pid);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isShellMainProcess(String commandLine) {
        if (!commandLine.contains(PROCESS_MARKER)) {
            return false;
        }
        return commandLine.startsWith("app_process")
                || commandLine.startsWith("/system/bin/app_process");
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
