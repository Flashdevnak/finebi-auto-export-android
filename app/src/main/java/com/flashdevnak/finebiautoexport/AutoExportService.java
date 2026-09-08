package com.flashdevnak.finebiautoexport;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.WebView;

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
    private WebView bootstrapWebView;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile boolean stopping;
    private volatile int consecutiveErrors;
    private volatile long lastFlashlinkAttemptAt;
    private volatile String lastNotificationText = "";

    private final Runnable monitorRunnable = new Runnable() {
        @Override public void run() {
            if (stopping) return;
            Prefs.touchServiceHeartbeat(AutoExportService.this);
            try {
                monitorOnce();
            } catch (Throwable t) {
                consecutiveErrors++;
                Prefs.setConsecutiveErrors(AutoExportService.this, consecutiveErrors);
                Prefs.setError(
                        AutoExportService.this,
                        t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage())
                );
                Prefs.setStatus(
                        AutoExportService.this,
                        "RECOVERING",
                        "ระบบเจอข้อผิดพลาดภายใน • กำลังกู้คืนอัตโนมัติ"
                );
                updateNotification("กำลังกู้คืน Auto Export อัตโนมัติ", true);
                destroyBootstrapWebView();
                scheduleMonitor(Math.min(2 * 60_000L, Math.max(15_000L, errorRetryDelayMs())));
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        main = new Handler(Looper.getMainLooper());
        workerThread = new HandlerThread("FineBI-AutoExport");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("กำลังเริ่มระบบ", false));
        registerNetworkWatcher();

        Prefs.get(this).edit()
                .putBoolean(Prefs.ENABLED, true)
                .putBoolean(Prefs.SMART_BATTERY, true)
                .apply();
        Prefs.markServiceStarted(this);

        UpdateManager.maybeCheckAndNotify(this);
        MailManager.kick(this);

        if (!NetworkHelper.isOnline(this)) {
            enterOfflineState();
        } else if (SessionStore.isReady()) {
            Prefs.setStatus(this, "STARTING", "กำลังตรวจ FineBI");
            scheduleMonitor(500L);
        } else {
            Prefs.setStatus(this, "STARTING", "กำลังเตรียม FineBI session");
            bootstrapSession();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Prefs.touchServiceHeartbeat(this);

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopping = true;
            cancelMonitor();
            Prefs.get(this).edit().putBoolean(Prefs.ENABLED, false).apply();
            Prefs.setStatus(this, "STOPPED", "หยุดโดยผู้ใช้");
            Prefs.setPollPlan(this, "STOPPED", 0L, 0L, false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!stopping) {
            if (!NetworkHelper.isOnline(this)) {
                enterOfflineState();
            } else if (SessionStore.isReady()) {
                scheduleMonitor(250L);
            } else {
                bootstrapSession();
            }
        }
        return START_STICKY;
    }

    private void registerNetworkWatcher() {
        try {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) return;

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    if (stopping || worker == null) return;
                    worker.post(() -> {
                        if (stopping) return;
                        Prefs.touchServiceHeartbeat(AutoExportService.this);
                        Prefs.setStatus(
                                AutoExportService.this,
                                "NETWORK_BACK",
                                "เครือข่ายกลับมา • ตรวจ FineBI ทันที"
                        );
                        MailManager.kick(AutoExportService.this);
                        UpdateManager.maybeCheckAndNotify(AutoExportService.this);
                        if (!SessionStore.isReady()) {
                            bootstrapSession();
                        }
                        scheduleMonitor(250L);
                    });
                }

                @Override public void onLost(Network network) {
                    if (stopping || worker == null) return;
                    worker.postDelayed(() -> {
                        if (!NetworkHelper.isOnline(AutoExportService.this)) {
                            cancelMonitor();
                            enterOfflineState();
                        }
                    }, 750L);
                }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception ignored) {
            networkCallback = null;
        }
    }

    private void unregisterNetworkWatcher() {
        try {
            if (connectivityManager != null && networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception ignored) {}
        networkCallback = null;
    }

    private void enterOfflineState() {
        cancelMonitor();
        Prefs.touchServiceHeartbeat(this);
        Prefs.setStatus(
                this,
                "OFFLINE",
                "Smart Battery • OFFLINE • หยุด polling จนกว่าเครือข่ายจะกลับมา"
        );
        Prefs.setPollPlan(this, "OFFLINE", 0L, 0L, false);
        updateNotification("ออฟไลน์ • หยุด polling เพื่อประหยัดแบต", false);
    }

    private void bootstrapSession() {
        main.post(() -> {
            if (stopping) return;

            if (!NetworkHelper.isOnline(this)) {
                enterOfflineState();
                return;
            }

            if (SessionStore.isReady()) {
                scheduleMonitor(250L);
                return;
            }

            if (bootstrapWebView != null) return;

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
                            consecutiveErrors = 0;
                            Prefs.setConsecutiveErrors(AutoExportService.this, 0);
                            Prefs.setStatus(
                                    AutoExportService.this,
                                    "RUNNING",
                                    "FineBI session พร้อม"
                            );
                            updateNotification("FineBI session พร้อม", false);
                            scheduleMonitor(250L);
                            main.postDelayed(() -> destroyBootstrapWebView(), 1500L);
                        }

                        @Override
                        public void onMainFrameError(String description) {
                            if (!NetworkHelper.isOnline(AutoExportService.this)) {
                                enterOfflineState();
                                return;
                            }
                            Prefs.setStatus(
                                    AutoExportService.this,
                                    "VPN_REQUIRED",
                                    "FineBI เข้าไม่ได้ • ตรวจ Flashlink"
                            );
                            updateNotification("FineBI เข้าไม่ได้ • ตรวจ Flashlink", true);
                            tryOpenFlashlinkRateLimited();
                        }
                    }
            );
            bootstrapWebView.loadUrl(FineBiConfig.ENTRY_URL);
        });
    }

    private void scheduleMonitor(long delayMs) {
        if (stopping || worker == null) return;
        worker.removeCallbacks(monitorRunnable);
        worker.postDelayed(monitorRunnable, Math.max(0L, delayMs));
    }

    private void cancelMonitor() {
        if (worker != null) worker.removeCallbacks(monitorRunnable);
    }

    private void monitorOnce() {
        if (stopping) return;
        Prefs.touchServiceHeartbeat(this);
        MailManager.kick(this);
        UpdateManager.maybeCheckAndNotify(this);

        if (!NetworkHelper.isOnline(this)) {
            consecutiveErrors = 0;
            Prefs.setConsecutiveErrors(this, 0);
            enterOfflineState();
            return;
        }

        if (!SessionStore.isReady()) {
            Prefs.setStatus(this, "SESSION", "กำลังเรียกคืน FineBI session");
            bootstrapSession();
            scheduleMonitor(5_000L);
            return;
        }

        if (!TemplateStore.isReady(this)) {
            Prefs.setStatus(
                    this,
                    "NEEDS_SETUP",
                    "ตั้งค่าครั้งแรกหลังติดตั้ง: เปิด FineBI แล้ว Export Excel 1 ครั้ง"
            );
            Prefs.setPollPlan(this, "SETUP", System.currentTimeMillis() + 5 * 60_000L, 0L, false);
            updateNotification("ตั้งค่า Export ครั้งแรก 1 ครั้ง • แตะเพื่อเปิดแอป", true);
            scheduleMonitor(5 * 60_000L);
            return;
        }

        Prefs.markPollAttempt(this);

        try {
            TemplateStore.UpdateTemplate updateTemplate = TemplateStore.loadUpdate(this);
            JsonUtils.NormalizeResult normalized = JsonUtils.normalizeFineBiBody(
                    updateTemplate.body,
                    SessionStore.sessionId(),
                    true
            );

            FineBiApi.Result result = FineBiApi.post(
                    updateTemplate.url,
                    normalized.body,
                    20_000
            );

            if (result.code == 401 || result.code == 403) {
                SessionStore.clear();
                consecutiveErrors = 0;
                Prefs.setConsecutiveErrors(this, 0);
                Prefs.setStatus(this, "SESSION_EXPIRED", "FineBI session หมดอายุ • กำลังจับใหม่");
                updateNotification("Session หมดอายุ • กำลังจับใหม่", true);
                destroyBootstrapWebView();
                bootstrapSession();
                scheduleMonitor(5_000L);
                return;
            }

            if (!result.ok()) throw new IllegalStateException("FineBI HTTP " + result.code);

            Matcher m = TIME_PATTERN.matcher(result.text());
            if (!m.find()) throw new IllegalStateException("ไม่พบ th_update_time ใน response");

            String update = m.group();
            consecutiveErrors = 0;
            Prefs.setConsecutiveErrors(this, 0);
            Prefs.setBackendUpdate(this, update);
            Prefs.markPollOk(this, update);
            Prefs.setError(this, "");

            SharedPreferences prefs = Prefs.get(this);
            String lastSuccessful = prefs.getString(Prefs.LAST_EXPORTED, "");

            if (isVersionNewer(update, lastSuccessful)) {
                exportVersion(update);
                lastSuccessful = Prefs.get(this).getString(Prefs.LAST_EXPORTED, lastSuccessful);
            } else if (isVersionOlder(update, lastSuccessful)) {
                Prefs.setStatus(
                        this,
                        "WAITING",
                        "FineBI ส่ง version เก่ากว่าไฟล์ล่าสุด • รอ version ใหม่จริง"
                );
            }

            scheduleBySmartBattery(lastSuccessful);
        } catch (Exception e) {
            if (!NetworkHelper.isOnline(this)) {
                consecutiveErrors = 0;
                Prefs.setConsecutiveErrors(this, 0);
                enterOfflineState();
                return;
            }

            consecutiveErrors++;
            Prefs.setConsecutiveErrors(this, consecutiveErrors);
            Prefs.setError(this, e.getClass().getSimpleName() + ": " + e.getMessage());

            if (consecutiveErrors >= 3) {
                Prefs.setStatus(this, "VPN_OR_NETWORK", "FineBI ไม่ตอบ • ตรวจ Flashlink");
                updateNotification("FineBI ไม่ตอบ • ตรวจ Flashlink", true);
                tryOpenFlashlinkRateLimited();
                SessionStore.clear();
                destroyBootstrapWebView();
                bootstrapSession();
            } else {
                Prefs.setStatus(this, "RETRYING", "FineBI error • กำลังลองใหม่");
                updateNotification("FineBI error • กำลังลองใหม่", true);
            }

            scheduleMonitor(errorRetryDelayMs());
        }
    }

    private void scheduleBySmartBattery(String lastSuccessful) {
        boolean smart = Prefs.get(this).getBoolean(Prefs.SMART_BATTERY, true);
        long now = System.currentTimeMillis();

        if (!smart) {
            long delay = 10_000L;
            Prefs.setPollPlan(this, "PERFORMANCE", now + delay, 0L, true);
            Prefs.setStatus(this, "RUNNING", "Performance Mode • ตรวจทุก 10 วินาที");
            updateNotification("Performance Mode • ตรวจทุก 10 วินาที", false);
            scheduleMonitor(delay);
            return;
        }

        SmartBatteryPolicy.Plan plan = SmartBatteryPolicy.next(lastSuccessful, now);
        String message;
        String notification;

        if ("FAST".equals(plan.mode)) {
            message = "Smart Battery • FAST 10s • รอ th_update_time ใหม่จริงจาก "
                    + safeVersion(lastSuccessful)
                    + " • ไม่หยุดจนกว่าจะ Export สำเร็จ";
            notification = "FAST 10s • รอ version ใหม่จาก " + safeVersion(lastSuccessful);
        } else if ("ECO".equals(plan.mode)) {
            long minutes = Math.max(1L, (plan.delayMs + 59_999L) / 60_000L);
            message = "Smart Battery • ECO • เช็กอีกประมาณ " + minutes
                    + " นาที • FAST ก่อนรอบถัดไป 5 นาที";
            notification = "ECO • รอบถัดไปประมาณ "
                    + SmartBatteryPolicy.formatClock(plan.expectedNextAtMs);
        } else {
            message = "Smart Battery • SYNC • กำลังจับ version แรก";
            notification = "SYNC • กำลังจับ version แรก";
        }

        Prefs.setPollPlan(
                this,
                plan.mode,
                now + plan.delayMs,
                plan.expectedNextAtMs,
                plan.waitingForNewVersion
        );
        Prefs.setStatus(this, "RUNNING", message);
        updateNotification(notification, false);
        scheduleMonitor(plan.delayMs);
    }

    private long errorRetryDelayMs() {
        if (consecutiveErrors <= 2) return 8_000L;
        if (consecutiveErrors <= 5) return 30_000L;
        return 2 * 60_000L;
    }

    private boolean isVersionNewer(String current, String lastSuccessful) {
        if (current == null || current.isEmpty()) return false;
        if (lastSuccessful == null || lastSuccessful.isEmpty() || "-".equals(lastSuccessful)) return true;
        long c = SmartBatteryPolicy.parseFineBiTime(current);
        long l = SmartBatteryPolicy.parseFineBiTime(lastSuccessful);
        if (c > 0L && l > 0L) return c > l;
        return current.compareTo(lastSuccessful) > 0;
    }

    private boolean isVersionOlder(String current, String lastSuccessful) {
        if (current == null || lastSuccessful == null || lastSuccessful.isEmpty()) return false;
        long c = SmartBatteryPolicy.parseFineBiTime(current);
        long l = SmartBatteryPolicy.parseFineBiTime(lastSuccessful);
        if (c > 0L && l > 0L) return c < l;
        return current.compareTo(lastSuccessful) < 0;
    }

    private String safeVersion(String value) {
        return value == null || value.isEmpty() ? "รอบล่าสุด" : value;
    }

    private void exportVersion(String update) throws Exception {
        String safe = update.replace(":", "-").replace(" ", "_");
        String displayName = "HUB_departure_monitor_" + safe + ".xlsx";

        if (ExportStore.exists(this, displayName)) {
            Prefs.setExport(this, update, "", displayName);
            MailManager.onExportSaved(this, update, displayName);
            updateNotification("มีไฟล์รอบ " + update + " อยู่แล้ว", false);
            return;
        }

        if (!TemplateStore.isReady(this)) {
            Prefs.setStatus(
                    this,
                    "NEEDS_SETUP",
                    "ตั้งค่าครั้งแรกหลังติดตั้ง: เปิด FineBI แล้ว Export Excel 1 ครั้ง"
            );
            updateNotification("ต้องตั้งค่า Export ครั้งแรก 1 ครั้ง", true);
            return;
        }

        TemplateStore.ExportTemplate exportTemplate = TemplateStore.loadExport(this);

        Prefs.setStatus(this, "EXPORTING", "กำลัง Export " + update);
        updateNotification("กำลัง Export " + update + " • HUB=ALL", false);

        JsonUtils.NormalizeResult normalized = JsonUtils.normalizeFineBiBody(
                exportTemplate.body,
                SessionStore.sessionId(),
                true
        );

        if (normalized.forceAllCount < 1) {
            throw new IllegalStateException(
                    "ไม่พบ HUB filter ใน export payload — BLOCK เพื่อกันไฟล์ที่ไม่ใช่ Select All"
            );
        }

        FineBiApi.Result result = FineBiApi.post(
                exportTemplate.url,
                normalized.body,
                60_000
        );

        if (!result.ok()) throw new IllegalStateException("Export HTTP " + result.code);
        if (result.bytes.length < 4
                || result.bytes[0] != 0x50
                || result.bytes[1] != 0x4B) {
            throw new IllegalStateException("Export response ไม่ใช่ XLSX");
        }

        ExportStore.Saved saved = ExportStore.saveValidated(this, result.bytes, update);

        Prefs.setExport(
                this,
                update,
                saved.uri == null ? "" : saved.uri.toString(),
                saved.displayName
        );
        Prefs.setStatus(this, "RUNNING", "Export สำเร็จ " + update);
        Prefs.setError(this, "");
        MailManager.onExportSaved(this, update, saved.displayName);
        updateNotification("Export สำเร็จ " + update + " • HUB=ALL", false);
    }

    private void tryOpenFlashlinkRateLimited() {
        if (!NetworkHelper.isOnline(this)) return;
        long now = System.currentTimeMillis();
        if (now - lastFlashlinkAttemptAt < 120_000L) return;
        lastFlashlinkAttemptAt = now;
        main.post(() -> FlashlinkHelper.open(this));
    }

    private void destroyBootstrapWebView() {
        main.post(() -> {
            if (bootstrapWebView != null) {
                bootstrapWebView.stopLoading();
                bootstrapWebView.destroy();
                bootstrapWebView = null;
            }
        });
    }

    private Notification buildNotification(String text, boolean needsAttention) {
        Intent openApp = new Intent(this, DailyMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(
                this,
                101,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stop = new Intent(this, AutoExportService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this,
                2,
                stop,
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
        if (!attention && text != null && text.equals(lastNotificationText)) return;
        lastNotificationText = text == null ? "" : text;
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
            channel.setDescription("สถานะ FineBI Auto Export และ Smart Battery Mode");
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        stopping = true;
        cancelMonitor();
        unregisterNetworkWatcher();
        if (workerThread != null) workerThread.quitSafely();
        destroyBootstrapWebView();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
