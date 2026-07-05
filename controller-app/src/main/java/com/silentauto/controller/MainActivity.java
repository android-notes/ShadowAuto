package com.silentauto.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONObject;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity implements AutomationTaskView.Listener, RpcClient.Listener {
    private static final int PORT = 43110;
    private static final int REQUEST_CONFIG = 1001;
    private static final int MAIN_PADDING_DP = 24;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final AtomicInteger nextTask = new AtomicInteger(1);
    private final List<AutomationTaskView> tasks = Collections.synchronizedList(new ArrayList<>());

    private AiConfigStore configStore;
    private AiConfig config = new AiConfig("", "", "");
    private RpcClient rpcClient;
    private ViewPager2 pager;
    private TaskPagerAdapter pagerAdapter;
    private TextView indicator;
    private boolean configOpen;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        UiUtils.setupSystemBars(this);
        startKeepAliveService();
        configStore = new AiConfigStore(this);
        config = configStore.load();
        rpcClient = new RpcClient("127.0.0.1", PORT, this);
        if (config.isConfigured()) {
            showMain();
        } else {
            openConfig(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (configStore == null) {
            return;
        }
        config = configStore.load();
        if (!config.isConfigured() && !configOpen) {
            openConfig(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CONFIG) {
            return;
        }
        configOpen = false;
        config = configStore.load();
        if (config.isConfigured()) {
            showMain();
        } else {
            openConfig(false);
        }
    }

    @Override
    public void onTaskStart(AutomationTaskView task) {
        io.execute(() -> sendStart(task));
    }

    @Override
    public void onTaskStop(String taskId) {
        io.execute(() -> sendStop(taskId));
    }

    @Override
    public void onLine(String line) {
        handleRpcLine(line);
    }

    @Override
    public void onClosed(Exception error) {
        appendGlobal("[closed] " + error.getMessage());
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void startKeepAliveService() {
        Intent intent = new Intent(this, KeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private void openConfig(boolean fromSettings) {
        configOpen = true;
        Intent intent = new Intent(this, ConfigActivity.class);
        intent.putExtra(ConfigActivity.EXTRA_FROM_SETTINGS, fromSettings);
        startActivityForResult(intent, REQUEST_CONFIG);
    }

    private void showMain() {
        setContentView(R.layout.activity_main);
        FrameLayout root = findViewById(R.id.mainRoot);
        LinearLayout page = findViewById(R.id.mainPage);
        ImageButton stopAll = findViewById(R.id.stopAllButton);
        ImageButton settings = findViewById(R.id.settingsButton);
        ImageButton help = findViewById(R.id.helpButton);
        Button add = findViewById(R.id.addTaskButton);
        pager = findViewById(R.id.taskPager);
        indicator = findViewById(R.id.pageIndicator);

        UiUtils.clearButtonTint(add);
        FrameLayout.LayoutParams addLp = (FrameLayout.LayoutParams) add.getLayoutParams();
        UiUtils.applyMainInsets(this, root, page, add, addLp, MAIN_PADDING_DP);

        ensureTaskPage();
        pagerAdapter = new TaskPagerAdapter(tasks);
        pager.setAdapter(pagerAdapter);
        pager.setOffscreenPageLimit(2);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator();
            }
        });

        stopAll.setOnClickListener(v -> confirmStopAll());
        settings.setOnClickListener(v -> openConfig(true));
        help.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        add.setOnClickListener(v -> addEmptyTask());
        updateIndicator();
    }

    private void ensureTaskPage() {
        synchronized (tasks) {
            if (!tasks.isEmpty()) {
                return;
            }
            tasks.add(new AutomationTaskView(this, main, newTaskId(), this));
        }
    }

    private void addEmptyTask() {
        Toast.makeText(this, getString(R.string.parallel_task_warning), Toast.LENGTH_SHORT).show();
        AutomationTaskView task = new AutomationTaskView(this, main, newTaskId(), this);
        int index;
        synchronized (tasks) {
            tasks.add(task);
            index = tasks.size() - 1;
        }
        pagerAdapter.notifyItemInserted(index);
        pager.setCurrentItem(index, true);
        updateIndicator();
    }

    private void confirmStopAll() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.stop_all_title))
                .setMessage(getString(R.string.stop_all_message))
                .setNegativeButton(getString(R.string.no), null)
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> stopAllTasks())
                .show();
    }

    private void stopAllTasks() {
        synchronized (tasks) {
            for (AutomationTaskView task : tasks) {
                task.setRunning(false);
            }
        }
        io.execute(() -> {
            if (sendStopAll()) {
                main.post(() -> Toast.makeText(this, getString(R.string.stop_all_sent), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String newTaskId() {
        return "app-" + nextTask.getAndIncrement();
    }

    private void sendStart(AutomationTaskView task) {
        try {
            JSONObject params = new JSONObject();
            params.put("taskId", task.taskId());
            params.put("goal", task.goalText());
            params.put("apiKey", config.apiKey);
            params.put("apiBase", config.apiBase);
            params.put("model", config.model);
            rpcClient.send("startTask", params);
        } catch (Exception e) {
            reportRpcError(task, e);
        }
    }

    private void sendStop(String taskId) {
        try {
            rpcClient.send("stopTask", new JSONObject().put("taskId", taskId));
        } catch (Exception e) {
            reportRpcError(taskById(taskId), e);
        }
    }

    private boolean sendStopAll() {
        try {
            rpcClient.send("stopTask", new JSONObject());
            return true;
        } catch (Exception e) {
            reportRpcError(null, e);
            return false;
        }
    }

    private void handleRpcLine(String line) {
        try {
            JSONObject object = new JSONObject(line);
            if (!"event".equals(object.optString("method"))) {
                return;
            }
            JSONObject params = object.getJSONObject("params");
            AutomationTaskView task = taskById(params.optString("taskId"));
            if (task == null) {
                return;
            }
            if ("frame".equals(params.optString("type"))) {
                task.showFrame(params.optString("data"));
            } else {
                task.append(params.optString("message"));
            }
        } catch (Exception ignored) {
        }
    }

    private AutomationTaskView taskById(String taskId) {
        synchronized (tasks) {
            for (AutomationTaskView task : tasks) {
                if (task.taskId().equals(taskId)) {
                    return task;
                }
            }
        }
        return null;
    }

    private void appendGlobal(String text) {
        runOnUiThread(() -> {
            synchronized (tasks) {
                if (!tasks.isEmpty()) {
                    tasks.get(0).append(text);
                }
            }
        });
    }

    private void reportRpcError(AutomationTaskView task, Exception error) {
        String detail = rpcErrorMessage(error);
        String message = getString(R.string.rpc_error, detail);
        if (task != null) {
            task.append(message);
            task.setRunning(false);
        } else {
            appendGlobal(message);
        }
        main.post(() -> Toast.makeText(this, detail, Toast.LENGTH_LONG).show());
    }

    private String rpcErrorMessage(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException) {
                return getString(R.string.rpc_shell_unavailable);
            }
            current = current.getCause();
        }
        String message = error.getMessage();
        if (message != null && message.toLowerCase(Locale.US).contains("refused")) {
            return getString(R.string.rpc_shell_unavailable);
        }
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private void updateIndicator() {
        if (indicator == null || pagerAdapter == null || pager == null) {
            return;
        }
        int count = pagerAdapter.getItemCount();
        int current = pager.getCurrentItem();
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                dots.append("  ");
            }
            dots.append(i == current ? "\u25CF" : "\u25CB");
        }
        indicator.setText(dots.toString());
    }
}
