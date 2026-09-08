package com.flashdevnak.finebiautoexport;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                showInstallConfirmationNotification(context, confirm);
                try {
                    context.startActivity(confirm);
                } catch (Exception ignored) {
                    // Android may block a background activity launch on some builds.
                    // The notification above remains as a guaranteed user-visible fallback.
                }
            } else {
                notify(context, "รอยืนยันการติดตั้ง",
                        "เปิด FineBI Auto Export แล้วกดอัปเดตอีกครั้ง");
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

    private static void showInstallConfirmationNotification(Context context, Intent confirm) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannel(nm);

        PendingIntent pi = PendingIntent.getActivity(
                context,
                21032,
                confirm,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31
                        ? PendingIntent.FLAG_MUTABLE
                        : 0)
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("พร้อมติดตั้ง FineBI Auto Export")
                .setContentText("แตะเพื่อยืนยันการอัปเดต")
                .setAutoCancel(true)
                .setContentIntent(pi);
        nm.notify(NOTIFICATION_ID, b.build());
    }

    private static void notify(Context context, String title, String text) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannel(nm);

        Intent open = new Intent(context, DailyMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context,
                21031,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi);
        nm.notify(NOTIFICATION_ID, b.build());
    }

    private static void ensureChannel(NotificationManager nm) {
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "FineBI Update Install",
                    NotificationManager.IMPORTANCE_HIGH
            );
            nm.createNotificationChannel(channel);
        }
    }
}
