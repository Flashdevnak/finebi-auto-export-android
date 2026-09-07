package com.flashdevnak.finebiautoexport;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Battery-aware polling for a source that normally publishes a new th_update_time every ~30 minutes.
 *
 * Key rule: once the next version is due, FAST mode never gives up just because the clock moved on.
 * It remains at 10s until FineBI actually exposes a version newer than the last successfully exported one.
 */
public final class SmartBatteryPolicy {
    public static final long FAST_POLL_MS = 10_000L;
    public static final long ECO_POLL_MS = 5 * 60_000L;
    public static final long FIRST_SYNC_POLL_MS = 60_000L;
    public static final long EXPECTED_CADENCE_MS = 30 * 60_000L;
    public static final long FAST_LEAD_MS = 5 * 60_000L;

    public static final class Plan {
        public final String mode;
        public final long delayMs;
        public final long expectedNextAtMs;
        public final boolean waitingForNewVersion;

        Plan(String mode, long delayMs, long expectedNextAtMs, boolean waitingForNewVersion) {
            this.mode = mode;
            this.delayMs = delayMs;
            this.expectedNextAtMs = expectedNextAtMs;
            this.waitingForNewVersion = waitingForNewVersion;
        }
    }

    private SmartBatteryPolicy() {}

    public static Plan next(String lastSuccessfulVersion, long nowMs) {
        long lastMs = parseFineBiTime(lastSuccessfulVersion);
        if (lastMs <= 0L) {
            return new Plan("SYNC", FIRST_SYNC_POLL_MS, 0L, false);
        }

        long expected = lastMs + EXPECTED_CADENCE_MS;
        long fastStart = expected - FAST_LEAD_MS;

        if (nowMs >= fastStart) {
            // Important: keep checking every 10s until a NEW th_update_time is actually exported.
            // There is intentionally no fixed end time here.
            return new Plan("FAST", FAST_POLL_MS, expected, true);
        }

        long untilFast = fastStart - nowMs;
        long delay = Math.min(ECO_POLL_MS, untilFast);
        delay = Math.max(FAST_POLL_MS, delay);
        return new Plan("ECO", delay, expected, false);
    }

    public static long parseFineBiTime(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) return -1L;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            f.setLenient(false);
            Date d = f.parse(value.trim());
            return d == null ? -1L : d.getTime();
        } catch (ParseException e) {
            return -1L;
        }
    }

    public static String formatClock(long timeMs) {
        if (timeMs <= 0L) return "-";
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timeMs));
    }
}
