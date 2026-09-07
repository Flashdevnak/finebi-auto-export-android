package com.flashdevnak.finebiautoexport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = Prefs.get(context).getBoolean(Prefs.ENABLED, false);
        if (!enabled) return;

        Intent service = new Intent(context, AutoExportService.class)
                .setAction(AutoExportService.ACTION_START);

        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }
}
