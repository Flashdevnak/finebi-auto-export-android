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
    private static final long RETURN_TIMEOUT_MS = 2 * 60_000L;

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
     * one normal app press another app's Connect button. When recovery itself
     * opened Flashlink while our UI was visible, wait until FineBI is healthy
     * again and then return the user to FineBI Auto Export automatically.
     */
    public static boolean open(Context context) {
        if (!isInstalled(context)) return false;

        final Context app = context.getApplicationContext();
        final boolean serviceRecovery = !(context instanceof Activity);
        final boolean returnAfterRecovery = serviceRecovery && AppVisibility.isDailyVisible();
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

        if (opened && returnAfterRecovery) {
            scheduleReturnWhenRecovered(app, openedAt);
        }
        return opened;
    }

    private static void scheduleReturnWhenRecovered(Context app, long openedAt) {
        final long deadline = openedAt + RETURN_TIMEOUT_MS;
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
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

    private static boolean isRecoveredSince(Context app, long openedAt) {
        try {
            SharedPreferences p = Prefs.get(app);
            long lastPollOk = p.getLong(Prefs.LAST_POLL_OK_AT, 0L);
            if (lastPollOk > openedAt) return true;

            String state = p.getString(Prefs.SERVICE_STATE, "");
            boolean healthyState = "RUNNING".equals(state)
                    || "WAITING".equals(state)
                    || "EXPORTING".equals(state);
            return SessionStore.isReady() && healthyState;
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
            }, 350L);
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