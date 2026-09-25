package com.mycloner.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CloneStore {
    private static final String PREF = "clones";
    private static final String KEY = "entries";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<CloneEntry> all(Context c) {
        List<CloneEntry> out = new ArrayList<>();
        String raw = sp(c).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new CloneEntry(o.getString("id"), o.getString("pkg"),
                        o.optInt("slot", 1), o.getString("label")));
            }
        } catch (Exception ignored) {
            // corrupt prefs value; treat as empty rather than crash
        }
        return out;
    }

    private static void saveAll(Context c, List<CloneEntry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (CloneEntry e : list) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("pkg", e.pkg);
                o.put("slot", e.slot);
                o.put("label", e.label);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        sp(c).edit().putString(KEY, arr.toString()).apply();
    }

    /** How many clones of this package already exist. */
    public static int countFor(Context c, String pkg) {
        int n = 0;
        for (CloneEntry e : all(c)) if (e.pkg.equals(pkg)) n++;
        return n;
    }

    /** Adds a brand new clone slot for pkg (does not touch existing ones) and returns it. */
    public static CloneEntry add(Context c, String pkg, String baseLabel) {
        List<CloneEntry> list = all(c);
        int slot = countFor(c, pkg) + 1;
        String label = slot == 1 ? baseLabel : (baseLabel + " " + slot);
        CloneEntry e = new CloneEntry(UUID.randomUUID().toString(), pkg, slot, label);
        list.add(e);
        saveAll(c, list);
        return e;
    }

    public static void remove(Context c, String cloneId) {
        List<CloneEntry> list = all(c);
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(cloneId)) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) return;
        saveAll(c, list);
        LaunchRegistry.unregister(cloneId);
        StubSlots.release(c, cloneId);
        // Matches the root CloneContext computes: <filesDir>/clones/<cloneId>
        deleteTree(new java.io.File(c.getFilesDir(), "clones/" + cloneId));
    }

    private static void deleteTree(java.io.File f) {
        if (f == null || !f.exists()) return;
        java.io.File[] children = f.listFiles();
        if (children != null) {
            for (java.io.File child : children) deleteTree(child);
        }
        // Best effort: an in-use cache/db file can be left behind safely.
        f.delete();
    }

    public static CloneEntry byId(Context c, String cloneId) {
        for (CloneEntry e : all(c)) if (e.id.equals(cloneId)) return e;
        return null;
    }
}
