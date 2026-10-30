package dev.local.usagewidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())
                && !Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) return;
        RefreshJob.schedule(context);
        UsageWidget.updateAll(context);
    }
}
