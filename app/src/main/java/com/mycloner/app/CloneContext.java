package com.mycloner.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

/**
 * Wraps a guest app's Context so file / database / SharedPreferences calls
 * land in a private folder under the HOST app's own storage, instead of the
 * guest package's real data directory.
 *
 * Why this is required, not optional: the host process runs under the
 * host app's UID, not the guest's. Even with CONTEXT_IGNORE_SECURITY on
 * createPackageContext(), the guest's real /data/.../<guest-pkg>/ directory
 * is owned by a different UID at the Linux filesystem level, so any guest
 * code that touches storage the normal way would hit a SecurityException
 * or silent failure. Redirecting to a directory the host owns is what
 * makes guest code able to persist anything at all, and giving each clone
 * its own subfolder (keyed by cloneId) is what keeps clone 1 and clone 2
 * of the same app from overwriting each other's data.
 *
 * Known limits (not solved by this class):
 *  - Anything the guest reads via ApplicationInfo.dataDir directly
 *    (bypassing Context methods) is not redirected.
 *  - ContentProviders, background Services, account/notification state
 *    kept in system services are untouched by this wrapper.
 */
public class CloneContext extends ContextWrapper {
    private final Context host;
    private final String cloneId;
    private final File root;

    public CloneContext(Context guestContext, Context hostContext, String cloneId) {
        super(guestContext);
        this.host = hostContext.getApplicationContext();
        this.cloneId = cloneId;
        this.root = new File(host.getFilesDir(), "clones/" + cloneId);
    }

    private File dir(String sub) {
        File f = new File(root, sub);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    @Override public File getFilesDir() { return dir("files"); }
    @Override public File getCacheDir() { return dir("cache"); }
    @Override public File getCodeCacheDir() { return dir("code_cache"); }
    @Override public File getNoBackupFilesDir() { return dir("no_backup"); }
    @Override public File getObbDir() { return dir("obb"); }

    @Override public File getExternalFilesDir(String type) {
        return dir("external_files" + (type != null ? "/" + type : ""));
    }

    @Override public File getExternalCacheDir() { return dir("external_cache"); }

    @Override public File getDir(String name, int mode) { return dir("dirs/" + name); }

    @Override public File getDatabasePath(String name) {
        return new File(dir("databases"), name);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory,
                                                DatabaseErrorHandler errorHandler) {
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(), factory, errorHandler);
    }

    @Override public boolean deleteDatabase(String name) {
        return getDatabasePath(name).delete();
    }

    @Override public String[] databaseList() {
        String[] l = dir("databases").list();
        return l != null ? l : new String[0];
    }

    @Override public SharedPreferences getSharedPreferences(String name, int mode) {
        return host.getSharedPreferences(prefsName(name), mode);
    }

    @Override public boolean deleteSharedPreferences(String name) {
        return host.deleteSharedPreferences(prefsName(name));
    }

    private String prefsName(String name) {
        return "clone_" + cloneId + "_" + name;
    }
}
