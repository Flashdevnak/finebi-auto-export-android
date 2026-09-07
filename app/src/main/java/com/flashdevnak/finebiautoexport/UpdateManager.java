package com.flashdevnak.finebiautoexport;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Lightweight in-app updater backed by this project's public GitHub Releases.
 * No auth/session/cookie data is involved.
 *
 * Android still requires the user to confirm the APK install. The app handles
 * checking, downloading and signature/package verification before that prompt.
 */
public final class UpdateManager {
    public interface Listener {
        void onChanged(Info info, String message);
    }

    public static final class Info {
        public final boolean available;
        public final String version;
        public final String url;
        public final String notes;
        public final long checkedAt;

        Info(boolean available, String version, String url, String notes, long checkedAt) {
            this.available = available;
            this.version = version == null ? "" : version;
            this.url = url == null ? "" : url;
            this.notes = notes == null ? "" : notes;
            this.checkedAt = checkedAt;
        }
    }

    private static final String PREF = "finebi_app_update";
    private static final String K_VERSION = "latest_version";
    private static final String K_URL = "latest_url";
    private static final String K_NOTES = "latest_notes";
    private static final String K_CHECKED = "last_checked_at";
    private static final String K_PENDING = "pending_install";
    private static final long AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;

    private static final String RELEASE_API =
            "https://api.github.com/repos/Flashdevnak/finebi-auto-export-android/releases/latest";
    private static final String UPDATE_CHANNEL = "finebi_app_updates";
    private static final int UPDATE_NOTIFICATION_ID = 21030;

    private static volatile boolean checking;
    private static volatile boolean downloading;

    private UpdateManager() {}

    public static Info cached(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String version = p.getString(K_VERSION, "");
        String url = p.getString(K_URL, "");
        String notes = p.getString(K_NOTES, "");
        long checked = p.getLong(K_CHECKED, 0L);
        return new Info(isNewer(version, BuildConfig.VERSION_NAME) && !url.isEmpty(),
                version, url, notes, checked);
    }

    public static void checkAsync(Context context, boolean force, Listener listener) {
        Context app = context.getApplicationContext();
        Info cached = cached(app);
        long now = System.currentTimeMillis();
        if (!force && cached.checkedAt > 0L && now - cached.checkedAt < AUTO_CHECK_INTERVAL_MS) {
            if (listener != null) listener.onChanged(cached, cached.available ? "มีอัปเดตใหม่" : "เป็นเวอร์ชันล่าสุดแล้ว");
            return;
        }
        if (checking) {
            if (listener != null) listener.onChanged(cached, "กำลังตรวจอัปเดต");
            return;
        }

        checking = true;
        new Thread(() -> {
            String message;
            Info info;
            try {
                info = fetchLatest(app);
                message = info.available
                        ? "มีเวอร์ชัน " + info.version + " พร้อมอัปเดต"
                        : "เป็นเวอร์ชันล่าสุดแล้ว";
            } catch (Exception e) {
                info = cached(app);
                message = "ตรวจอัปเดตไม่ได้: " + e.getClass().getSimpleName();
            } finally {
                checking = false;
            }
            if (listener != null) listener.onChanged(info, message);
        }, "FineBI-UpdateCheck").start();
    }

    public static void maybeCheckAndNotify(Context context) {
        checkAsync(context, false, (info, message) -> {
            if (info.available) notifyUpdate(context.getApplicationContext(), info);
        });
    }

    public static void startUpdate(Activity activity, Info info, Listener listener) {
        if (info == null || !info.available || info.url.isEmpty()) {
            checkAsync(activity, true, listener);
            return;
        }

        if (Build.VERSION.SDK_INT >= 26
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putBoolean(K_PENDING, true).apply();
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(settings);
            if (listener != null) {
                listener.onChanged(info, "อนุญาตติดตั้งแอปจากแหล่งนี้ 1 ครั้ง แล้วกลับเข้าแอป");
            }
            return;
        }

        activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(K_PENDING, false).apply();
        downloadAndInstall(activity, info, listener);
    }

