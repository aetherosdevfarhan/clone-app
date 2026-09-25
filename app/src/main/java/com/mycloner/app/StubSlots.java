package com.mycloner.app;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fixed pool of stub Activity slots (StubActivity1..StubActivity5,
 * declared in the manifest as distinct components with their own task
 * affinity). That's what lets several clones stay open side by side in
 * Recents instead of collapsing into one task. SLOT_COUNT is how many
 * clones can be actively running (resumed or backgrounded) at once -
 * you can still keep more than that added in your library, you just
 * can't have more than SLOT_COUNT of them open simultaneously.
 */
public class StubSlots {
    public static final int SLOT_COUNT = 6;

    private static final Map<String, Integer> assigned = new LinkedHashMap<>();

    public static synchronized ComponentName componentFor(Context ctx, String cloneId) {
        Integer idx = assigned.get(cloneId);
        if (idx != null) return alias(ctx, idx);

        idx = nextFreeIndex();
        if (idx == -1) {
            String oldest = assigned.keySet().iterator().next();
            idx = assigned.remove(oldest);
            finishTaskFor(ctx, alias(ctx, idx));
        }
        assigned.put(cloneId, idx);
        return alias(ctx, idx);
    }

    public static synchronized boolean isRunning(String cloneId) {
        return assigned.containsKey(cloneId);
    }

    public static synchronized void release(Context ctx, String cloneId) {
        Integer idx = assigned.remove(cloneId);
        if (idx != null) finishTaskFor(ctx, alias(ctx, idx));
    }

    public static boolean isStubClassName(String className) {
        if (className == null) return false;
        for (int i = 1; i <= SLOT_COUNT; i++) {
            if (("com.mycloner.app.StubActivity" + i).equals(className)) return true;
        }
        return false;
    }

    private static int nextFreeIndex() {
        boolean[] used = new boolean[SLOT_COUNT];
        for (int v : assigned.values()) used[v] = true;
        for (int i = 0; i < SLOT_COUNT; i++) if (!used[i]) return i;
        return -1;
    }

    private static ComponentName alias(Context ctx, int idx) {
        return new ComponentName(ctx, "com.mycloner.app.StubActivity" + (idx + 1));
    }

    private static void finishTaskFor(Context ctx, ComponentName comp) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            for (ActivityManager.AppTask t : am.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = t.getTaskInfo();
                if (info != null && comp.equals(info.baseActivity)) {
                    t.finishAndRemoveTask();
                }
            }
        } catch (Throwable ignored) { }
    }
}
