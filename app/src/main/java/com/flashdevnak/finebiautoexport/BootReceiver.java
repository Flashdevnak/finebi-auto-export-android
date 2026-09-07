package com.flashdevnak.finebiautoexport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Restarts the foreground monitor after reboot, user unlock, or an in-place APK update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = Prefs.get(context).getBoolean(Prefs.ENABLED, false);
        if (!enabled) return;

        Intent service = new Intent(context, AutoExportService.class)
                .setAction(AutoExportService.ACTION_START);

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Exception e) {
            Prefs.setStatus(
                    context,
                    "RESTART_PENDING",
                    "ระบบจะกลับมาทำงานเมื่อ Android อนุญาตให้เริ่มเบื้องหลัง"
            );
            Prefs.setError(context, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
