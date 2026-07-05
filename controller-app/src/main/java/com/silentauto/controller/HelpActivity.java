package com.silentauto.controller;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public final class HelpActivity extends Activity {
    private static final int PAGE_PADDING_DP = 28;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setupSystemBars();
        setContentView(R.layout.activity_help);
        LinearLayout page = findViewById(R.id.helpPage);

        applyPageInsets(page, PAGE_PADDING_DP);

    }

    private void setupSystemBars() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
    }

    private void applyPageInsets(LinearLayout layout, int horizontalDp) {
        int inset = dp(horizontalDp);
        layout.setPadding(inset, inset + statusBarHeight(), inset, inset + navigationBarHeight());
        layout.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left = inset + windowInsets.getSystemWindowInsetLeft();
            int top = inset + Math.max(statusBarHeight(), windowInsets.getSystemWindowInsetTop());
            int right = inset + windowInsets.getSystemWindowInsetRight();
            int bottom = inset + windowInsets.getSystemWindowInsetBottom();
            view.setPadding(left, top, right, bottom);
            return windowInsets;
        });
        requestInsetsWhenAttached(layout);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id <= 0) {
            return 0;
        }
        return getResources().getDimensionPixelSize(id);
    }

    private int navigationBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (id <= 0) {
            return 0;
        }
        return getResources().getDimensionPixelSize(id);
    }

    private void requestInsetsWhenAttached(View view) {
        if (view.isAttachedToWindow()) {
            view.requestApplyInsets();
            return;
        }
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View attached) {
                attached.removeOnAttachStateChangeListener(this);
                attached.requestApplyInsets();
            }

            @Override
            public void onViewDetachedFromWindow(View detached) {
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
