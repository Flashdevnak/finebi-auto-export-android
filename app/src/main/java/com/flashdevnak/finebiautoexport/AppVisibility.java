package com.flashdevnak.finebiautoexport;

/**
 * Process-local UI visibility used only to decide whether recovery may return the user
 * to FineBI Auto Export after the service temporarily opened Flashlink.
 * No auth/session data is stored here.
 */
public final class AppVisibility {
    private static volatile boolean dailyVisible;

    private AppVisibility() {}

    public static void setDailyVisible(boolean visible) {
        dailyVisible = visible;
    }

    public static boolean isDailyVisible() {
        return dailyVisible;
    }
}
