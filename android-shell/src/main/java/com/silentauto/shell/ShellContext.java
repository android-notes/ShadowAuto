package com.silentauto.shell;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Application;
import android.app.Instrumentation;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ShellContext extends ContextWrapper {
    static final String PACKAGE_NAME = "com.android.shell";
    private static final ShellContext INSTANCE = new ShellContext();

    static void init() {
        Workarounds.apply();
    }

    static ShellContext get() {
        return INSTANCE;
    }

    private ShellContext() {
        super(Workarounds.systemContext());
    }

    @Override
    public String getPackageName() {
        return PACKAGE_NAME;
    }

    @Override
    public String getOpPackageName() {
        return PACKAGE_NAME;
    }

    @TargetApi(31)
    @Override
    public AttributionSource getAttributionSource() {
        return new AttributionSource.Builder(Process.SHELL_UID).setPackageName(PACKAGE_NAME).build();
    }

    @SuppressWarnings("unused")
    public int getDeviceId() {
        return 0;
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @SuppressLint("SoonBlockedPrivateApi")
    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (service == null) {
            return null;
        }
        if (Context.CLIPBOARD_SERVICE.equals(name) || Context.ACTIVITY_SERVICE.equals(name)) {
            try {
                Field field = service.getClass().getDeclaredField("mContext");
                field.setAccessible(true);
                field.set(service, this);
            } catch (Throwable ignored) {
            }
        }
        return service;
    }

    @SuppressLint("PrivateApi,DiscouragedPrivateApi")
    private static final class Workarounds {
        private static final Class<?> ACTIVITY_THREAD_CLASS;
        private static final Object ACTIVITY_THREAD;

        static {
            try {
                ACTIVITY_THREAD_CLASS = Class.forName("android.app.ActivityThread");
                Constructor<?> ctor = ACTIVITY_THREAD_CLASS.getDeclaredConstructor();
                ctor.setAccessible(true);
                ACTIVITY_THREAD = ctor.newInstance();
                Field current = ACTIVITY_THREAD_CLASS.getDeclaredField("sCurrentActivityThread");
                current.setAccessible(true);
                current.set(null, ACTIVITY_THREAD);
                Field system = ACTIVITY_THREAD_CLASS.getDeclaredField("mSystemThread");
                system.setAccessible(true);
                system.setBoolean(ACTIVITY_THREAD, true);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        static void apply() {
            if (Build.VERSION.SDK_INT >= 31) {
                fillConfigurationController();
            }
            fillAppInfo();
            fillAppContext();
        }

        static Context systemContext() {
            try {
                Method method = ACTIVITY_THREAD_CLASS.getDeclaredMethod("getSystemContext");
                return (Context) method.invoke(ACTIVITY_THREAD);
            } catch (Throwable e) {
                throw new IllegalStateException("system context unavailable", e);
            }
        }

        private static void fillAppInfo() {
            try {
                Class<?> bindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
                Object bindData = bindDataClass.getDeclaredConstructor().newInstance();
                ApplicationInfo appInfo = new ApplicationInfo();
                appInfo.packageName = PACKAGE_NAME;
                Field appInfoField = bindDataClass.getDeclaredField("appInfo");
                appInfoField.setAccessible(true);
                appInfoField.set(bindData, appInfo);
                Field bound = ACTIVITY_THREAD_CLASS.getDeclaredField("mBoundApplication");
                bound.setAccessible(true);
                bound.set(ACTIVITY_THREAD, bindData);
            } catch (Throwable ignored) {
            }
        }

        private static void fillAppContext() {
            try {
                Application app = Instrumentation.newApplication(Application.class, ShellContext.get());
                Field field = ACTIVITY_THREAD_CLASS.getDeclaredField("mInitialApplication");
                field.setAccessible(true);
                field.set(ACTIVITY_THREAD, app);
            } catch (Throwable ignored) {
            }
        }

        private static void fillConfigurationController() {
            try {
                Class<?> controllerClass = Class.forName("android.app.ConfigurationController");
                Class<?> internalClass = Class.forName("android.app.ActivityThreadInternal");
                Constructor<?> ctor = controllerClass.getDeclaredConstructor(internalClass);
                ctor.setAccessible(true);
                Object controller = ctor.newInstance(ACTIVITY_THREAD);
                Field field = ACTIVITY_THREAD_CLASS.getDeclaredField("mConfigurationController");
                field.setAccessible(true);
                field.set(ACTIVITY_THREAD, controller);
            } catch (Throwable ignored) {
            }
        }
    }
}
