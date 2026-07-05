package com.silentauto.controller;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

final class UiUtils {
    private UiUtils() {
    }

    static void setupSystemBars(Activity activity) {
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
    }

    static void applyPageInsets(Activity activity, LinearLayout layout, int horizontalDp) {
        int inset = dp(activity, horizontalDp);
        layout.setPadding(inset, inset + statusBarHeight(activity), inset, inset + navigationBarHeight(activity));
        layout.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left = inset + windowInsets.getSystemWindowInsetLeft();
            int top = inset + Math.max(statusBarHeight(activity), windowInsets.getSystemWindowInsetTop());
            int right = inset + windowInsets.getSystemWindowInsetRight();
            int bottom = inset + windowInsets.getSystemWindowInsetBottom();
            view.setPadding(left, top, right, bottom);
            return windowInsets;
        });
        requestInsetsWhenAttached(layout);
    }

    static void applyMainInsets(Activity activity, FrameLayout host, LinearLayout page, View fab, FrameLayout.LayoutParams fabLp, int paddingDp) {
        int inset = dp(activity, paddingDp);
        int fabMargin = dp(activity, 24);
        page.setPadding(inset, inset + statusBarHeight(activity), inset, inset + navigationBarHeight(activity));
        fabLp.setMargins(0, 0, fabMargin, fabMargin + navigationBarHeight(activity));
        fab.setLayoutParams(fabLp);
        host.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left = inset + windowInsets.getSystemWindowInsetLeft();
            int top = inset + Math.max(statusBarHeight(activity), windowInsets.getSystemWindowInsetTop());
            int right = inset + windowInsets.getSystemWindowInsetRight();
            int bottom = inset + windowInsets.getSystemWindowInsetBottom();
            page.setPadding(left, top, right, bottom);
            fabLp.setMargins(0, 0, fabMargin + windowInsets.getSystemWindowInsetRight(), fabMargin + windowInsets.getSystemWindowInsetBottom());
            fab.setLayoutParams(fabLp);
            return windowInsets;
        });
        requestInsetsWhenAttached(host);
    }

    static void clearButtonTint(Button... buttons) {
        for (Button button : buttons) {
            button.setBackgroundTintList(null);
        }
    }

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int statusBarHeight(Activity activity) {
        int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id <= 0) {
            return 0;
        }
        return activity.getResources().getDimensionPixelSize(id);
    }

    private static int navigationBarHeight(Activity activity) {
        int id = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (id <= 0) {
            return 0;
        }
        return activity.getResources().getDimensionPixelSize(id);
    }

    private static void requestInsetsWhenAttached(View view) {
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
}
