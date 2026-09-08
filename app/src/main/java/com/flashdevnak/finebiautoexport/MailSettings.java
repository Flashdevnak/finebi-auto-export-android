package com.flashdevnak.finebiautoexport;

import android.content.Context;
import android.content.SharedPreferences;

public final class MailSettings {
    private static final String FILE = "finebi_mail_settings";

    public static final String ENABLED = "enabled";
    public static final String SENDER = "sender";
    public static final String TO = "to";
    public static final String CC = "cc";
    private static final String APP_PASSWORD_ENCRYPTED = "app_password_encrypted";

    public static final String PENDING_VERSION = "pending_version";
    public static final String PENDING_FILE = "pending_file";
    public static final String LAST_SENT_VERSION = "last_sent_version";
    public static final String LAST_SENT_AT = "last_sent_at";
    public static final String STATUS = "status";
    public static final String NEXT_RETRY_AT = "next_retry_at";
    public static final String FAIL_COUNT = "fail_count";

    private MailSettings() {}

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean configured(Context c) {
        SharedPreferences p = get(c);
        return !p.getString(SENDER, "").trim().isEmpty()
                && !p.getString(TO, "").trim().isEmpty()
                && !p.getString(APP_PASSWORD_ENCRYPTED, "").isEmpty();
    }

    public static boolean enabled(Context c) {
        return get(c).getBoolean(ENABLED, false) && configured(c);
    }

    public static void save(
            Context c,
            boolean enabled,
            String sender,
            String to,
            String cc,
            String newAppPassword
    ) throws Exception {
        SharedPreferences.Editor e = get(c).edit()
                .putBoolean(ENABLED, enabled)
                .putString(SENDER, clean(sender))
                .putString(TO, clean(to))
                .putString(CC, clean(cc));
        if (newAppPassword != null && !newAppPassword.trim().isEmpty()) {
            e.putString(
                    APP_PASSWORD_ENCRYPTED,
                    SecretStore.encrypt(newAppPassword.replace(" ", "").trim())
            );
        }
        e.apply();
    }

    public static String appPassword(Context c) throws Exception {
        return SecretStore.decrypt(get(c).getString(APP_PASSWORD_ENCRYPTED, ""));
    }

    public static void queue(Context c, String version, String fileName) {
        SharedPreferences p = get(c);
        if (version == null || version.isEmpty()) return;
        if (version.equals(p.getString(LAST_SENT_VERSION, ""))) return;
        p.edit()
                .putString(PENDING_VERSION, version)
                .putString(PENDING_FILE, fileName == null ? "" : fileName)
                .putString(STATUS, "PENDING")
                .putLong(NEXT_RETRY_AT, System.currentTimeMillis())
                .putInt(FAIL_COUNT, 0)
                .apply();
    }

    public static void markSent(Context c, String version) {
        get(c).edit()
                .putString(LAST_SENT_VERSION, version == null ? "" : version)
                .putLong(LAST_SENT_AT, System.currentTimeMillis())
                .putString(PENDING_VERSION, "")
                .putString(PENDING_FILE, "")
                .putString(STATUS, "SENT")
                .putLong(NEXT_RETRY_AT, 0L)
                .putInt(FAIL_COUNT, 0)
                .apply();
    }

    public static void markSending(Context c) {
        get(c).edit().putString(STATUS, "SENDING").apply();
    }

    public static void markFailure(Context c, String reason) {
        SharedPreferences p = get(c);
        int count = p.getInt(FAIL_COUNT, 0) + 1;
        long delay;
        if (count <= 1) delay = 60_000L;
        else if (count == 2) delay = 5 * 60_000L;
        else if (count == 3) delay = 15 * 60_000L;
        else delay = 30 * 60_000L;
        String safe = reason == null ? "" : reason;
        if (safe.length() > 160) safe = safe.substring(0, 160);
        p.edit()
                .putInt(FAIL_COUNT, count)
                .putLong(NEXT_RETRY_AT, System.currentTimeMillis() + delay)
                .putString(STATUS, "RETRY " + count + (safe.isEmpty() ? "" : " • " + safe))
                .apply();
    }

    public static void clearPending(Context c) {
        get(c).edit()
                .putString(PENDING_VERSION, "")
                .putString(PENDING_FILE, "")
                .putLong(NEXT_RETRY_AT, 0L)
                .putInt(FAIL_COUNT, 0)
                .apply();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
