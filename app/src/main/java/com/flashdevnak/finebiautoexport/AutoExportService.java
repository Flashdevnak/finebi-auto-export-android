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

    private static final long SESSION_RELOAD_MS = 8_000L;
    private static final long SESSION_RECREATE_MS = 35_000L;
    private static final long FLASHLINK_WAKE_INTERVAL_MS = 45_000L;
    private static final long HEALTH_WATCHDOG_MS = 60_000L;
    private static final long POLL_OVERDUE_GRACE_MS = 2 * 60_000L;
    private static final long NO_SUCCESS_RECOVERY_MS = 12 * 60_000L;
    private static final long FIRST_SUCCESS_GRACE_MS = 3 * 60_000L;

    private HandlerThread workerThread;
    private Handler worker;
    private Handler main;
    private WebView bootstrapWebView;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile boolean stopping;
    private volatile int consecutiveErrors;
    private volatile long lastFlashlinkAttemptAt;
    private volatile long bootstrapStartedAt;
    private volatile long lastBootstrapReloadAt;
    private volatile int bootstrapAttempts;
    private volatile boolean returnToAppAfterRecovery;
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
                        "ระบบพบข้อผิดพลาด • กำลังกู้คืนการเชื่อมต่ออัตโนมัติ"
                );
                updateNotification("กำลังกู้คืนการเชื่อมต่ออัตโนมัติ", false);
                restartBootstrapSession();
                scheduleMonitor(Math.min(2 * 60_000L, Math.max(15_000L, errorRetryDelayMs())));
            }
        }
    };

    private final Runnable healthWatchdogRunnable = new Runnable() {
        @Override public void run() {
            if (stopping) return;
            try {
                healthCheckOnce();
            } catch (Throwable ignored) {
            } finally {
                if (!stopping) scheduleHealthWatchdog();
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
        scheduleHealthWatchdog();

        UpdateManager.maybeCheckAndNotify(this);
        MailManager.kick(this);

        if (!NetworkHelper.isOnline(this)) {
            enterOfflineState();
        } else if (SessionStore.isReady()) {
            Prefs.setStatus(this, "STARTING", "กำลังตรวจ FineBI");
            scheduleMonitor(500L);
        } else {
            Prefs.setStatus(this, "STARTING", "กำลังเชื่อม FineBI อัตโนมัติ");
            bootstrapSession();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Prefs.touchServiceHeartbeat(this);

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopping = true;
            cancelMonitor();
            cancelHealthWatchdog();
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
                        consecutiveErrors = 0;
                        Prefs.setConsecutiveErrors(AutoExportService.this, 0);
                        Prefs.setStatus(
                                AutoExportService.this,
                                "NETWORK_BACK",
                                "เครือข่ายกลับมา • กำลังกู้คืน FineBI อัตโนมัติ"
                        );
                        updateNotification("เครือข่ายกลับมา • กำลังกู้คืนอัตโนมัติ", false);
                        MailManager.kick(AutoExportService.this);
                        UpdateManager.maybeCheckAndNotify(AutoExportService.this);
                        if (!SessionStore.isReady()) {
                            restartBootstrapSession();
                        }
                        scheduleMonitor(750L);
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
        destroyBootstrapWebView();
        Prefs.touchServiceHeartbeat(this);
        Prefs.setStatus(
                this,
                "OFFLINE",
                "เครือข่ายหาย • ระบบรอและจะเชื่อมต่อกลับเองเมื่ออินเทอร์เน็ตพร้อม"
        );
        Prefs.setPollPlan(this, "OFFLINE", 0L, 0L, false);
        updateNotification("ออฟไลน์ • จะเชื่อมต่อกลับเองเมื่อเครือข่ายพร้อม", false);
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

            long now = System.currentTimeMillis();
            if (bootstrapWebView != null) {
                if (bootstrapStartedAt > 0L && now - bootstrapStartedAt >= SESSION_RECREATE_MS) {
                    destroyBootstrapWebViewNow();
                } else if (now - lastBootstrapReloadAt >= SESSION_RELOAD_MS) {
                    lastBootstrapReloadAt = now;
                    bootstrapAttempts++;
                    Prefs.setStatus(
                            this,
                            "SESSION_RECOVERY",
                            "กำลังเชื่อม FineBI ใหม่อัตโนมัติ • ครั้งที่ " + bootstrapAttempts
                    );
                    updateNotification("กำลังเชื่อม FineBI ใหม่อัตโนมัติ", false);
                    bootstrapWebView.stopLoading();
                    bootstrapWebView.loadUrl(FineBiConfig.ENTRY_URL);
                    return;
                } else {
                    return;
                }
            }

            bootstrapStartedAt = now;
            lastBootstrapReloadAt = now;
            bootstrapAttempts = Math.max(1, bootstrapAttempts + 1);
            Prefs.setStatus(this, "SESSION", "กำลังเชื่อม FineBI อัตโนมัติ");
            updateNotification("กำลังเชื่อม FineBI อัตโนมัติ", false);

            bootstrapWebView = WebViewFactory.create(
                    this,
                    new FineBiWebViewClient.Listener() {
                        @Override public void onPageStarted(String url) {}

                        @Override
                        public void onPageFinished(String url) {
                            if (!SessionStore.isReady()) {
                                Prefs.setStatus(
                                        AutoExportService.this,
                                        "SESSION_RECOVERY",
                                        "ยังไม่พบ FineBI session • ระบบจะลองเชื่อมใหม่เอง"
                                );
                                if (bootstrapAttempts >= 2) {
                                    tryOpenFlashlinkRateLimited();
                                }
                                scheduleMonitor(5_000L);
                            }
                        }

                        @Override
                        public void onSessionCaptured() {
                            consecutiveErrors = 0;
                            bootstrapAttempts = 0;
                            bootstrapStartedAt = 0L;
                            lastBootstrapReloadAt = 0L;
                            Prefs.setConsecutiveErrors(AutoExportService.this, 0);
                            Prefs.setStatus(
                                    AutoExportService.this,
                                    "RUNNING",
                                    "FineBI พร้อม • เชื่อมต่อกลับสำเร็จ"
                            );
                            updateNotification("FineBI พร้อม • ระบบทำงานต่ออัตโนมัติ", false);
                            scheduleMonitor(250L);
                            main.postDelayed(() -> destroyBootstrapWebView(), 1500L);
                            returnToAppIfNeeded();
                        }

                        @Override
                        public void onMainFrameError(String description) {
                            if (!NetworkHelper.isOnline(AutoExportService.this)) {
                                enterOfflineState();
                                return;
                            }
                            Prefs.setStatus(
                                    AutoExportService.this,
                                    "FLASHLINK_RECOVERY",
                                    "FineBI ยังเข้าไม่ได้ • กำลังกู้คืน Flashlink/FineBI อัตโนมัติ"
                            );
                            updateNotification("กำลังกู้คืน Flashlink และ FineBI", false);
                            tryOpenFlashlinkRateLimited();
                            scheduleMonitor(5_000L);
                        }
                    }
            );
            bootstrapWebView.loadUrl(FineBiConfig.ENTRY_URL);
        });
    }

    private void restartBootstrapSession() {
        main.post(() -> {
            destroyBootstrapWebViewNow();
            if (!stopping && NetworkHelper.isOnline(AutoExportService.this)
                    && !SessionStore.isReady()) {
                bootstrapSession();
            }
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

    private void scheduleHealthWatchdog() {
        if (stopping || worker == null) return;
        worker.removeCallbacks(healthWatchdogRunnable);
        worker.postDelayed(healthWatchdogRunnable, HEALTH_WATCHDOG_MS);
    }

    private void cancelHealthWatchdog() {
        if (worker != null) worker.removeCallbacks(healthWatchdogRunnable);
    }

    /**
     * Connection watchdog intentionally uses successful API response time, not th_update_time.
     * FineBI may legitimately keep the same data version for a long time; that must not be
     * interpreted as a broken session or trigger visible navigation.
     */
    private void healthCheckOnce() {
        if (stopping) return;
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean(Prefs.ENABLED, false)) return;
        if (!NetworkHelper.isOnline(this)) return;

        long now = System.currentTimeMillis();
        long lastPollOk = p.getLong(Prefs.LAST_POLL_OK_AT, 0L);
        long nextCheck = p.getLong(Prefs.NEXT_CHECK_AT, 0L);
        long serviceStart = p.getLong(Prefs.LAST_SERVICE_START_AT, 0L);
        String state = p.getString(Prefs.SERVICE_STATE, "");

        if (isRecoveryState(state)) return;

        if (lastPollOk <= 0L) {
            if (serviceStart > 0L && now - serviceStart >= FIRST_SUCCESS_GRACE_MS) {
                Prefs.setStatus(
                        this,
                        "HEALTH_RECOVERY",
                        "FineBI ยังไม่ตอบสำเร็จ • กำลังกู้คืนเบื้องหลังอัตโนมัติ"
                );
                updateNotification("FineBI ยังไม่ตอบ • กำลังกู้คืนเบื้องหลัง", false);
                SessionStore.clear();
                restartBootstrapSession();
                scheduleMonitor(1_000L);
            }
            return;
        }

        boolean overdue = nextCheck > 0L && now > nextCheck + POLL_OVERDUE_GRACE_MS;
        boolean noSuccessTooLong = now - lastPollOk >= NO_SUCCESS_RECOVERY_MS;
        if (overdue && noSuccessTooLong) {
            Prefs.setStatus(
                    this,
                    "HEALTH_RECOVERY",
                    "FineBI ไม่ได้ตอบตามรอบ • กำลังกู้คืนเบื้องหลังอัตโนมัติ"
            );
            updateNotification("FineBI ขาดการตอบกลับ • กำลังกู้คืนเบื้องหลัง", false);
            SessionStore.clear();
            restartBootstrapSession();
            scheduleMonitor(1_000L);
        }
    }

    private boolean isRecoveryState(String state) {
        if (state == null) return false;
        return state.contains("RECOVERY")
                || "RECOVERING".equals(state)
                || "SESSION_EXPIRED".equals(state)
                || "NETWORK_BACK".equals(state);
    }

    private void monitorOnce() {
        if (stopping) return;
        Prefs.touchServiceHeartbeat(this);
        MailManager.kick(this);

        if (!NetworkHelper.isOnline(this)) {
            consecutiveErrors = 0;
            Prefs.setConsecutiveErrors(this, 0);
            enterOfflineState();
            return;
        }

        if (!SessionStore.isReady()) {
            Prefs.setStatus(this, "SESSION_RECOVERY", "กำลังเรียกคืน FineBI session อัตโนมัติ");
            if (bootstrapAttempts >= 2) tryOpenFlashlinkRateLimited();
            bootstrapSession();
            scheduleMonitor(5_000L);
            return;
        }

        if (!TemplateStore.isReady(this)) {
            Prefs.setStatus(
                    this,
                    "NEEDS_SETUP",
                    "ต้องตั้งค่าครั้งแรกเพียงครั้งเดียว: เปิด FineBI แล้ว Export Excel 1 ครั้ง"
            );
            Prefs.setPollPlan(this, "SETUP", System.currentTimeMillis() + 5 * 60_000L, 0L, false);
            updateNotification("ต้องตั้งค่า Export ครั้งแรก 1 ครั้ง", true);
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
                Prefs.setStatus(this, "SESSION_EXPIRED", "FineBI session หมดอายุ • กำลังเชื่อมใหม่อัตโนมัติ");
                updateNotification("Session หมดอายุ • กำลังเชื่อมใหม่อัตโนมัติ", false);
                restartBootstrapSession();
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
                Prefs.setStatus(this, "FLASHLINK_RECOVERY", "FineBI ไม่ตอบ • กำลังกู้คืน Flashlink/FineBI อัตโนมัติ");
                updateNotification("FineBI ไม่ตอบ • กำลังกู้คืนอัตโนมัติ", false);
                tryOpenFlashlinkRateLimited();
                SessionStore.clear();
                restartBootstrapSession();
            } else {
                Prefs.setStatus(this, "RETRYING", "FineBI ตอบผิดพลาด • กำลังลองใหม่อัตโนมัติ");
                updateNotification("FineBI error • กำลังลองใหม่", false);
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
            message = "ตรวจถี่ • รอข้อมูลรอบใหม่จาก "
                    + safeVersion(lastSuccessful)
                    + " • ทำงานต่อจน Export สำเร็จ";
            notification = "ตรวจถี่ • รอข้อมูลรอบใหม่จาก " + safeVersion(lastSuccessful);
        } else if ("ECO".equals(plan.mode)) {
            long minutes = Math.max(1L, (plan.delayMs + 59_999L) / 60_000L);
            message = "ประหยัดพลังงาน • เช็กอีกประมาณ " + minutes
                    + " นาที • เพิ่มความถี่ก่อนรอบถัดไป";
            notification = "รอบถัดไปประมาณ "
                    + SmartBatteryPolicy.formatClock(plan.expectedNextAtMs);
        } else {
            message = "กำลังซิงก์ข้อมูลรอบแรก";
            notification = "กำลังซิงก์ข้อมูลรอบแรก";
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
        if (consecutiveErrors <= 5) return 20_000L;
        return 60_000L;
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
                    "ต้องตั้งค่าครั้งแรกเพียงครั้งเดียว: เปิด FineBI แล้ว Export Excel 1 ครั้ง"
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
        if (!NetworkHelper.isOnline(this) || !FlashlinkHelper.isInstalled(this)) return;
        long now = System.currentTimeMillis();
        if (now - lastFlashlinkAttemptAt < FLASHLINK_WAKE_INTERVAL_MS) return;
        lastFlashlinkAttemptAt = now;

        if (AppVisibility.isDailyVisible()) {
            returnToAppAfterRecovery = true;
        }

        Prefs.setStatus(this, "FLASHLINK_RECOVERY", "กำลังปลุก Flashlink และเชื่อม FineBI ใหม่อัตโนมัติ");
        main.post(() -> FlashlinkHelper.open(this));
    }

    private void returnToAppIfNeeded() {
        if (!returnToAppAfterRecovery) return;
        returnToAppAfterRecovery = false;
        main.postDelayed(() -> {
            try {
                Intent i = new Intent(AutoExportService.this, DailyMainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pi = PendingIntent.getActivity(
                        AutoExportService.this,
                        909,
                        i,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                pi.send();
            } catch (Exception ignored) {}
        }, 600L);
    }

    private void destroyBootstrapWebView() {
        main.post(this::destroyBootstrapWebViewNow);
    }

    private void destroyBootstrapWebViewNow() {
        if (bootstrapWebView != null) {
            try { bootstrapWebView.stopLoading(); } catch (Throwable ignored) {}
            try { bootstrapWebView.destroy(); } catch (Throwable ignored) {}
            bootstrapWebView = null;
        }
        bootstrapStartedAt = 0L;
        lastBootstrapReloadAt = 0L;
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
            channel.setDescription("สถานะ Auto Export และการกู้คืนเครือข่ายอัตโนมัติ");
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        stopping = true;
        cancelMonitor();
        cancelHealthWatchdog();
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
