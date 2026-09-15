package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.List;

public final class FlashlinkHelper {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long RETURN_MIN_DELAY_MS = 1_500L;
    private static final long RETURN_POLL_MS = 1_000L;
    private static final long RETURN_TIMEOUT_MS = 5 * 60_000L;
    private static volatile long returnRequestId;

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
     * Best-effort wake of the authorized Flashlink client.
     *
     * Important: when AutoExportService opens Flashlink as part of recovery, the
     * recovery flow owns that temporary navigation. Flashlink may auto-connect by
     * itself, so there may be no user tap to trigger a lifecycle transition back
     * to this app. We therefore wait for a NEW successful FineBI API response
     * after Flashlink was opened, then bring FineBI Auto Export back to front.
     *
     * Manual Flashlink opens from an Activity do not auto-return, because those
     * were initiated by the user rather than by unattended recovery.
     */
    public static boolean open(Context context) {
        if (!isInstalled(context)) return false;

        final Context app = context.getApplicationContext();
        final boolean serviceRecovery = !(context instanceof Activity);
        final long openedAt = System.currentTimeMillis();

        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(FineBiConfig.FLASHLINK_PACKAGE);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        boolean opened = false;
        try {
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    6201,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            pi.send();
            opened = true;
        } catch (Exception ignored) {
            try {
                context.startActivity(launch);
                opened = true;
            } catch (Exception ignoredAgain) {
                opened = false;
            }
        }

        if (opened && serviceRecovery) {
            scheduleReturnWhenRecovered(app, openedAt);
        }
        return opened;
    }

    private static void scheduleReturnWhenRecovered(Context app, long openedAt) {
        final long requestId = ++returnRequestId;
        final long deadline = openedAt + RETURN_TIMEOUT_MS;

        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                if (requestId != returnRequestId) return;

                long now = System.currentTimeMillis();
                if (isRecoveredSince(app, openedAt)) {
                    bringOwnAppToFront(app);
                    return;
                }

                if (now < deadline) {
                    MAIN.postDelayed(this, RETURN_POLL_MS);
                }
            }
        }, RETURN_MIN_DELAY_MS);
    }

    /**
     * A fresh successful API poll is the recovery proof. Do not use the old
     * in-memory session alone because it may still look READY while Flashlink is
     * reconnecting, which could return to the app too early.
     */
    private static boolean isRecoveredSince(Context app, long openedAt) {
        try {
            SharedPreferences p = Prefs.get(app);
            long lastPollOk = p.getLong(Prefs.LAST_POLL_OK_AT, 0L);
            return lastPollOk >= openedAt;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void bringOwnAppToFront(Context app) {
        boolean moved = false;
        try {
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.AppTask> tasks = am.getAppTasks();
                if (tasks != null && !tasks.isEmpty()) {
                    tasks.get(0).moveToFront();
                    moved = true;
                }
            }
        } catch (Throwable ignored) {
            moved = false;
        }

        if (moved) {
            MAIN.postDelayed(() -> {
                if (!AppVisibility.isDailyVisible()) {
                    sendReturnPendingIntent(app);
                }
            }, 500L);
        } else {
            sendReturnPendingIntent(app);
        }
    }

    private static void sendReturnPendingIntent(Context app) {
        try {
            Intent i = new Intent(app, DailyMainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    app,
                    6202,
                    i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                );
                pi.send(app, 0, null, null, null, null, options.toBundle());
            } else {
                pi.send();
            }
        } catch (Throwable ignored) {
            try {
                Intent i = new Intent(app, DailyMainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                app.startActivity(i);
            } catch (Throwable ignoredAgain) {}
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
