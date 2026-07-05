package com.silentauto.shell;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Method;

final class WindowManagerBridge {
    static final int DISPLAY_IME_POLICY_LOCAL = 0;
    static final int DISPLAY_IME_POLICY_FALLBACK_DISPLAY = 1;
    static final int DISPLAY_IME_POLICY_HIDE = 2;

    private final IInterface manager;
    private Method getDisplayImePolicy;
    private Method setDisplayImePolicy;

    WindowManagerBridge() {
        manager = service("window", "android.view.IWindowManager");
    }

    @TargetApi(29)
    int getDisplayImePolicy(int displayId) {
        try {
            Method method = getGetDisplayImePolicy();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return (int) method.invoke(manager, displayId);
            }
            return (boolean) method.invoke(manager, displayId)
                    ? DISPLAY_IME_POLICY_LOCAL
                    : DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    @TargetApi(29)
    boolean setDisplayImePolicy(int displayId, int policy) {
        try {
            Method method = getSetDisplayImePolicy();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                method.invoke(manager, displayId, policy);
            } else if (policy != DISPLAY_IME_POLICY_HIDE) {
                method.invoke(manager, displayId, policy == DISPLAY_IME_POLICY_LOCAL);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Method getGetDisplayImePolicy() throws NoSuchMethodException {
        if (getDisplayImePolicy == null) {
            getDisplayImePolicy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? manager.getClass().getMethod("getDisplayImePolicy", int.class)
                    : manager.getClass().getMethod("shouldShowIme", int.class);
        }
        return getDisplayImePolicy;
    }

    private Method getSetDisplayImePolicy() throws NoSuchMethodException {
        if (setDisplayImePolicy == null) {
            setDisplayImePolicy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? manager.getClass().getMethod("setDisplayImePolicy", int.class, int.class)
                    : manager.getClass().getMethod("setShouldShowIme", int.class, boolean.class);
        }
        return setDisplayImePolicy;
    }

    private static IInterface service(String name, String type) {
        try {
            Method getService = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, name);
            Method asInterface = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
            return (IInterface) asInterface.invoke(null, binder);
        } catch (Throwable e) {
            throw new IllegalStateException("service unavailable: " + name, e);
        }
    }
}
