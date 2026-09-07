package com.flashdevnak.finebiautoexport;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/** Receives PackageInstaller callbacks for in-app updates. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "finebi_update_install";
    private static final int NOTIFICATION_ID = 21031;

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
        );

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirm);
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            notify(context, "อัปเดตสำเร็จ", "FineBI Auto Export อัปเดตเรียบร้อยแล้ว");
            return;
        }

        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        notify(
                context,
                "อัปเดตไม่สำเร็จ",
                message == null || message.isEmpty() ? "กรุณาเปิดแอปแล้วลองอัปเดตอีกครั้ง" : message
        );
    }

    private static void notify(Context context, String title, String text) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "FineBI Update Install",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            nm.createNotificationChannel(channel);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);
        nm.notify(NOTIFICATION_ID, b.build());
    }
}
