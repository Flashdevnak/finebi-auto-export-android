package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
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

        GoogleOAuthManager.getAccessTokenSilent(app, new GoogleOAuthManager.TokenCallback() {
            @Override public void onToken(String token) {
                new Thread(() -> sendQueuedWithToken(app, token, version, fileName),
                        "FineBI-GmailSender").start();
            }

            @Override public void onUserActionRequired(String message) {
                MailSettings.markAuthRequired(app, message);
                SENDING.set(false);
            }

            @Override public void onError(String message) {
                MailSettings.markFailure(app, message);
                SENDING.set(false);
            }
        });
    }

    private static void sendQueuedWithToken(
            Context app,
            String token,
            String version,
            String fileName
    ) {
        try {
            MailSettings.markSending(app);
            byte[] bytes = ExportStore.readBytes(app, fileName, 25 * 1024 * 1024);
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("ไม่พบไฟล์ XLSX สำหรับส่งเมล");
            }

            List<String> to = recipients(MailSettings.get(app).getString(MailSettings.TO, ""));
            List<String> cc = recipients(MailSettings.get(app).getString(MailSettings.CC, ""));
            String subject = "[FineBI] HUB Departure Monitor • " + version;
            String body = "FineBI Auto Export\n"
                    + "รอบข้อมูล: " + version + "\n"
                    + "HUB: SELECT ALL\n"
                    + "สถานะ: Export + XLSX Validate PASS\n"
                    + "ไฟล์: " + fileName + "\n\n"
                    + "ส่งอัตโนมัติผ่าน Gmail API จาก FineBI Auto Export Android";

            GmailApiSender.send(token, to, cc, subject, body, fileName, bytes);
            MailSettings.markSent(app, version);
        } catch (GmailApiSender.GmailHttpException e) {
            if (e.code == 401 || e.code == 403) {
                MailSettings.markAuthRequired(app, "สิทธิ์ Gmail ต้องอนุญาตใหม่");
            } else {
                MailSettings.markFailure(app, e.getMessage());
            }
        } catch (Exception e) {
            MailSettings.markFailure(app,
                    e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        } finally {
            SENDING.set(false);
        }
    }

    public static void sendTest(Activity activity, Listener listener) {
        Context app = activity.getApplicationContext();
        if (!MailSettings.configured(app)) {
            if (listener != null) listener.onResult(false, "เชื่อมต่อ Google และกรอกผู้รับก่อน");
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

        GoogleOAuthManager.getAccessTokenSilent(app, new GoogleOAuthManager.TokenCallback() {
            @Override public void onToken(String token) {
                new Thread(() -> {
                    try {
                        List<String> to = recipients(MailSettings.get(app).getString(MailSettings.TO, ""));
                        List<String> cc = recipients(MailSettings.get(app).getString(MailSettings.CC, ""));
                        GmailApiSender.send(
                                token,
                                to,
                                cc,
                                "[FineBI] ทดสอบระบบส่งอีเมล",
                                "ทดสอบสำเร็จจาก FineBI Auto Export Android\nส่งผ่าน Google OAuth + Gmail API\nไม่มีไฟล์แนบในอีเมลทดสอบนี้",
                                null,
                                null
                        );
                        if (listener != null) listener.onResult(true, "ส่งอีเมลทดสอบสำเร็จ");
                    } catch (GmailApiSender.GmailHttpException e) {
                        if (e.code == 401 || e.code == 403) {
                            MailSettings.markAuthRequired(app, "สิทธิ์ Gmail ต้องอนุญาตใหม่");
                        }
                        if (listener != null) listener.onResult(false, "ส่งไม่สำเร็จ: " + e.getMessage());
                    } catch (Exception e) {
                        if (listener != null) listener.onResult(false,
                                "ส่งไม่สำเร็จ: " + e.getClass().getSimpleName()
                                        + (e.getMessage() == null ? "" : " • " + e.getMessage()));
                    } finally {
                        SENDING.set(false);
                    }
                }, "FineBI-GmailTest").start();
            }

            @Override public void onUserActionRequired(String message) {
                MailSettings.markAuthRequired(app, message);
                SENDING.set(false);
                if (listener != null) listener.onResult(false, message);
            }

            @Override public void onError(String message) {
                SENDING.set(false);
                if (listener != null) listener.onResult(false, message);
            }
        });
    }

    private static List<String> recipients(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw.split("[,;\\s]+")) {
            String v = s.trim();
            if (!v.isEmpty() && v.contains("@")) out.add(v);
        }
        return out;
    }
}
