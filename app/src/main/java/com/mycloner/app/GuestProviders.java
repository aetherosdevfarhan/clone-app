package com.mycloner.app;

import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;

/**
 * Instantiates and runs onCreate() for every <provider> the guest package
 * declares, mirroring real process startup order: on a normal launch,
 * Android runs every ContentProvider's onCreate() BEFORE Application.onCreate().
 * This matters because a lot of modern SDK auto-init (androidx App Startup's
 * InitializationProvider, Firebase, WorkManager, Facebook SDK, etc.) piggybacks
 * on a provider's onCreate() specifically because it's guaranteed to run first.
 * Skipping this step (as earlier versions of this loader did) means
 * Application.onCreate() runs against a half-initialized app - code that
 * expects an initializer to have already completed can end up waiting on
 * something that will never happen, which shows up as a silent hang rather
 * than a crash.
 *
 * This only runs onCreate() locally, in-process - it does NOT register these
 * providers with the system ContentResolver, so cross-process or
 * ContentResolver.query() access to them from elsewhere still won't work.
 */
public class GuestProviders {
    public static void initAll(Context host, Context guestContext, ClassLoader loader, String pkg, String cloneId) {
        try {
            android.content.pm.PackageInfo pi = host.getPackageManager()
                    .getPackageInfo(pkg, PackageManager.GET_PROVIDERS);
            ProviderInfo[] providers = pi.providers;
            if (providers == null || providers.length == 0) {
                CrashLog.checkpoint(host, "clone " + cloneId + ": no <provider> components declared");
                return;
            }
            for (ProviderInfo info : providers) {
                CrashLog.checkpoint(host, "clone " + cloneId + ": provider " + info.name
                        + " (" + info.authority + ") - onCreate start");
                try {
                    Class<?> c = Class.forName(info.name, false, loader);
                    ContentProvider cp = (ContentProvider) c.newInstance();
                    cp.attachInfo(guestContext, info);
                    CrashLog.checkpoint(host, "clone " + cloneId + ": provider " + info.name + " - onCreate done");
                } catch (Throwable t) {
                    CrashLog.logCaught(host, "provider init failed: " + info.name, t);
                }
            }
        } catch (Throwable t) {
            CrashLog.logCaught(host, "could not list providers for " + pkg, t);
        }
    }
}
