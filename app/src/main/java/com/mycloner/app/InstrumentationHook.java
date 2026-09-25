package com.mycloner.app;

import android.app.Instrumentation;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InstrumentationHook {
    private static final String TAG = "InstrumentationHook";
    private static volatile boolean installed = false;
    private static volatile String failureReason = null;

    public static synchronized boolean install() {
        if (installed) return true;
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object activityThread = currentActivityThread.invoke(null);
            if (activityThread == null) {
                failureReason = "currentActivityThread() returned null";
                return false;
            }

            Field mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            Instrumentation original = (Instrumentation) mInstrumentationField.get(activityThread);

            if (original instanceof GuestInstrumentation) {
                installed = true;
                return true;
            }

            GuestInstrumentation wrapper = new GuestInstrumentation(original);
            mInstrumentationField.set(activityThread, wrapper);
            installed = true;
            Log.i(TAG, "instrumentation hook installed");
            return true;
        } catch (Throwable t) {
            failureReason = t.getClass().getSimpleName() + ": " + t.getMessage();
            Log.e(TAG, "install failed — likely blocked by hidden-API enforcement on this Android version", t);
            return false;
        }
    }

    public static boolean isInstalled() { return installed; }
    public static String getFailureReason() { return failureReason; }
}
