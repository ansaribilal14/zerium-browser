package com.zerium.browser;

import android.app.Application;
import android.content.Context;
import com.google.android.material.color.DynamicColors;

public class ZeriumApp extends Application {

    private static volatile Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

    /** Application-wide context for non-Activity components. */
    public static Context appContext() {
        return appContext;
    }
}
