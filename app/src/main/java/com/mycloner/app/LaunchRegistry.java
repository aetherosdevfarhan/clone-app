package com.mycloner.app;

import android.app.Application;
import android.content.Context;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LaunchRegistry {
    public static final String EXTRA_TARGET_CLASS = "com.mycloner.app.TARGET_CLASS";
    public static final String EXTRA_CLONE_ID = "com.mycloner.app.CLONE_ID";
    public static final String EXTRA_TARGET_PKG = "com.mycloner.app.TARGET_PKG";

    private static final Map<String, ClassLoader> loaders = new ConcurrentHashMap<>();
    private static final Map<String, Context> guestContexts = new ConcurrentHashMap<>();
    private static final Map<ClassLoader, String> cloneIdsByLoader = new ConcurrentHashMap<>();
    private static final Map<String, String> appClassNames = new ConcurrentHashMap<>();
    private static final Map<String, Application> apps = new ConcurrentHashMap<>();

    /** appClassName may be null (guest uses the plain android.app.Application). */
    public static void register(String cloneId, ClassLoader loader, Context guestContext, String appClassName) {
        if (cloneId == null) return;
        if (loader != null) {
            loaders.put(cloneId, loader);
            cloneIdsByLoader.put(loader, cloneId);
        }
        if (guestContext != null) guestContexts.put(cloneId, guestContext);
        if (appClassName != null) appClassNames.put(cloneId, appClassName);
    }

    public static ClassLoader loaderFor(String cloneId) {
        return cloneId == null ? null : loaders.get(cloneId);
    }

    public static Context guestContextFor(String cloneId) {
        return cloneId == null ? null : guestContexts.get(cloneId);
    }

    public static String appClassNameFor(String cloneId) {
        return cloneId == null ? null : appClassNames.get(cloneId);
    }

    public static Application appFor(String cloneId) {
        return cloneId == null ? null : apps.get(cloneId);
    }

    public static void registerApp(String cloneId, Application app) {
        if (cloneId != null && app != null) apps.put(cloneId, app);
    }

    public static String cloneIdForLoader(ClassLoader loader) {
        return loader == null ? null : cloneIdsByLoader.get(loader);
    }

    public static void unregister(String cloneId) {
        if (cloneId == null) return;
        ClassLoader loader = loaders.remove(cloneId);
        guestContexts.remove(cloneId);
        appClassNames.remove(cloneId);
        apps.remove(cloneId);
        if (loader != null) cloneIdsByLoader.remove(loader);
    }
}
