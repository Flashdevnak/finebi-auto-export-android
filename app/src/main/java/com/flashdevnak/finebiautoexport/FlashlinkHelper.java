package com.flashdevnak.finebiautoexport;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

public final class FlashlinkHelper {
    private FlashlinkHelper() {}

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(FineBiConfig.FLASHLINK_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Best-effort wake of the authorized Flashlink client. Android does not let
     * one normal app press another app's Connect button, but waking Flashlink is
     * enough when its own auto-connect/session policy is enabled.
     */
    public static boolean open(Context context) {
        if (!isInstalled(context)) return false;
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(FineBiConfig.FLASHLINK_PACKAGE);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    6201,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            pi.send();
            return true;
        } catch (Exception ignored) {
            try {
                context.startActivity(launch);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** Opens Android VPN settings for one-time Always-on VPN configuration. */
    public static boolean openVpnSettings(Context context) {
        try {
            Intent i = new Intent(Settings.ACTION_VPN_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
