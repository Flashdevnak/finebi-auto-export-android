package com.flashdevnak.finebiautoexport;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

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

    public static boolean open(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(FineBiConfig.FLASHLINK_PACKAGE);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(launch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
