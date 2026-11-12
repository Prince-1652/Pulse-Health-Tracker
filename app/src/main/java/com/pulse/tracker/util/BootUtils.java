package com.pulse.tracker.util;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.UUID;

/**
 * Utility providing a robust boot-session identifier that remains constant
 * throughout a device's boot lifecycle and changes uniquely upon reboot.
 */
public final class BootUtils {

    public static final long BOOT_DRIFT_TOLERANCE_MS = 30_000L;

    private static volatile Long cachedBootMarker = null;

    private BootUtils() {
    }

    /**
     * Returns a stable 64-bit boot session marker.
     * Prefers Linux kernel boot_id if accessible; falls back to stable boot time.
     */
    public static long getBootSessionMarker() {
        return getBootSessionMarker(0L);
    }

    /**
     * Returns a stable 64-bit boot session marker, reconciling with an existing
     * persisted marker if within fuzzy tolerance.
     */
    public static long getBootSessionMarker(long persistedBootMarker) {
        if (cachedBootMarker != null) {
            return cachedBootMarker;
        }

        synchronized (BootUtils.class) {
            if (cachedBootMarker != null) {
                return cachedBootMarker;
            }

            long marker = readKernelBootId();
            if (marker == 0L) {
                // Fallback: check against persisted marker with 30s fuzzy tolerance to absorb clock drift / restarts
                long bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();
                if (persistedBootMarker != 0L && Math.abs(bootTime - persistedBootMarker) <= BOOT_DRIFT_TOLERANCE_MS) {
                    marker = persistedBootMarker;
                } else {
                    marker = bootTime;
                }
            }

            cachedBootMarker = marker;
            return marker;
        }
    }

    /**
     * Determines whether two boot markers belong to the same boot session within fuzzy tolerance.
     */
    public static boolean isSameBootSession(long marker1, long marker2) {
        if (marker1 == marker2) {
            return true;
        }
        if (marker1 == 0L || marker2 == 0L) {
            return false;
        }
        return Math.abs(marker1 - marker2) <= BOOT_DRIFT_TOLERANCE_MS;
    }

    private static long readKernelBootId() {
        try {
            File bootIdFile = new File("/proc/sys/kernel/random/boot_id");
            if (bootIdFile.exists() && bootIdFile.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(bootIdFile))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        UUID uuid = UUID.fromString(line.trim());
                        return uuid.getMostSignificantBits();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    /**
     * Clears cached boot marker (primarily for testing).
     */
    public static void resetCacheForTesting() {
        cachedBootMarker = null;
    }
}

