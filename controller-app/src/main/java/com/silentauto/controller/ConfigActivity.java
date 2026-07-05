package com.silentauto.controller;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ConfigActivity extends Activity implements AiConfigScreen.Listener {
    static final String EXTRA_FROM_SETTINGS = "fromSettings";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private AiConfigStore store;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        UiUtils.setupSystemBars(this);
        store = new AiConfigStore(this);
        boolean fromSettings = getIntent().getBooleanExtra(EXTRA_FROM_SETTINGS, false);
        new AiConfigScreen(this, main, io, store, this).show(store.load(), fromSettings);
    }

    @Override
    public void onConfigSaved(AiConfig config) {
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onConfigCancelled() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
