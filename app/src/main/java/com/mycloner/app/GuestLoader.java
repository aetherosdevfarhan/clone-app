package com.mycloner.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GuestLoader {
    public static final int INFO = 0, OK = 1, FAIL = 2;

    public static class Step {
        public final String name, detail;
        public final int state;
        Step(String name, int state, String detail) {
            this.name = name;
            this.state = state;
            this.detail = detail;
        }
    }

    public static class Result {
        public final List<Step> steps = new ArrayList<>();
        public String verdict = "";
        public Context guestContext;   // storage-isolated (CloneContext), ready to use as an Activity's base
        public ClassLoader loader;
        public Class<?> launcherClass;
        public String launcherName;
        public String appClassName; // ApplicationInfo.className, null = plain android.app.Application
        void add(String n, int s, String d) { steps.add(new Step(n, s, d)); }
    }

    private static String err(Throwable e) {
        String m = e.getClass().getSimpleName();
        if (e.getMessage() != null) m += ": " + e.getMessage();
        return m;
    }

    /** entry identifies which clone slot this is (its id drives storage isolation). */
    public static Result load(Context host, CloneEntry entry) {
        String pkg = entry.pkg;
        long t0 = System.currentTimeMillis();
        Result r = new Result();
        PackageManager pm = host.getPackageManager();
        ApplicationInfo ai = null;

        try {
            PackageInfo pi = pm.getPackageInfo(pkg, 0);
            ai = pi.applicationInfo;
            r.add("Package", INFO, pkg + " v" + pi.versionName);
            r.add("Target SDK", INFO, "guest targets " + ai.targetSdkVersion
                    + ", phone is API " + Build.VERSION.SDK_INT);
            int splits = ai.splitSourceDirs == null ? 0 : ai.splitSourceDirs.length;
            long mb = new File(ai.sourceDir).length() / (1024 * 1024);
            r.add("APK", INFO, "base " + mb + " MB, " + splits + " split(s)");
            String[] libs = ai.nativeLibraryDir == null ? null
                    : new File(ai.nativeLibraryDir).list();
            r.add("Native libs", INFO, libs == null ? "none found or not readable"
                    : libs.length + " file(s)");
            r.add("Application class", INFO,
                    ai.className == null ? "default" : ai.className);
            r.add("Clone slot", INFO, "#" + entry.slot + " (" + entry.label + "), id " + entry.id);
            r.appClassName = ai.className;
        } catch (Throwable e) {
            r.add("Package info", FAIL, err(e));
            r.verdict = "Could not read the app. Is it still installed?";
            return r;
        }

        // Launcher activity
        try {
            Intent li = pm.getLaunchIntentForPackage(pkg);
            ComponentName cn = li == null ? null : li.getComponent();
            if (cn == null) {
                r.add("Launcher activity", FAIL, "no launch intent found");
            } else {
                String declaredName = cn.getClassName();
                String resolvedName = declaredName;
                String note = "";
                try {
                    // If cn points at an <activity-alias> (common for apps
                    // that swap launcher icons - seasonal/beta variants),
                    // getActivityInfo() resolves it and returns the real
                    // implementing class in .targetActivity. The alias
                    // name itself is never a loadable class.
                    android.content.pm.ActivityInfo actInfo = pm.getActivityInfo(
                            cn, PackageManager.MATCH_ALL | PackageManager.GET_META_DATA);
                    if (actInfo.targetActivity != null
                            && !actInfo.targetActivity.equals(declaredName)) {
                        resolvedName = actInfo.targetActivity;
                        note = " (activity-alias -> " + resolvedName + ")";
                    }
                } catch (Throwable ignored) {
                    // Couldn't resolve - fall back to the declared name below,
                    // the Launcher class step will report the real failure.
                }
                r.launcherName = resolvedName;
                r.add("Launcher activity", OK, declaredName + note);
            }
        } catch (Throwable e) {
            r.add("Launcher activity", FAIL, err(e));
        }

        // Guest class loader (+ raw package context, before storage isolation is applied)
        ClassLoader loader = null;
        Context rawGuestContext = null;
        String how = "";
        String ctxErr = null;
        try {
            rawGuestContext = host.createPackageContext(pkg,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            loader = rawGuestContext.getClassLoader();
            how = "system-made loader";
        } catch (Throwable e) {
            ctxErr = err(e);
        }
        if (loader == null) {
            try {
                // Base APK alone isn't enough for split/bundled apps - the
                // launcher class (or classes it touches) can live in a
                // split. Chain every split dex onto the path so we don't
                // misreport a class-not-found that's really a missing split.
                StringBuilder dexPath = new StringBuilder(ai.sourceDir);
                if (ai.splitSourceDirs != null) {
                    for (String split : ai.splitSourceDirs) {
                        dexPath.append(File.pathSeparator).append(split);
                    }
                }
                loader = new DexClassLoader(dexPath.toString(),
                        host.getCodeCacheDir().getAbsolutePath(),
                        ai.nativeLibraryDir,
                        host.getClassLoader().getParent());
                how = "DexClassLoader fallback"
                        + (ai.splitSourceDirs != null ? " (+" + ai.splitSourceDirs.length + " split dex)" : "");
            } catch (Throwable e) {
                r.add("Class loader", FAIL, err(e));
            }
        }
        if (ctxErr != null) {
            r.add("Guest context", loader != null ? INFO : FAIL,
                    ctxErr + (loader != null ? "\n(fallback used)" : ""));
        } else {
            r.add("Guest context", OK, "createPackageContext accepted");
        }
        if (loader != null) {
            r.add("Class loader", OK, how + " (" + loader.getClass().getSimpleName() + ")");
        }
        r.loader = loader;

        // Wrap whatever context we have (or the host itself, as a last resort) so
        // file/db/prefs calls redirect into this clone's own isolated folder instead
        // of the guest's real - and inaccessible - data directory.
        Context storageBase = rawGuestContext != null ? rawGuestContext : host;
        r.guestContext = new CloneContext(storageBase, host, entry.id);
        r.add("Storage isolation", OK,
                "files/prefs/db redirected under clones/" + entry.id
                        + (rawGuestContext == null ? " (built on host context - resources may not match guest)" : ""));

        // Load the launcher class (loads it, does NOT run any guest code)
        if (loader != null && r.launcherName != null) {
            try {
                Class<?> c = Class.forName(r.launcherName, false, loader);
                r.launcherClass = c;
                boolean act = android.app.Activity.class.isAssignableFrom(c);
                r.add("Launcher class", act ? OK : FAIL,
                        c.getName() + (act ? " is an Activity" : " is NOT an Activity")
                                + "\nsuper: " + c.getSuperclass().getName());
            } catch (ClassNotFoundException e) {
                r.add("Launcher class", FAIL,
                        err(e) + "\n(could be an activity-alias)");
            } catch (Throwable e) {
                r.add("Launcher class", FAIL, err(e));
            }
        }

        // Resources
        try {
            Resources res = pm.getResourcesForApplication(ai);
            String d = "resources opened";
            if (ai.icon != 0) {
                res.getDrawable(ai.icon, null);
                d += ", icon loaded";
            }
            r.add("Resources", OK, d);
        } catch (Throwable e) {
            r.add("Resources", FAIL, err(e));
        }

        Step firstFail = null;
        for (Step s : r.steps) {
            if (s.state == FAIL) { firstFail = s; break; }
        }
        r.verdict = firstFail == null
                ? "Ready: guest code, resources, and isolated storage are set up. Launch is still experimental."
                : "Blocked at \"" + firstFail.name + "\". Copy the report and send it to me.";
        r.add("Time", INFO, (System.currentTimeMillis() - t0) + " ms");
        return r;
    }
}