    public static void resumePendingInstall(Activity activity, Listener listener) {
        SharedPreferences p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!p.getBoolean(K_PENDING, false)) return;
        if (Build.VERSION.SDK_INT >= 26
                && !activity.getPackageManager().canRequestPackageInstalls()) return;
        Info info = cached(activity);
        if (!info.available || info.url.isEmpty()) {
            p.edit().putBoolean(K_PENDING, false).apply();
            return;
        }
        p.edit().putBoolean(K_PENDING, false).apply();
        downloadAndInstall(activity, info, listener);
    }

    private static Info fetchLatest(Context context) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "FineBI-Auto-Export-Android/" + BuildConfig.VERSION_NAME);
        int code = c.getResponseCode();
        if (code != 200) throw new IllegalStateException("GitHub HTTP " + code);

        String json = readText(c.getInputStream(), 2_000_000);
        JSONObject root = new JSONObject(json);
        String version = stripV(root.optString("tag_name", ""));
        String notes = root.optString("body", "");
        String apkUrl = "";

        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (name.toLowerCase(Locale.US).endsWith(".apk") && !url.isEmpty()) {
                    apkUrl = url;
                    break;
                }
            }
        }

        long checked = System.currentTimeMillis();
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(K_VERSION, version)
                .putString(K_URL, apkUrl)
                .putString(K_NOTES, notes)
                .putLong(K_CHECKED, checked)
                .apply();

        return new Info(isNewer(version, BuildConfig.VERSION_NAME) && !apkUrl.isEmpty(),
                version, apkUrl, notes, checked);
    }

    private static void downloadAndInstall(Activity activity, Info info, Listener listener) {
        if (downloading) {
            if (listener != null) listener.onChanged(info, "กำลังดาวน์โหลดอัปเดตอยู่");
            return;
        }
        downloading = true;

        new Thread(() -> {
            try {
                if (listener != null) listener.onChanged(info, "กำลังดาวน์โหลด v" + info.version);
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("สร้างโฟลเดอร์อัปเดตไม่ได้");
                }
                File apk = new File(dir, "FineBI-Auto-Export-v" + info.version + ".apk");
                download(info.url, apk);
                verifyApk(activity, apk);
                if (listener != null) listener.onChanged(info, "ดาวน์โหลดและตรวจลายเซ็นผ่าน • กำลังเปิดตัวติดตั้ง");
                commitInstall(activity, apk);
            } catch (Exception e) {
                if (listener != null) {
                    listener.onChanged(info,
                            "อัปเดตไม่สำเร็จ: " + e.getClass().getSimpleName()
                                    + (e.getMessage() == null ? "" : " • " + e.getMessage()));
                }
            } finally {
                downloading = false;
            }
        }, "FineBI-AppUpdate").start();
    }

    private static void download(String url, File outFile) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20_000);
        c.setReadTimeout(60_000);
        c.setRequestProperty("User-Agent", "FineBI-Auto-Export-Android/" + BuildConfig.VERSION_NAME);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("Download HTTP " + code);

        try (InputStream in = c.getInputStream();
             OutputStream out = new FileOutputStream(outFile)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
            }
        }
        if (outFile.length() < 10_000L) throw new IllegalStateException("ไฟล์ APK เล็กผิดปกติ");
    }

    private static void verifyApk(Context context, File apk) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;

        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) throw new IllegalStateException("อ่าน APK ไม่ได้");
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new SecurityException("package ไม่ตรง");
        }

        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
        long archiveCode = Build.VERSION.SDK_INT >= 28
                ? archive.getLongVersionCode() : archive.versionCode;
        long installedCode = Build.VERSION.SDK_INT >= 28
                ? installed.getLongVersionCode() : installed.versionCode;
        if (archiveCode <= installedCode) {
            throw new IllegalStateException("เวอร์ชันที่ดาวน์โหลดไม่ใหม่กว่าเครื่อง");
        }

        String currentSigner = signerSha256(installed);
        String archiveSigner = signerSha256(archive);
        if (currentSigner.isEmpty() || !currentSigner.equalsIgnoreCase(archiveSigner)) {
            throw new SecurityException("ลายเซ็น APK ไม่ตรงกับแอปปัจจุบัน");
        }
    }

    private static String signerSha256(PackageInfo info) throws Exception {
        Signature sig = null;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            Signature[] signers = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
            if (signers != null && signers.length > 0) sig = signers[0];
        } else if (info.signatures != null && info.signatures.length > 0) {
            sig = info.signatures[0];
        }
        if (sig == null) return "";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray());
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static void commitInstall(Context context, File apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int sessionId = installer.createSession(params);

        try (PackageInstaller.Session session = installer.openSession(sessionId);
             InputStream in = new FileInputStream(apk);
             OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
            }
            session.fsync(out);

            Intent result = new Intent(context, UpdateInstallReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    result,
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)
            );
            session.commit(pending.getIntentSender());
        }
    }

    public static void notifyUpdate(Context context, Info info) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    UPDATE_CHANNEL,
                    "FineBI App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            ch.setDescription("แจ้งเตือนเมื่อ FineBI Auto Export มีเวอร์ชันใหม่");
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(context, DailyMainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 31, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, UPDATE_CHANNEL)
                : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("FineBI Auto Export มีอัปเดต")
                .setContentText("แตะเพื่ออัปเดตเป็น v" + info.version)
                .setAutoCancel(true)
                .setContentIntent(pi);
        nm.notify(UPDATE_NOTIFICATION_ID, b.build());
    }

    private static String readText(InputStream in, int max) throws Exception {
        try (InputStream input = in;
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int total = 0;
            int n;
            while ((n = input.read(buf)) >= 0) {
                if (total + n > max) n = max - total;
                if (n > 0) {
                    bos.write(buf, 0, n);
                    total += n;
                }
                if (total >= max) break;
            }
            return bos.toString("UTF-8");
        }
    }

    private static String stripV(String value) {
        if (value == null) return "";
        String s = value.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        return s;
    }

    static boolean isNewer(String candidate, String current) {
        String a = stripV(candidate);
        String b = stripV(current);
        if (a.isEmpty()) return false;
        String[] aa = a.split("[^0-9]+");
        String[] bb = b.split("[^0-9]+");
        int n = Math.max(aa.length, bb.length);
        for (int i = 0; i < n; i++) {
            int av = i < aa.length && !aa[i].isEmpty() ? parseInt(aa[i]) : 0;
            int bv = i < bb.length && !bb[i].isEmpty() ? parseInt(bb[i]) : 0;
            if (av != bv) return av > bv;
        }
        return false;
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return 0; }
    }
}
