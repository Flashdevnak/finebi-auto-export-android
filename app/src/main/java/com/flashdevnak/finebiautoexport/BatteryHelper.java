package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Battery policy helper.
 *
 * Battery Unrestricted is an optimization for unattended/background reliability,
 * not an authentication or functional requirement. Once the user has configured
 * or explicitly acknowledged the battery setting, remember that choice across
 * normal app updates and do not keep forcing the settings screen.
 */
public final class BatteryHelper {
    private static final String PREFS = "finebi_battery_policy";
    private static final String KEY_ACKNOWLEDGED = "configured_once";
    private static final String KEY_LAST_OS_UNRESTRICTED = "last_os_unrestricted";
    private static final String KEY_LAST_CHECK_AT = "last_check_at";

    private BatteryHelper() {}

    /**
     * UI/background readiness result.
     *
     * True means either Android currently exempts the app from battery
     * optimization OR the user has already dealt with this optional setting once.
     * This intentionally prevents repeated battery setup prompts on OEM devices
     * whose battery APIs do not always mirror the UI wording exactly.
     */
    public static boolean isUnrestricted(Context context) {
        if (Build.VERSION.SDK_INT < 23) return true;

        SharedPreferences prefs = prefs(context);
        migrateExistingInstall(context, prefs);

        boolean osUnrestricted = isActuallyUnrestricted(context);
        prefs.edit()
                .putBoolean(KEY_LAST_OS_UNRESTRICTED, osUnrestricted)
                .putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis())
                .apply();

        if (osUnrestricted) {
            prefs.edit().putBoolean(KEY_ACKNOWLEDGED, true).apply();
            return true;
        }

        // Battery Unrestricted is advisory. Once configured/acknowledged, do not
        // block Daily Readiness or repeatedly send the user back to Settings.
        return prefs.getBoolean(KEY_ACKNOWLEDGED, false);
    }

    /** Raw Android battery-optimization state, kept separate from UI readiness. */
    public static boolean isActuallyUnrestricted(Context context) {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean wasConfiguredOnce(Context context) {
        SharedPreferences prefs = prefs(context);
        migrateExistingInstall(context, prefs);
        return prefs.getBoolean(KEY_ACKNOWLEDGED, false);
    }

    /**
     * Useful for a non-blocking warning in future UI: the user configured it once,
     * but Android currently reports that the exemption is not active.
     */
    public static boolean needsReview(Context context) {
        return Build.VERSION.SDK_INT >= 23
                && wasConfiguredOnce(context)
                && !isActuallyUnrestricted(context);
    }

    public static long lastCheckedAt(Context context) {
        return prefs(context).getLong(KEY_LAST_CHECK_AT, 0L);
    }

    public static void requestUnrestricted(Activity activity) {
        // The user explicitly chose to configure this optional optimization.
        // Remember the choice immediately so Cancel/OEM-specific battery screens
        // do not cause an endless setup loop on the next app resume.
        prefs(activity).edit()
                .putBoolean(KEY_ACKNOWLEDGED, true)
                .putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis())
                .apply();

        if (Build.VERSION.SDK_INT < 23 || isActuallyUnrestricted(activity)) {
            return;
        }

        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(i);
            return;
        } catch (Exception ignored) {
            // Some OEMs do not expose the direct exemption dialog. Fall through
            // to the app settings page, but still keep the user's acknowledgement.
        }

        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(i);
        } catch (Exception ignored) {}
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * v0.7.2 migration: existing working installs have already been through the
     * setup flow in earlier versions, so do not make them configure Battery again.
     */
    private static void migrateExistingInstall(Context context, SharedPreferences prefs) {
        if (prefs.contains(KEY_ACKNOWLEDGED)) return;

        boolean existingSetup = false;
        try {
            existingSetup = Prefs.get(context).getBoolean(Prefs.ENABLED, false)
                    || TemplateStore.isReady(context);
        } catch (Exception ignored) {}

        if (existingSetup) {
            prefs.edit().putBoolean(KEY_ACKNOWLEDGED, true).apply();
        }
    }
}
