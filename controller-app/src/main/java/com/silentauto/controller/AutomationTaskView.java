package com.silentauto.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

final class AutomationTaskView {
    interface Listener {
        void onTaskStart(AutomationTaskView task);

        void onTaskStop(String taskId);
    }

    private final Activity activity;
    private final Handler main;
    private final Listener listener;
    private final String taskId;
    private final View view;
    private final EditText goal;
    private final FrameLayout screenBox;
    private final ImageView screen;
    private final TextView log;
    private final ScrollView logScroll;
    private final Button run;
    private final Button stop;

    private Bitmap latestFrame;
    private AlertDialog screenDialog;
    private ImageView dialogScreen;

    AutomationTaskView(Activity activity, Handler main, String taskId, Listener listener) {
        this.activity = activity;
        this.main = main;
        this.taskId = taskId;
        this.listener = listener;
        view = activity.getLayoutInflater().inflate(R.layout.item_task, null, false);
        goal = view.findViewById(R.id.taskGoal);
        screenBox = view.findViewById(R.id.screenBox);
        screen = view.findViewById(R.id.screenImage);
        log = view.findViewById(R.id.logText);
        logScroll = view.findViewById(R.id.logScroll);
        run = view.findViewById(R.id.runButton);
        stop = view.findViewById(R.id.stopButton);
        UiUtils.clearButtonTint(run, stop);
        screenBox.setOnClickListener(v -> showScreenDialog());
        run.setOnClickListener(v -> start());
        stop.setOnClickListener(v -> {
            setRunning(false);
            listener.onTaskStop(taskId);
        });
    }

    String taskId() {
        return taskId;
    }

    String goalText() {
        return goal.getText().toString();
    }

    View view() {
        return view;
    }

    void start() {
        if (goalText().trim().isEmpty()) {
            return;
        }
        showLiveUi(true);
        setRunning(true);
        log.setText("");
        append(activity.getString(R.string.starting));
        listener.onTaskStart(this);
    }

    void append(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        activity.runOnUiThread(() -> {
            log.append(text.endsWith("\n") ? text : text + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
            if (text.contains("automation finished")
                    || text.contains("automation failed")
                    || text.contains("automation cancelled")
                    || text.contains("[start error]")
                    || text.contains("[rpc error]")) {
                setRunning(false);
            }
        });
    }

    void showFrame(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap != null) {
                activity.runOnUiThread(() -> {
                    latestFrame = bitmap;
                    screen.setImageBitmap(bitmap);
                    if (dialogScreen != null) {
                        dialogScreen.setImageBitmap(bitmap);
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    void showLiveUi(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        stop.setVisibility(visibility);
        screenBox.setVisibility(visibility);
        logScroll.setVisibility(visibility);
    }

    void setRunning(boolean running) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> setRunning(running));
            return;
        }
        if (running) {
            hideKeyboard();
        }
        goal.setVisibility(running ? View.GONE : View.VISIBLE);
        run.setEnabled(!running);
        run.setText(running ? activity.getString(R.string.running) : activity.getString(R.string.run));
        stop.setVisibility(running ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager input = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) {
            input.hideSoftInputFromWindow(goal.getWindowToken(), 0);
        }
        goal.clearFocus();
    }

    private void showScreenDialog() {
        if (screenDialog != null && screenDialog.isShowing()) {
            return;
        }
        View holder = activity.getLayoutInflater().inflate(R.layout.dialog_screen_preview, null, false);
        int previewHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.66f);
        holder.setMinimumHeight(previewHeight);
        ImageView preview = holder.findViewById(R.id.dialogScreenImage);
        if (latestFrame != null) {
            preview.setImageBitmap(latestFrame);
        }
        dialogScreen = preview;
        screenDialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.screen_preview_title))
                .setView(holder)
                .setPositiveButton(activity.getString(R.string.close), null)
                .create();
        screenDialog.setOnDismissListener(dialog -> {
            dialogScreen = null;
            screenDialog = null;
        });
        screenDialog.setOnShowListener(dialog -> {
            android.view.Window window = screenDialog.getWindow();
            if (window != null) {
                int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f);
                int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.78f);
                window.setLayout(width, height);
            }
        });
        screenDialog.show();
    }
}
