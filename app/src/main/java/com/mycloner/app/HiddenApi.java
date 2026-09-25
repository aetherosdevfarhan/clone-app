package com.mycloner.app;

import android.util.Log;
import java.lang.reflect.Method;

public class HiddenApi {
    private static final String TAG = "HiddenApi";
    private static volatile boolean attempted = false;
    private static volatile boolean ok = false;

    public static boolean exempt() {
        if (attempted) return ok;
        attempted = true;
        try {
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object runtime = getRuntime.invoke(null);

            Method setExemptions = vmRuntimeClass.getDeclaredMethod(
                    "setHiddenApiExemptions", String[].class);
            setExemptions.setAccessible(true);
            setExemptions.invoke(runtime, (Object) new String[]{"L"});

            ok = true;
            Log.i(TAG, "hidden API exemptions granted");
        } catch (Throwable t) {
            ok = false;
            Log.w(TAG, "hidden API exemption failed — blocked on this ROM/version: " + t);
        }
        return ok;
    }

    public static boolean isOk() {
        return ok;
    }
}
