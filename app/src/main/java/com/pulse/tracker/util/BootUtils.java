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

    private static volatile Long cachedBootMarker = null;

    private BootUtils() {
    }

    /**
     * Returns a stable 64-bit boot session marker.
     * Prefers Linux kernel boot_id if accessible; falls back to rounded boot time.
     */
    public static long getBootSessionMarker() {
        if (cachedBootMarker != null) {
            return cachedBootMarker;
        }

        synchronized (BootUtils.class) {
            if (cachedBootMarker != null) {
                return cachedBootMarker;
            }

            long marker = readKernelBootId();
            if (marker == 0L) {
                // Fallback: round (wallTime - elapsedRealtime) to 1-minute blocks to absorb NTP drift
                long bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();
                marker = (bootTime / 60_000L) * 60_000L;
            }

            cachedBootMarker = marker;
            return marker;
        }
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

