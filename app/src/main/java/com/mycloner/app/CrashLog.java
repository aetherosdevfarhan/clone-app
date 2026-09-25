package com.mycloner.app;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashLog {
    private static final String FILE_NAME = "crash_log.txt";
    private static volatile boolean installed = false;

    public static synchronized void install(Context appContext) {
        if (installed) return;
        installed = true;
        final Context ctx = appContext.getApplicationContext();
        final Thread mainThread = ctx.getMainLooper().getThread();
        final Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                append(ctx, thread, ex);
            } catch (Throwable ignored) {
            }
            if (thread == mainThread) {
                if (original != null) original.uncaughtException(thread, ex);
            } else {
                write(ctx, "SUPPRESSED (background thread, not fatal): " + thread.getName()
                        + " - " + ex.getClass().getName() + ": " + ex.getMessage());
            }
        });
    }

    private static void append(Context c, Thread thread, Throwable ex) {
        File f = new File(c.getFilesDir(), FILE_NAME);
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String entry = "----- " + ts + " (thread: " + thread.getName() + ") -----\n" + sw + "\n";
        try (FileWriter fw = new FileWriter(f, true)) {
            fw.write(entry);
        } catch (Throwable ignored) {}
    }

    /**
     * Records a step reached, written and flushed immediately (open-write-close
     * per call). This exists specifically for hangs: an uncaught-exception
     * handler logs nothing if the app never throws, it just stops responding.
     * Checkpoints sprinkled through the risky launch path mean that even on a
     * silent hang, the log already has every step reached before the freeze -
     * so "where did it stop" becomes visible without logcat.
     */
    public static void checkpoint(Context c, String msg) {
        write(c, "STEP  " + msg);
    }

    /** Logs an exception the code caught and handled (i.e. didn't crash), so it's
     *  still visible in the log even though CrashLog's uncaught-handler never saw it. */
    public static void logCaught(Context c, String label, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        write(c, "CAUGHT  " + label + "\n" + sw);
    }

    private static void write(Context c, String line) {
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), FILE_NAME);
            String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            try (FileWriter fw = new FileWriter(f, true)) {
                fw.write(ts + "  " + line + "\n");
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Re-claims Thread's default uncaught-exception-handler slot. Some SDKs
     * (Firebase Crashlytics in particular) install their own handler during
     * init and do NOT chain back to whatever was set before them - which
     * silently steals crashes away from this logger with no trace at all.
     * Call this right after guest Application.onCreate() (and ideally again
     * right before the guest Activity's onCreate()) so our handler is back
     * on top, still chaining to whatever is currently installed underneath.
     *
     * Also changes what happens after logging: Android's default behavior is
     * to kill the WHOLE process for an uncaught exception on ANY thread, main
     * or background. In practice a lot of guest apps run non-essential
     * background work (vendor "bonus feature" code, analytics, cleanup jobs)
     * that can throw for reasons specific to running inside this host process
     * (e.g. a null Context some OEM helper expected to be set elsewhere).
     * Killing the whole clone over one broken background task, with the
     * symptom being "the app just closes" and no other clue, isn't useful -
     * so background-thread crashes are logged and swallowed instead of
     * propagated. A crash on the main/UI thread still propagates normally,
     * since that's a real, user-visible problem worth surfacing as such.
     */
    public static synchronized void reassert(Context appContext) {
        Context ctx = appContext.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        write(ctx, "reasserting crash handler (previous: "
                + (previous == null ? "null" : previous.getClass().getName()) + ")");
        Thread mainThread = ctx.getMainLooper().getThread();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                append(ctx, thread, ex);
            } catch (Throwable ignored) {}
            if (thread == mainThread) {
                if (previous != null) previous.uncaughtException(thread, ex);
            } else {
                write(ctx, "SUPPRESSED (background thread, not fatal): " + thread.getName()
                        + " - " + ex.getClass().getName() + ": " + ex.getMessage());
            }
        });
    }

    public static String read(Context c) {
        File f = new File(c.getFilesDir(), FILE_NAME);
        if (!f.exists()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) >= 0) off += n;
            return new String(data, "UTF-8");
        } catch (Throwable t) {
            return "(could not read crash log: " + t + ")";
        }
    }

    public static void clear(Context c) {
        new File(c.getFilesDir(), FILE_NAME).delete();
    }
}
