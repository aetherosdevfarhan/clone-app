package com.mycloner.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.*;

public class AppLoader {
    public static List<AppItem> launchable(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, AppItem> map = new LinkedHashMap<>();
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            ApplicationInfo ai = r.activityInfo.applicationInfo;
            if (ai.packageName.equals(c.getPackageName()) || map.containsKey(ai.packageName)) continue;
            map.put(ai.packageName, new AppItem(ai.packageName,
                    String.valueOf(r.loadLabel(pm)), r.loadIcon(pm), ai.sourceDir));
        }
        List<AppItem> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static AppItem byPackage(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return new AppItem(pkg, String.valueOf(pm.getApplicationLabel(ai)),
                    pm.getApplicationIcon(ai), ai.sourceDir);
        } catch (PackageManager.NameNotFoundException e) {
            return null; // app was uninstalled
        } catch (Throwable e) {
            return null; // e.g. icon resources too large/corrupt to decode
        }
    }
}
