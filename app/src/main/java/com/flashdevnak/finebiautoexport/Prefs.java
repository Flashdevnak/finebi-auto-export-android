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

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
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
}
