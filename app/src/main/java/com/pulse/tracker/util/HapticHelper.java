package com.pulse.tracker.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Precision tactile feedback helper delivering crisp, instantaneous micro-pulses
 * that bypass system haptic_feedback_enabled=0 restrictions and hardware HAL limitations
 * via direct sonification audio attributes.
 */
public final class HapticHelper {

    private static final String TAG = "HapticHelper";

    public static final long TICK_DURATION_MS = 11L;
    public static final int TICK_AMPLITUDE = 255;

    public static final long MILESTONE_DURATION_MS = 20L;
    public static final int MILESTONE_AMPLITUDE = 255;

    public static final long THROTTLE_WINDOW_MS = 12L;

    private static volatile long lastTickTime = 0L;
    private static volatile Vibrator vibratorInstance = null;

    private static final AudioAttributes SONIFICATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();

    private HapticHelper() {}

    private static Vibrator getVibrator(Context context) {
        if (vibratorInstance == null && context != null) {
            synchronized (HapticHelper.class) {
                if (vibratorInstance == null) {
                    vibratorInstance = (Vibrator) context.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                }
            }
        }
        return vibratorInstance;
    }

    /**
     * Alias for performTick.
     */
    public static void tick(@Nullable Context context) {
        performTick(context, false);
    }

    /**
     * Alias for performMilestone.
     */
    public static void milestone(@Nullable Context context) {
        performMilestone(context);
    }

    /**
     * Performs an ultra-crisp, instantaneous micro-pulse tactile click (11ms at max amplitude).
     * Internally throttled to 12ms to prevent actuator saturation during high-speed flings
     * while capturing every single tick.
     */
    public static void performTick(@Nullable Context context) {
        performTick(context, false);
    }

    /**
     * Performs an ultra-crisp, instantaneous micro-pulse tactile click.
     *
     * @param context Context
     * @param force   If true, bypasses the 12ms throttling window.
     */
    public static void performTick(@Nullable Context context, boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && (now - lastTickTime < THROTTLE_WINDOW_MS)) {
            return;
        }
        lastTickTime = now;

        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createOneShot(TICK_DURATION_MS, TICK_AMPLITUDE);
                vibrator.vibrate(effect, SONIFICATION_ATTRIBUTES);
            } else {
                vibrator.vibrate(TICK_DURATION_MS);
            }
        } catch (Exception e) {
            Log.w(TAG, "performTick failed: " + e.getMessage());
        }
    }

    /**
     * Performs a prominent snap detent / milestone lock-in tactile pulse (20ms at max amplitude).
     */
    public static void performMilestone(@Nullable Context context) {
        lastTickTime = SystemClock.uptimeMillis();
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createOneShot(MILESTONE_DURATION_MS, MILESTONE_AMPLITUDE);
                vibrator.vibrate(effect, SONIFICATION_ATTRIBUTES);
            } else {
                vibrator.vibrate(MILESTONE_DURATION_MS);
            }
        } catch (Exception e) {
            Log.w(TAG, "performMilestone failed: " + e.getMessage());
        }
    }
}

