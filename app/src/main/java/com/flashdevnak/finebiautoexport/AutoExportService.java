package com.flashdevnak.finebiautoexport;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.WebView;

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoExportService extends Service {
    public static final String ACTION_START = "com.flashdevnak.finebiautoexport.START";
    public static final String ACTION_STOP = "com.flashdevnak.finebiautoexport.STOP";

    private static final int NOTIFICATION_ID = 21029;
    private static final String CHANNEL_ID = "finebi_auto_export";
    private static final Pattern TIME_PATTERN =
            Pattern.compile("20\\d{2}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

    private HandlerThread workerThread;
    private Handler worker;
    private Handler main;
    private FineBiConfig config;
    private WebView bootstrapWebView;
    private final AtomicBoolean monitorScheduled = new AtomicBoolean(false);
    private volatile boolean stopping;
    private volatile int consecutiveErrors;
    private volatile long lastFlashlinkAttemptAt;

    @Override
    public void onCreate() {
        super.onCreate();
        main = new Handler(Looper.getMainLooper());
        workerThread = new HandlerThread("FineBI-AutoExport");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("กำลังเริ่มระบบ", false));

        try {
            config = FineBiConfig.load(this);
        } catch (Exception e) {
            Prefs.setError(this, e.toString());
            Prefs.setStatus(this, "ERROR", "โหลด FineBI template ไม่ได้");
            updateNotification("Template error", true);
            stopSelf();
            return;
        }

        Prefs.get(this).edit().putBoolean(Prefs.ENABLED, true).apply();
        Prefs.setStatus(this, "STARTING", "กำลังเตรียม FineBI session");

        if (SessionStore.isReady()) {
            scheduleMonitor(500);
        } else {
            bootstrapSession();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopping = true;
            Prefs.get(this).edit().putBoolean(Prefs.ENABLED, false).apply();
            Prefs.setStatus(this, "STOPPED", "หยุดโดยผู้ใช้");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!stopping && config != null) {
            if (SessionStore.isReady()) {
                scheduleMonitor(250);
            } else {
                bootstrapSession();
            }
        }
        return START_STICKY;
    }

    private void bootstrapSession() {
        main.post(() -> {
            if (stopping || bootstrapWebView != null || SessionStore.isReady()) {
                if (SessionStore.isReady()) scheduleMonitor(250);
                return;
            }

            Prefs.setStatus(this, "SESSION", "กำลังเปิด FineBI เพื่อจับ session");
            updateNotification("กำลังจับ FineBI session", false);

            bootstrapWebView = WebViewFactory.create(
                    this,
                    new FineBiWebViewClient.Listener() {
                        @Override public void onPageStarted(String url) {}

                        @Override
                        public void onPageFinished(String url) {
                            if (!SessionStore.isReady()) {
                                Prefs.setStatus(
                                        AutoExportService.this,
                                        "LOGIN_OR_VPN",
                                        "รอ FineBI login หรือ Flashlink"
                                );
                            }
                        }

                        @Override
                        public void onSessionCaptured() {
                            Prefs.setStatus(
                                    AutoExportService.this,
                                    "RUNNING",
                                    "FineBI session พร้อม"
                            );
                            updateNotification("FineBI session พร้อม", false);
                            scheduleMonitor(250);

                            main.postDelayed(() -> {
                                if (bootstrapWebView != null) {
                                    bootstrapWebView.stopLoading();
                                    bootstrapWebView.loadUrl("about:blank");
                                }
                            }, 1200);
                        }

                        @Override
                        public void onMainFrameError(String description) {
                            Prefs.setStatus(
                                    AutoExportService.this,
                                    "VPN_REQUIRED",
                                    "FineBI เข้าไม่ได้: ตรวจ Flashlink"
                            );
                            updateNotification("FineBI เข้าไม่ได้ • แตะเพื่อเปิดแอป", true);
                            tryOpenFlashlinkRateLimited();
                        }
                    }
            );
            bootstrapWebView.loadUrl(FineBiConfig.ENTRY_URL);
        });
    }

    private void scheduleMonitor(long delayMs) {
        if (stopping) return;
        if (monitorScheduled.compareAndSet(false, true)) {
            worker.postDelayed(() -> {
                monitorScheduled.set(false);
                monitorOnce();
            }, delayMs);
        }
    }

    private void monitorOnce() {
        if (stopping) return;

        if (!SessionStore.isReady()) {
            Prefs.setStatus(this, "SESSION", "รอ FineBI session");
            bootstrapSession();
            scheduleMonitor(5000);
            return;
        }

        try {
            JsonUtils.NormalizeResult normalized = JsonUtils.normalizeFineBiBody(
                    config.updateBody,
                    SessionStore.sessionId(),
                    true
            );

            FineBiApi.Result result = FineBiApi.post(
                    config.updateUrl,
                    normalized.body,
                    20_000
            );

            if (result.code == 401 || result.code == 403) {
                SessionStore.clear();
                consecutiveErrors = 0;
                Prefs.setStatus(this, "SESSION_EXPIRED", "FineBI session หมดอายุ");
                updateNotification("Session หมดอายุ • กำลังจับใหม่", true);
                bootstrapSession();
                scheduleMonitor(5000);
                return;
            }

            if (!result.ok()) {
                throw new IllegalStateException("FineBI HTTP " + result.code);
            }

            Matcher m = TIME_PATTERN.matcher(result.text());
            if (!m.find()) {
                throw new IllegalStateException("ไม่พบ th_update_time ใน response");
            }

            String update = m.group();
            consecutiveErrors = 0;
            Prefs.setBackendUpdate(this, update);
            Prefs.setStatus(this, "RUNNING", "กำลังเฝ้ารอบข้อมูล " + update);
            updateNotification("Backend " + update + " • Auto Export ACTIVE", false);

            SharedPreferences prefs = Prefs.get(this);
            String lastExported = prefs.getString(Prefs.LAST_EXPORTED, "");

            if (!update.equals(lastExported)) {
                exportVersion(update);
            }

            scheduleMonitor(nextPollDelayMs());
        } catch (Exception e) {
            consecutiveErrors++;
            Prefs.setError(this, e.getClass().getSimpleName() + ": " + e.getMessage());

            if (consecutiveErrors >= 3) {
                Prefs.setStatus(this, "VPN_OR_NETWORK", "FineBI ไม่ตอบ • ตรวจ Flashlink");
                updateNotification("FineBI ไม่ตอบ • แตะเพื่อเปิดแอป", true);
                tryOpenFlashlinkRateLimited();
                SessionStore.clear();
                bootstrapSession();
            } else {
                Prefs.setStatus(this, "RETRYING", "FineBI error • กำลังลองใหม่");
                updateNotification("FineBI error • กำลังลองใหม่", true);
            }

            scheduleMonitor(consecutiveErrors >= 3 ? 15_000 : 8_000);
        }
    }

    private void exportVersion(String update) throws Exception {
        String safe = update.replace(":", "-").replace(" ", "_");
        String displayName = "HUB_departure_monitor_" + safe + ".xlsx";

        if (ExportStore.exists(this, displayName)) {
            Prefs.setExport(this, update, "", displayName);
            updateNotification("มีไฟล์รอบ " + update + " อยู่แล้ว", false);
            return;
        }

        Prefs.setStatus(this, "EXPORTING", "กำลัง Export " + update);
        updateNotification("กำลัง Export " + update + " • HUB=ALL", false);

        JsonUtils.NormalizeResult normalized = JsonUtils.normalizeFineBiBody(
                config.exportBody,
                SessionStore.sessionId(),
                true
        );

        if (normalized.forceAllCount < 1) {
            throw new IllegalStateException(
                    "ไม่พบ HUB filter ใน export payload — BLOCK เพื่อกันไฟล์ที่ไม่ใช่ Select All"
            );
        }

        FineBiApi.Result result = FineBiApi.post(
                config.exportUrl,
                normalized.body,
                60_000
        );

        if (!result.ok()) {
            throw new IllegalStateException("Export HTTP " + result.code);
        }
        if (result.bytes.length < 4
                || result.bytes[0] != 0x50
                || result.bytes[1] != 0x4B) {
            throw new IllegalStateException("Export response ไม่ใช่ XLSX");
        }

        ExportStore.Saved saved = ExportStore.saveValidated(
                this,
                result.bytes,
                update
        );

        Prefs.setExport(
                this,
                update,
                saved.uri == null ? "" : saved.uri.toString(),
                saved.displayName
        );
        Prefs.setStatus(this, "RUNNING", "Export สำเร็จ " + update);
        updateNotification("Export สำเร็จ " + update + " • HUB=ALL", false);
    }

    private long nextPollDelayMs() {
        Calendar c = Calendar.getInstance();
        int minute = c.get(Calendar.MINUTE);
        boolean fast = (minute >= 25 && minute <= 40)
                || minute >= 55
                || minute <= 10;
        return fast ? 10_000L : 60_000L;
    }

    private void tryOpenFlashlinkRateLimited() {
        long now = System.currentTimeMillis();
        if (now - lastFlashlinkAttemptAt < 120_000L) return;
        lastFlashlinkAttemptAt = now;

        main.post(() -> FlashlinkHelper.open(this));
    }

    private Notification buildNotification(String text, boolean needsAttention) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(
                this, 1, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stop = new Intent(this, AutoExportService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setContentTitle("FineBI Auto Export")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(true)
                .setContentIntent(content)
                .setOnlyAlertOnce(!needsAttention)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "หยุด",
                        stopPi
                ).build());

        return b.build();
    }

    private void updateNotification(String text, boolean attention) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text, attention));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FineBI Auto Export",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("สถานะการตรวจ FineBI และ Auto Export");
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        stopping = true;
        if (workerThread != null) workerThread.quitSafely();
        main.post(() -> {
            if (bootstrapWebView != null) {
                bootstrapWebView.destroy();
                bootstrapWebView = null;
            }
        });
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
