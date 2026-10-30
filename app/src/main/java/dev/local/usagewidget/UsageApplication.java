package dev.local.usagewidget;

import android.app.Application;

public final class UsageApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        UiText.initialize(this);
    }
}
