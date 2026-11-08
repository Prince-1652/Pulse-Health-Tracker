package com.pulse.tracker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.service.StepTrackingService;
import com.pulse.tracker.util.PermissionUtils;

/**
 * BroadcastReceiver listening for Intent.ACTION_BOOT_COMPLETED.
 * Restores the ongoing StepTrackingService after device reboot if the user
 * had previously enabled continuous background tracking and permissions are intact.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }

        Log.i(TAG, "BOOT_COMPLETED received");

        // Reschedule periodic daytime hydration reminders
        WaterReminderManager.scheduleNextReminder(context);

        SettingsRepository settings = SettingsRepository.getInstance(context);
        if (!settings.isTrackingEnabled()) {
            Log.i(TAG, "Tracking was not enabled by user; ignoring boot completion");
            return;
        }

        if (!PermissionUtils.hasActivityRecognitionPermission(context)) {
            Log.w(TAG, "Activity recognition permission missing after boot; cannot restore tracking service");
            return;
        }

        try {
            Intent serviceIntent = new Intent(context, StepTrackingService.class);
            serviceIntent.setAction(StepTrackingService.ACTION_START);
            if (settings.isNotificationEnabled()) {
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.i(TAG, "StepTrackingService started successfully after boot");
        } catch (Exception e) {
            // Catches ForegroundServiceStartNotAllowedException on Android 12+ and SecurityExceptions cleanly
            Log.e(TAG, "Failed to restore StepTrackingService on boot: " + e.getMessage(), e);
        }
    }
}

