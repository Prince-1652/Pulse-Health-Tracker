package com.pulse.tracker.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility functions for hardware step sensor checks, runtime permissions,
 * and system settings routing.
 */
public final class PermissionUtils {

    private PermissionUtils() {
        // Utility class
    }

    /**
     * Checks if the physical device has a hardware step counter sensor (Sensor.TYPE_STEP_COUNTER).
     */
    public static boolean isStepCounterSensorAvailable(@NonNull Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            return false;
        }
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    /**
     * Checks if ACTIVITY_RECOGNITION runtime permission is granted.
     * Prior to Android 10 (API 29), this permission was not required at runtime and returns true.
     */
    public static boolean hasActivityRecognitionPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if POST_NOTIFICATIONS runtime permission is granted.
     * Prior to Android 13 (API 33), notification permission was granted at install time and returns true.
     */
    public static boolean hasNotificationPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if all tracking-critical permissions are granted.
     * Activity recognition is strictly required for the hardware step sensor on API 29+.
     */
    public static boolean hasTrackingPermission(@NonNull Context context) {
        return hasActivityRecognitionPermission(context);
    }

    /**
     * Returns true if both activity recognition and notification permissions are granted.
     */
    public static boolean hasAllPermissions(@NonNull Context context) {
        return hasActivityRecognitionPermission(context) && hasNotificationPermission(context);
    }

    /**
     * Returns the array of runtime permissions that currently need to be requested on this OS version.
     */
    @NonNull
    public static String[] getPermissionsToRequest(@NonNull Context context) {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityRecognitionPermission(context)) {
            list.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return list.toArray(new String[0]);
    }

    /**
     * Checks if the user should be shown a rationale dialog/explanation for the specified permission.
     */
    public static boolean shouldShowRationale(@NonNull Activity activity, @NonNull String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    /**
     * Determines whether the permission is denied and the system will not show the permission prompt again
     * (i.e. permanently denied / "Don't ask again" selected).
     *
     * Note: should be called after a request permission flow has completed where the permission was not granted.
     */
    public static boolean isPermissionPermanentlyDenied(@NonNull Activity activity, @NonNull String permission) {
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    /**
     * Creates an Intent to navigate the user directly to the application details settings screen
     * where permissions can be manually toggled.
     */
    @NonNull
    public static Intent createAppSettingsIntent(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        return intent;
    }

    /**
     * Launches the App Settings screen for this application.
     */
    public static void openAppSettings(@NonNull Activity activity) {
        activity.startActivity(createAppSettingsIntent(activity));
    }
}

