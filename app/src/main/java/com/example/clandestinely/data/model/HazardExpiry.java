package com.example.clandestinely.data.model;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Expiry rules for hazard reports (schema section 4.1, agreed with Intan):
 * - a new report lasts 2 hours
 * - each confirmation adds 1 hour
 * - never more than 10 hours after it was made
 * - all reports end at midnight, Malaysia time
 * Same logic as firebase/schema.js. The Firestore rules enforce the 2 h / +1 h / 10 h limits.
 * Works in milliseconds so it runs on every Android version.
 */
public final class HazardExpiry {

    public static final TimeZone MALAYSIA = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
    public static final long HOUR_MS = 60L * 60L * 1000L;
    public static final long DEFAULT_LIFETIME_MS = 2 * HOUR_MS;
    public static final long PER_CONFIRMATION_MS = HOUR_MS;
    public static final long MAX_LIFETIME_MS = 10 * HOUR_MS;

    private HazardExpiry() {
    }

    /** The next 00:00 Malaysia time after {@code createdAtMillis}. */
    public static long nextMidnight(long createdAtMillis) {
        Calendar calendar = Calendar.getInstance(MALAYSIA);
        calendar.setTimeInMillis(createdAtMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }

    /** expiresAt for a brand-new report. */
    public static long initialExpiry(long createdAtMillis) {
        return min(createdAtMillis + DEFAULT_LIFETIME_MS,
                createdAtMillis + MAX_LIFETIME_MS,
                nextMidnight(createdAtMillis));
    }

    /** expiresAt after one more confirmation. */
    public static long extendedExpiry(long createdAtMillis, long currentExpiresAtMillis) {
        return min(currentExpiresAtMillis + PER_CONFIRMATION_MS,
                createdAtMillis + MAX_LIFETIME_MS,
                nextMidnight(createdAtMillis));
    }

    private static long min(long a, long b, long c) {
        return Math.min(a, Math.min(b, c));
    }
}
