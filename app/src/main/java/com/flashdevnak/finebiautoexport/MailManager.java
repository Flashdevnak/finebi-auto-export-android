package com.flashdevnak.finebiautoexport;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MailManager {
    public interface Listener {
        void onResult(boolean ok, String message);
    }

    private static final AtomicBoolean SENDING = new AtomicBoolean(false);

    private MailManager() {}

    public static void onExportSaved(Context context, String version, String fileName) {
        if (!MailSettings.enabled(context)) return;
        SharedPreferences p = MailSettings.get(context);
        if (version != null && version.equals(p.getString(MailSettings.LAST_SENT_VERSION, ""))) return;
        MailSettings.queue(context, version, fileName);
        kick(context);
    }

    public static void kick(Context context) {
        Context app = context.getApplicationContext();
        if (!MailSettings.enabled(app)) return;
        if (!NetworkHelper.isOnline(app)) return;

        SharedPreferences p = MailSettings.get(app);
        String version = p.getString(MailSettings.PENDING_VERSION, "");
        String fileName = p.getString(MailSettings.PENDING_FILE, "");
        long next = p.getLong(MailSettings.NEXT_RETRY_AT, 0L);
        if (version.isEmpty() || fileName.isEmpty()) return;
        if (next > System.currentTimeMillis()) return;
        if (!SENDING.compareAndSet(false, true)) return;

        new Thread(() -> {
            try {
                MailSettings.markSending(app);
                byte[] bytes = ExportStore.readBytes(app, fileName, 25 * 1024 * 1024);
                if (bytes == null || bytes.length == 0) {
                    throw new IllegalStateException("ไม่พบไฟล์ XLSX สำหรับส่งเมล");
                }
                String subject = "[FineBI] HUB Departure Monitor • " + version;
                String body = "FineBI Auto Export\n"
                        + "รอบข้อมูล: " + version + "\n"
                        + "HUB: SELECT ALL\n"
                        + "สถานะ: Export + XLSX Validate PASS\n"
                        + "ไฟล์: " + fileName + "\n\n"
                        + "ส่งอัตโนมัติจาก FineBI Auto Export Android";
                SmtpMailSender.send(app, subject, body, fileName, bytes);
                MailSettings.markSent(app, version);
            } catch (Exception e) {
                MailSettings.markFailure(app,
                        e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            } finally {
                SENDING.set(false);
            }
        }, "FineBI-MailSender").start();
    }

    public static void sendTest(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        if (!MailSettings.configured(app)) {
            if (listener != null) listener.onResult(false, "ตั้งค่า Gmail/ผู้รับให้ครบก่อน");
            return;
        }
        if (!NetworkHelper.isOnline(app)) {
            if (listener != null) listener.onResult(false, "ออฟไลน์ • ยังส่งอีเมลไม่ได้");
            return;
        }
        if (!SENDING.compareAndSet(false, true)) {
            if (listener != null) listener.onResult(false, "กำลังส่งอีเมลอยู่");
            return;
        }
        new Thread(() -> {
            try {
                SmtpMailSender.send(
                        app,
                        "[FineBI] ทดสอบระบบส่งอีเมล",
                        "ทดสอบสำเร็จจาก FineBI Auto Export Android\nไม่มีไฟล์แนบในอีเมลทดสอบนี้",
                        null,
                        null
                );
                if (listener != null) listener.onResult(true, "ส่งอีเมลทดสอบสำเร็จ");
            } catch (Exception e) {
                String msg = "ส่งไม่สำเร็จ: " + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : " • " + e.getMessage());
                if (listener != null) listener.onResult(false, msg);
            } finally {
                SENDING.set(false);
            }
        }, "FineBI-MailTest").start();
    }
}
