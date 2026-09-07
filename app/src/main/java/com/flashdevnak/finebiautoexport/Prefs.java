package com.flashdevnak.finebiautoexport;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "finebi_auto_export";
    public static final String ENABLED = "enabled";
    public static final String SERVICE_STATE = "service_state";
    public static final String MESSAGE = "message";
    public static final String BACKEND_UPDATE = "backend_update";
    public static final String LAST_EXPORTED = "last_exported";
    public static final String LAST_EXPORT_URI = "last_export_uri";
    public static final String LAST_EXPORT_NAME = "last_export_name";
    public static final String LAST_EXPORT_AT = "last_export_at";
    public static final String LAST_ERROR = "last_error";
    public static final String SMART_BATTERY = "smart_battery";
    public static final String POLL_MODE = "poll_mode";
    public static final String NEXT_CHECK_AT = "next_check_at";
    public static final String EXPECTED_NEXT_VERSION_AT = "expected_next_version_at";
    public static final String WAITING_FOR_NEW_VERSION = "waiting_for_new_version";

    // Daily-use health telemetry. No auth/session/cookie values are persisted here.
    public static final String LAST_SERVICE_START_AT = "last_service_start_at";
    public static final String LAST_SERVICE_HEARTBEAT_AT = "last_service_heartbeat_at";
    public static final String LAST_POLL_ATTEMPT_AT = "last_poll_attempt_at";
    public static final String LAST_POLL_OK_AT = "last_poll_ok_at";
    public static final String LAST_POLL_VERSION = "last_poll_version";
    public static final String CONSECUTIVE_ERRORS = "consecutive_errors";

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (!p.contains(SMART_BATTERY)) {
            p.edit().putBoolean(SMART_BATTERY, true).apply();
        }
        return p;
    }

    public static void setStatus(Context c, String state, String message) {
        get(c).edit()
                .putString(SERVICE_STATE, state)
                .putString(MESSAGE, message)
                .apply();
    }

    public static void setBackendUpdate(Context c, String update) {
        get(c).edit().putString(BACKEND_UPDATE, update).apply();
    }

    public static void setExport(Context c, String update, String uri, String name) {
        get(c).edit()
                .putString(LAST_EXPORTED, update)
                .putString(LAST_EXPORT_URI, uri)
                .putString(LAST_EXPORT_NAME, name)
                .putLong(LAST_EXPORT_AT, System.currentTimeMillis())
                .putString(LAST_ERROR, "")
                .apply();
    }

    public static void setError(Context c, String error) {
        get(c).edit().putString(LAST_ERROR, error == null ? "" : error).apply();
    }

    public static void setPollPlan(
            Context c,
            String mode,
            long nextCheckAt,
            long expectedNextVersionAt,
            boolean waitingForNewVersion
    ) {
        get(c).edit()
                .putString(POLL_MODE, mode == null ? "" : mode)
                .putLong(NEXT_CHECK_AT, nextCheckAt)
                .putLong(EXPECTED_NEXT_VERSION_AT, expectedNextVersionAt)
                .putBoolean(WAITING_FOR_NEW_VERSION, waitingForNewVersion)
                .apply();
    }

    public static void markServiceStarted(Context c) {
        long now = System.currentTimeMillis();
        get(c).edit()
                .putLong(LAST_SERVICE_START_AT, now)
                .putLong(LAST_SERVICE_HEARTBEAT_AT, now)
                .apply();
    }

    public static void touchServiceHeartbeat(Context c) {
        get(c).edit()
                .putLong(LAST_SERVICE_HEARTBEAT_AT, System.currentTimeMillis())
                .apply();
    }

    public static void markPollAttempt(Context c) {
        get(c).edit()
                .putLong(LAST_POLL_ATTEMPT_AT, System.currentTimeMillis())
                .apply();
    }

    public static void markPollOk(Context c, String version) {
        get(c).edit()
                .putLong(LAST_POLL_OK_AT, System.currentTimeMillis())
                .putString(LAST_POLL_VERSION, version == null ? "" : version)
                .putInt(CONSECUTIVE_ERRORS, 0)
                .apply();
    }

    public static void setConsecutiveErrors(Context c, int count) {
        get(c).edit().putInt(CONSECUTIVE_ERRORS, Math.max(0, count)).apply();
    }
}
