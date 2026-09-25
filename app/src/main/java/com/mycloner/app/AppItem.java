package com.mycloner.app;

import android.graphics.drawable.Drawable;

public class AppItem {
    public final String pkg, label, apkPath;
    public final Drawable icon;

    /** Set when this row represents an existing clone (MainActivity list). Null = installable app (Picker). */
    public String cloneId;
    /** How many clones of this package already exist. Used by the Picker to show a badge. */
    public int cloneCount = 0;

    public AppItem(String pkg, String label, Drawable icon, String apkPath) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
        this.apkPath = apkPath;
    }
}
