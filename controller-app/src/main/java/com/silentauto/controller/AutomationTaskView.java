package com.silentauto.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.TextureView;

import org.json.JSONObject;

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
    private final TextureView screen;
    private final TextView log;
    private final ScrollView logScroll;
    private final Button run;
    private final Button stop;

    private H264VideoDecoder screenDecoder;
    private AlertDialog screenDialog;
    private H264VideoDecoder dialogDecoder;
    private VideoConfig videoConfig;

    AutomationTaskView(Activity activity, Handler main, String taskId, Listener listener) {
        this.activity = activity;
        this.main = main;
        this.taskId = taskId;
        this.listener = listener;
        view = activity.getLayoutInflater().inflate(R.layout.item_task, null, false);
        goal = view.findViewById(R.id.taskGoal);
        screenBox = view.findViewById(R.id.screenBox);
        screen = view.findViewById(R.id.screenTexture);
        screen.setOpaque(true);
        log = view.findViewById(R.id.logText);
        logScroll = view.findViewById(R.id.logScroll);
        run = view.findViewById(R.id.runButton);
        stop = view.findViewById(R.id.stopButton);
        screenDecoder = new H264VideoDecoder(screen, main);
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
        resetVideo();
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

    void showVideoConfig(JSONObject params) {
        activity.runOnUiThread(() -> {
            videoConfig = VideoConfig.from(params);
            if (screenDecoder != null) {
                videoConfig.apply(screenDecoder);
            }
            if (dialogDecoder != null) {
                videoConfig.apply(dialogDecoder);
            }
        });
    }

    void showVideoSample(JSONObject params) {
        String data = params.optString("data");
        long ptsUs = params.optLong("ptsUs");
        int flags = params.optInt("flags");
        activity.runOnUiThread(() -> {
            if (screenDecoder != null) {
                screenDecoder.queueSample(data, ptsUs, flags);
            }
            if (dialogDecoder != null) {
                dialogDecoder.queueSample(data, ptsUs, flags);
            }
        });
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
        TextureView preview = holder.findViewById(R.id.dialogScreenTexture);
        preview.setOpaque(true);
        H264VideoDecoder decoder = new H264VideoDecoder(preview, main);
        dialogDecoder = decoder;
        if (videoConfig != null) {
            videoConfig.apply(decoder);
        }
        screenDialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.screen_preview_title))
                .setView(holder)
                .setPositiveButton(activity.getString(R.string.close), null)
                .create();
        screenDialog.setOnDismissListener(dialog -> {
            if (dialogDecoder != null) {
                dialogDecoder.release();
            }
            dialogDecoder = null;
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

    private void resetVideo() {
        videoConfig = null;
        if (screenDecoder != null) {
            screenDecoder.release();
        }
        if (dialogDecoder != null) {
            dialogDecoder.release();
            dialogDecoder = null;
        }
        screenDecoder = new H264VideoDecoder(screen, main);
    }

    private static final class VideoConfig {
        final String mime;
        final int width;
        final int height;
        final String csd0;
        final String csd1;

        private VideoConfig(String mime, int width, int height, String csd0, String csd1) {
            this.mime = mime;
            this.width = width;
            this.height = height;
            this.csd0 = csd0;
            this.csd1 = csd1;
        }

        static VideoConfig from(JSONObject params) {
            return new VideoConfig(
                    params.optString("mime", "video/avc"),
                    params.optInt("width"),
                    params.optInt("height"),
                    params.optString("csd0"),
                    params.optString("csd1")
            );
        }

        void apply(H264VideoDecoder decoder) {
            decoder.configure(mime, width, height, csd0, csd1);
        }
    }
}
