package com.mycloner.app;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GuestInstrumentation extends Instrumentation {
    private static final String TAG = "GuestInstrumentation";
    private final Instrumentation base;

    public GuestInstrumentation(Instrumentation base) {
        this.base = base;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (intent != null && StubSlots.isStubClassName(className)) {
            String targetClass = intent.getStringExtra(LaunchRegistry.EXTRA_TARGET_CLASS);
            String cloneId = intent.getStringExtra(LaunchRegistry.EXTRA_CLONE_ID);
            ClassLoader guestLoader = cloneId == null ? null : LaunchRegistry.loaderFor(cloneId);
            if (guestLoader == null && cloneId != null) {
                // The host process may have been killed while the proxy task
                // remained in Recents. Rebuild the guest loader/context on
                // demand instead of rendering the stub forever.
                try {
                    CloneEntry entry = CloneStore.byId(
                            getInstrumentationContext(), cloneId);
                    if (entry != null) {
                        GuestLoader.Result result = GuestLoader.load(
                                getInstrumentationContext(), entry);
                        if (result.loader != null && result.guestContext != null) {
                            LaunchRegistry.register(cloneId, result.loader, result.guestContext, result.appClassName);
                            guestLoader = result.loader;
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "failed to restore clone " + cloneId, t);
                }
            }
            if (targetClass != null && guestLoader != null) {
                try {
                    Class<?> real = Class.forName(targetClass, false, guestLoader);
                    Activity instance = (Activity) real.newInstance();
                    Log.i(TAG, "substituted " + targetClass + " for stub (clone " + cloneId + ")");
                    try {
                        CrashLog.checkpoint(getInstrumentationContext(),
                                "clone " + cloneId + ": substituted " + targetClass + " for stub");
                    } catch (Throwable ignored) {}
                    return instance;
                } catch (Throwable t) {
                    Log.e(TAG, "failed to instantiate guest activity " + targetClass, t);
                    try {
                        CrashLog.logCaught(getInstrumentationContext(),
                                "failed to instantiate guest activity " + targetClass + " (clone " + cloneId + ")", t);
                    } catch (Throwable ignored) {}
                }
            }
        }
        return base.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        patchBaseContext(activity);
        base.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle, android.os.PersistableBundle persistentState) {
        patchBaseContext(activity);
        base.callActivityOnCreate(activity, icicle, persistentState);
    }

    private void patchBaseContext(Activity activity) {
        Context hostCtx = null;
        String cloneId = null;
        try {
            hostCtx = getInstrumentationContext();
            Intent intent = activity.getIntent();
            cloneId = intent == null ? null
                    : intent.getStringExtra(LaunchRegistry.EXTRA_CLONE_ID);
            if (cloneId == null) {
                cloneId = LaunchRegistry.cloneIdForLoader(
                        activity.getClass().getClassLoader());
            }
            if (cloneId == null) return;
            CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": patchBaseContext start");

            Context guestContext = LaunchRegistry.guestContextFor(cloneId);
            if (guestContext == null) {
                CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": no guestContext registered, aborting patch");
                return;
            }

            Field mBase = ContextWrapper.class.getDeclaredField("mBase");
            mBase.setAccessible(true);
            mBase.set(activity, guestContext);
            CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": base context patched");

            Application guestApp = ensureGuestApplication(hostCtx, cloneId);
            if (guestApp != null) {
                try {
                    Field mApplication = Activity.class.getDeclaredField("mApplication");
                    mApplication.setAccessible(true);
                    mApplication.set(activity, guestApp);
                    CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": mApplication patched");
                } catch (Throwable t) {
                    CrashLog.logCaught(hostCtx, "failed to patch mApplication for clone " + cloneId, t);
                }
            }
        } catch (Throwable t) {
            if (hostCtx != null) CrashLog.logCaught(hostCtx, "patchBaseContext failed (clone " + cloneId + ")", t);
            Log.e(TAG, "patchBaseContext failed", t);
        }
    }

    /**
     * Lazily creates and initializes the guest package's real Application
     * object (its declared android:name class, or plain Application if
     * none) - once per clone, cached in LaunchRegistry. Without this, code
     * in the guest Activity that expects its own Application subclass
     * (SDK init done in Application.onCreate - Firebase, crash reporting,
     * DI containers, etc.) sees CloneApplication instead and can NPE or
     * silently skip that init. This gets much closer, but Application.onCreate()
     * for a heavy app can still throw if it depends on ContentProviders,
     * background Services, or other manifest components this host process
     * never registers - failures here are caught and logged, not fatal to
     * the activity launch.
     */
    private Application ensureGuestApplication(Context hostCtx, String cloneId) {
        Application existing = LaunchRegistry.appFor(cloneId);
        if (existing != null) return existing;
        try {
            ClassLoader loader = LaunchRegistry.loaderFor(cloneId);
            Context guestContext = LaunchRegistry.guestContextFor(cloneId);
            if (loader == null || guestContext == null) return null;

            String appClassName = LaunchRegistry.appClassNameFor(cloneId);
            Class<?> appClass = appClassName != null
                    ? Class.forName(appClassName, false, loader)
                    : Application.class;
            CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": instantiating " + appClass.getName());
            Application app = (Application) appClass.newInstance();

            // Application.attach(Context) is package-private; it sets up
            // mLoadedApk/mBase and is what the system itself calls before
            // Application.onCreate() during a normal app launch.
            CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": attach() start");
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            attach.invoke(app, guestContext);
            CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": attach() done");

            // Real process startup runs every declared <provider>'s onCreate()
            // BEFORE Application.onCreate() - a lot of SDK auto-init (androidx
            // App Startup, Firebase, WorkManager) depends on that order. Do the
            // same here, or code in onCreate() can hang waiting on init that
            // never ran.
            CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": provider init start");
            CloneEntry entry = CloneStore.byId(hostCtx, cloneId);
            if (entry != null) {
                GuestProviders.initAll(hostCtx, guestContext, loader, entry.pkg, cloneId);
            }
            CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": provider init done");

            CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": Application.onCreate() start");
            app.onCreate();
            CrashLog.checkpoint(hostCtx, "clone " + cloneId + ": Application.onCreate() done");

            LaunchRegistry.registerApp(cloneId, app);
            Log.i(TAG, "guest Application created for clone " + cloneId + " (" + appClass.getName() + ")");
            return app;
        } catch (Throwable t) {
            CrashLog.logCaught(hostCtx, "guest Application init failed for clone " + cloneId
                    + " - continuing without it", t);
            Log.e(TAG, "guest Application init failed for clone " + cloneId
                    + " - continuing without it", t);
            return null;
        }
    }

    /** Returns the host application context without keeping a strong Activity reference. */
    private Context getInstrumentationContext() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        Field f = at.getDeclaredField("mInitialApplication");
        f.setAccessible(true);
        java.lang.reflect.Method current = at.getDeclaredMethod("currentActivityThread");
        current.setAccessible(true);
        Object thread = current.invoke(null);
        Object app = f.get(thread);
        if (app instanceof Context) return (Context) app;
        throw new IllegalStateException("initial application unavailable");
    }

}
