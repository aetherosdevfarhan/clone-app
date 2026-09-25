package com.mycloner.app;

import android.app.Application;

public class CloneApplication extends Application {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
        HiddenApi.exempt();
        CrashLog.install(base);
    }
}
