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
        final Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                append(ctx, thread, ex);
            } catch (Throwable ignored) {
            }
            if (original != null) original.uncaughtException(thread, ex);
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
