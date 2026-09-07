package com.flashdevnak.finebiautoexport;

import java.util.Map;

public final class SessionStore {
    private static volatile String authorization;
    private static volatile String sessionId;
    private static volatile long capturedAt;

    private SessionStore() {}

    public static synchronized boolean capture(Map<String, String> headers) {
        if (headers == null) return false;

        String auth = find(headers, "Authorization");
        String sid = find(headers, "sessionId");

        if (auth == null || auth.trim().isEmpty()) {
            return false;
        }

        authorization = auth;
        if (sid != null && !sid.trim().isEmpty()) {
            sessionId = sid;
        }

        if (authorization != null && !authorization.isEmpty()
                && sessionId != null && !sessionId.isEmpty()) {
            capturedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public static synchronized void clear() {
        authorization = null;
        sessionId = null;
        capturedAt = 0L;
    }

    public static boolean isReady() {
        return authorization != null && !authorization.isEmpty()
                && sessionId != null && !sessionId.isEmpty();
    }

    public static String authorization() {
        return authorization;
    }

    public static String sessionId() {
        return sessionId;
    }

    public static long capturedAt() {
        return capturedAt;
    }

    private static String find(Map<String, String> headers, String target) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(target)) {
                return e.getValue();
            }
        }
        return null;
    }
}
