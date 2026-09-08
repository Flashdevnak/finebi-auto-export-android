package com.flashdevnak.finebiautoexport;

import android.content.Context;
import android.content.SharedPreferences;

public final class MailSettings {
    private static final String FILE = "finebi_mail_settings";

    public static final String ENABLED = "enabled";
    public static final String TO = "to";
    public static final String CC = "cc";
    public static final String GOOGLE_CONNECTED = "google_connected";
    public static final String GOOGLE_EMAIL = "google_email";

    public static final String PENDING_VERSION = "pending_version";
    public static final String PENDING_FILE = "pending_file";
    public static final String LAST_SENT_VERSION = "last_sent_version";
    public static final String LAST_SENT_AT = "last_sent_at";
    public static final String STATUS = "status";
    public static final String NEXT_RETRY_AT = "next_retry_at";
    public static final String FAIL_COUNT = "fail_count";

    private MailSettings() {}

    public static SharedPreferences get(Context c) {
        SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        migrateAuthRequiredConnection(p);
        return p;
    }

    /**
     * v0.8.2 migration: older builds marked GOOGLE_CONNECTED=false whenever a
     * temporary OAuth resolution was needed, even though the selected sender
     * email remained stored. Restore that identity without fabricating a token;
     * sending stays paused by AUTH_REQUIRED until Google confirms permission.
     */
    private static void migrateAuthRequiredConnection(SharedPreferences p) {
        if (p.getBoolean(GOOGLE_CONNECTED, false)) return;
        String status = p.getString(STATUS, "");
        String email = p.getString(GOOGLE_EMAIL, "");
        if (status != null && status.startsWith("AUTH_REQUIRED")
                && email != null && !email.trim().isEmpty()) {
            p.edit().putBoolean(GOOGLE_CONNECTED, true).apply();
        }
    }

    public static boolean configured(Context c) {
        SharedPreferences p = get(c);
        return p.getBoolean(GOOGLE_CONNECTED, false)
                && !p.getString(TO, "").trim().isEmpty();
    }

    public static boolean enabled(Context c) {
        return get(c).getBoolean(ENABLED, false) && configured(c);
    }

    public static boolean authRequired(Context c) {
        String status = get(c).getString(STATUS, "");
        return status != null && status.startsWith("AUTH_REQUIRED");
    }

    public static void saveRecipients(
            Context c,
            boolean enabled,
            String to,
            String cc
    ) {
        get(c).edit()
                .putBoolean(ENABLED, enabled)
                .putString(TO, clean(to))
                .putString(CC, clean(cc))
                .apply();
        get(c).edit()
                .remove("sender")
                .remove("app_password_encrypted")
                .apply();
    }

    public static void markGoogleConnected(Context c, String email) {
        SharedPreferences p = get(c);
        String resolved = clean(email);
        if (resolved.isEmpty()) {
            resolved = p.getString(GOOGLE_EMAIL, "");
        }
        p.edit()
                .putBoolean(GOOGLE_CONNECTED, true)
                .putString(GOOGLE_EMAIL, resolved == null ? "" : resolved)
                .putString(STATUS, "GOOGLE_CONNECTED")
                .putLong(NEXT_RETRY_AT, 0L)
                .apply();
    }

    public static void markGoogleDisconnected(Context c) {
        get(c).edit()
                .putBoolean(GOOGLE_CONNECTED, false)
                .putString(GOOGLE_EMAIL, "")
                .putBoolean(ENABLED, false)
                .putString(STATUS, "GOOGLE_DISCONNECTED")
                .apply();
    }

    /**
     * Google may occasionally require consent again. That does not mean the
     * sender account has been removed from this app. Keep the selected account,
     * recipients and Auto Email preference intact; only pause sending until the
     * user confirms the permission again.
     */
    public static void markAuthRequired(Context c, String reason) {
        String safe = reason == null ? "" : reason;
        if (safe.length() > 160) safe = safe.substring(0, 160);
        get(c).edit()
                .putString(STATUS, "AUTH_REQUIRED" + (safe.isEmpty() ? "" : " • " + safe))
                .putLong(NEXT_RETRY_AT, 0L)
                .apply();
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
