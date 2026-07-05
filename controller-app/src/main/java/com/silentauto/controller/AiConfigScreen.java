package com.silentauto.controller;

import android.app.Activity;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

final class AiConfigScreen {
    interface Listener {
        void onConfigSaved(AiConfig config);

        void onConfigCancelled();
    }

    private static final int CONFIG_PADDING_DP = 28;

    private final Activity activity;
    private final Handler main;
    private final ExecutorService io;
    private final AiConfigStore store;
    private final Listener listener;
    private AiConfig config;

    AiConfigScreen(Activity activity, Handler main, ExecutorService io, AiConfigStore store, Listener listener) {
        this.activity = activity;
        this.main = main;
        this.io = io;
        this.store = store;
        this.listener = listener;
    }

    void show(AiConfig currentConfig, boolean fromSettings) {
        config = currentConfig;
        activity.setContentView(R.layout.activity_config);
        LinearLayout page = activity.findViewById(R.id.configPage);
        ImageButton close = activity.findViewById(R.id.configCloseButton);
        EditText key = activity.findViewById(R.id.apiKey);
        AutoCompleteTextView base = activity.findViewById(R.id.apiBase);
        Spinner models = activity.findViewById(R.id.modelSpinner);
        TextView status = activity.findViewById(R.id.modelStatus);
        Button test = activity.findViewById(R.id.testButton);
        Button cancel = activity.findViewById(R.id.cancelConfigButton);

        UiUtils.applyPageInsets(activity, page, CONFIG_PADDING_DP);
        UiUtils.clearButtonTint(test, cancel);
        close.setContentDescription(activity.getString(R.string.back));
        close.setVisibility(fromSettings ? View.VISIBLE : View.GONE);
        cancel.setVisibility(fromSettings ? View.VISIBLE : View.GONE);
        key.setText(config.apiKey);
        base.setText(config.apiBase, false);
        setupEndpointInput(base);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, new ArrayList<>());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        models.setAdapter(adapter);

        Runnable[] pending = new Runnable[1];
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pending[0] != null) {
                    main.removeCallbacks(pending[0]);
                }
                pending[0] = () -> loadModels(key.getText().toString(), base.getText().toString(), adapter, models, status, test);
                main.postDelayed(pending[0], 700);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        key.addTextChangedListener(watcher);
        base.addTextChangedListener(watcher);
        main.postDelayed(() -> loadModels(key.getText().toString(), base.getText().toString(), adapter, models, status, test), 250);

        test.setOnClickListener(v -> {
            String enteredKey = key.getText().toString();
            String enteredBase = base.getText().toString();
            String selected = selectedModel(models);
            status.setText(activity.getString(R.string.test_running));
            io.execute(() -> {
                try {
                    testChat(enteredKey, enteredBase, selected);
                    AiConfig saved = store.save(enteredKey, enteredBase, selected);
                    activity.runOnUiThread(() -> listener.onConfigSaved(saved));
                } catch (Exception e) {
                    activity.runOnUiThread(() -> status.setText(activity.getString(R.string.test_failed, e.getMessage())));
                }
            });
        });
        close.setOnClickListener(v -> listener.onConfigCancelled());
        cancel.setOnClickListener(v -> listener.onConfigCancelled());
    }

    private void setupEndpointInput(AutoCompleteTextView edit) {
        ArrayAdapter<ProviderOption> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_dropdown_item_1line,
                providerOptions()
        );
        edit.setAdapter(adapter);
        edit.setThreshold(0);
        edit.setDropDownHeight(UiUtils.dp(activity, 240));
        edit.setOnClickListener(v -> edit.showDropDown());
        edit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                edit.showDropDown();
            }
        });
        edit.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (item instanceof ProviderOption) {
                edit.setText(((ProviderOption) item).url, false);
                edit.setSelection(edit.getText().length());
            }
        });
    }

    private void loadModels(String key, String base, ArrayAdapter<String> adapter, Spinner spinner, TextView status, Button test) {
        if (key.trim().isEmpty() || base.trim().isEmpty()) {
            adapter.clear();
            adapter.notifyDataSetChanged();
            spinner.setVisibility(View.GONE);
            status.setText(activity.getString(R.string.model_empty_status));
            test.setVisibility(View.GONE);
            return;
        }
        status.setText(activity.getString(R.string.model_loading_status));
        io.execute(() -> {
            try {
                List<String> models = fetchModels(key, base);
                activity.runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(models);
                    adapter.notifyDataSetChanged();
                    int i = modelIndex(models, config.model);
                    if (i >= 0) {
                        spinner.setSelection(i);
                    }
                    status.setText(models.isEmpty()
                            ? activity.getString(R.string.model_none_status)
                            : activity.getString(R.string.model_loaded_status, models.size()));
                    spinner.setVisibility(models.isEmpty() ? View.GONE : View.VISIBLE);
                    test.setVisibility(models.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    status.setText(activity.getString(R.string.model_load_failed, e.getMessage()));
                    spinner.setVisibility(View.GONE);
                    test.setVisibility(View.GONE);
                });
            }
        });
    }

    private List<String> fetchModels(String key, String base) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(AiConfigStore.trimSlash(base) + "/models").openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + key.trim());
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        int code = conn.getResponseCode();
        String text = read(conn, code);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }
        JSONArray data = new JSONObject(text).optJSONArray("data");
        ArrayList<String> result = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                String id = data.getJSONObject(i).optString("id");
                if (!id.isEmpty()) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    private void testChat(String key, String base, String selectedModel) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", selectedModel);
        body.put("stream", false);
        body.put("messages", new JSONArray()
                .put(new JSONObject().put("role", "user").put("content", "Say OK.")));
        HttpURLConnection conn = (HttpURLConnection) new URL(AiConfigStore.trimSlash(base) + "/chat/completions").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + key.trim());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        String text = read(conn, code);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }
        String content = new JSONObject(text).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").optString("content");
        if (content.trim().isEmpty()) {
            throw new IllegalStateException("empty output");
        }
    }

    private List<ProviderOption> providerOptions() {
        ArrayList<ProviderOption> options = new ArrayList<>();
        options.add(new ProviderOption(activity.getString(R.string.provider_deepseek), "https://api.deepseek.com"));
        options.add(new ProviderOption(activity.getString(R.string.provider_chatgpt), "https://api.openai.com/v1"));
        options.add(new ProviderOption(activity.getString(R.string.provider_zhipu), "https://open.bigmodel.cn/api/paas/v4"));
        options.add(new ProviderOption(activity.getString(R.string.provider_qwen), "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        return options;
    }

    private String selectedModel(Spinner spinner) {
        Object item = spinner.getSelectedItem();
        return item == null ? "" : item.toString();
    }

    private int modelIndex(List<String> models, String value) {
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).equals(value)) {
                return i;
            }
        }
        return -1;
    }

    private static String read(HttpURLConnection conn, int code) throws Exception {
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
            return out.toString();
        }
    }

    private static final class ProviderOption {
        final String name;
        final String url;

        ProviderOption(String name, String url) {
            this.name = name;
            this.url = url;
        }

        @Override
        public String toString() {
            return name + " - " + url;
        }
    }
}
