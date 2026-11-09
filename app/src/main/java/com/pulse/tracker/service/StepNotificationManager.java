package com.pulse.tracker.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.util.UnitFormatter;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Manages the ongoing, non-dismissible standard notification for StepTrackingService.
 * Clean, standard Android notification displaying step progress, daily goal remaining,
 * secondary metrics (distance & calories), and quick pause/resume controls.
 */
public class StepNotificationManager {

    public static final String CHANNEL_ID = "pulse_tracking_channel_v2";
    public static final String SILENT_CHANNEL_ID = "pulse_silent_channel";
    public static final int NOTIFICATION_ID = 1001;
    public static final int REQUEST_CODE_MAIN = 101;
    public static final int REQUEST_CODE_ACTION = 102;

    private final Context context;
    private final NotificationManagerCompat notificationManager;

    public StepNotificationManager(@NonNull Context context) {
        this.context = Objects.requireNonNull(context.getApplicationContext());
        this.notificationManager = NotificationManagerCompat.from(this.context);
    }

    /**
     * Creates the low-importance and silent notification channels on Android O (API 26) and higher.
     */
    public void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager systemManager = context.getSystemService(NotificationManager.class);
            if (systemManager == null) {
                return;
            }

            // Cleanup legacy channel if present
            try {
                systemManager.deleteNotificationChannel("pulse_tracking_channel");
            } catch (Exception ignored) {
            }

            // 1) Visible ongoing tracking channel
            String name;
            String description;
            try {
                name = context.getString(R.string.notification_channel_name);
                description = context.getString(R.string.notification_channel_description);
            } catch (Exception e) {
                name = "Step Tracking";
                description = "Continuous background pedometer and daily activity updates";
            }
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            systemManager.createNotificationChannel(channel);

            // 2) Silent minimal tracking channel
            NotificationChannel silentChannel = new NotificationChannel(
                    SILENT_CHANNEL_ID,
                    "Step Tracking (Silent)",
                    NotificationManager.IMPORTANCE_MIN
            );
            silentChannel.setDescription("Continuous silent background pedometer service");
            silentChannel.setShowBadge(false);
            silentChannel.enableVibration(false);
            silentChannel.setSound(null, null);
            systemManager.createNotificationChannel(silentChannel);
        }
    }

    /**
     * Builds standard ongoing notification reflecting current steps, daily goal, and pause state.
     */
    @NonNull
    public Notification buildNotification(long currentSteps, long goal, boolean isPaused) {
        double defaultStride = 76.0;
        try {
            SettingsRepository settings = SettingsRepository.getInstance(context);
            defaultStride = settings.getStrideLengthCm();
        } catch (Exception ignored) {
        }
        double estimatedDistanceMeters = (Math.max(0, currentSteps) * defaultStride) / 100.0;
        double estimatedCalories = Math.max(0, currentSteps) * 0.04;
        return buildNotification(currentSteps, goal, estimatedDistanceMeters, estimatedCalories, isPaused);
    }

    /**
     * Compatibility overload accepting optional token (delegates to standard notification).
     */
    @NonNull
    public Notification buildNotification(long currentSteps, long goal, boolean isPaused,
                                          @Nullable Object unusedToken) {
        return buildNotification(currentSteps, goal, isPaused);
    }

    /**
     * Builds standard ongoing notification with rich secondary metrics (distance and calories).
     */
    @NonNull
    public Notification buildNotification(long currentSteps, long goal, double distanceMeters,
                                          double caloriesKcal, boolean isPaused) {
        long safeSteps = Math.max(0, currentSteps);
        long safeGoal = Math.max(0, goal);
        double safeDistance = Math.max(0.0, distanceMeters);
        double safeCalories = Math.max(0.0, caloriesKcal);

        NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault());
        String formattedSteps = numberFormat.format(safeSteps);

        String pausedStatus = "Tracking paused";
        String activeStatus = "Tracking active";
        String goalReached = "Goal Reached";
        try {
            pausedStatus = context.getString(R.string.status_tracking_paused);
            activeStatus = context.getString(R.string.status_tracking_active);
            goalReached = context.getString(R.string.goal_reached);
        } catch (Exception ignored) {
        }

        String title;
        String contentText;
        boolean isGoalReached = (safeGoal > 0 && safeSteps >= safeGoal);

        if (isPaused) {
            title = "Pulse • " + pausedStatus;
            contentText = formattedSteps + " steps today • Paused";
        } else {
            title = "Pulse • " + activeStatus;
            if (isGoalReached) {
                contentText = formattedSteps + " steps today • " + goalReached;
            } else if (safeGoal > 0) {
                long remaining = Math.max(0, safeGoal - safeSteps);
                String formattedRemaining = numberFormat.format(remaining);
                contentText = formattedSteps + " steps today • " + formattedRemaining + " remaining";
            } else {
                contentText = formattedSteps + " steps today";
            }
        }

        // Tap action: open MainActivity
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_MAIN,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int maxProgress = (safeGoal > 0) ? (int) Math.min(Integer.MAX_VALUE, safeGoal) : 10000;
        int currentProgress = (int) Math.min(maxProgress, Math.max(0, safeSteps));

        String formattedDistance = UnitFormatter.formatDistance(safeDistance, SettingsRepository.UnitSystem.METRIC);
        String formattedCalories = safeCalories > 0 ? UnitFormatter.formatCalories(safeCalories) : "0 kcal";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_footprints)
                .setColor(ContextCompat.getColor(context, R.color.pulse_green))
                .setContentTitle(title)
                .setContentText(contentText)
                .setSubText(formattedDistance + " • " + formattedCalories)
                .setProgress(maxProgress, currentProgress, false)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        return notification;
    }

    /**
     * Compatibility overload accepting optional token (delegates to standard notification).
     */
    @NonNull
    public Notification buildNotification(long currentSteps, long goal, double distanceMeters,
                                          double caloriesKcal, boolean isPaused,
                                          @Nullable Object unusedToken) {
        return buildNotification(currentSteps, goal, distanceMeters, caloriesKcal, isPaused);
    }

    /**
     * Builds a silent minimal ongoing notification (IMPORTANCE_MIN) for background service persistence
     * when the user disables visible tracking notifications.
     */
    @NonNull
    public Notification buildSilentNotification(long currentSteps, long goal) {
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault());
        String formattedSteps = numberFormat.format(Math.max(0, currentSteps));

        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_MAIN,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, SILENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_footprints)
                .setColor(ContextCompat.getColor(context, R.color.pulse_green))
                .setContentTitle("Pulse • Tracking active")
                .setContentText(formattedSteps + " steps")
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        return notification;
    }

    /**
     * Cancels the notification completely from NotificationManager.
     */
    public void cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {
        }
    }

    /**
     * Posts an updated notification to NotificationManager.
     */
    public void updateNotification(long currentSteps, long goal, boolean isPaused) {
        updateNotification(currentSteps, goal, isPaused, true);
    }

    /**
     * Posts an updated notification to NotificationManager.
     * If isNotificationEnabled is false, cancels the notification completely.
     */
    public void updateNotification(long currentSteps, long goal, boolean isPaused, boolean isNotificationEnabled) {
        double defaultStride = 76.0;
        try {
            SettingsRepository settings = SettingsRepository.getInstance(context);
            defaultStride = settings.getStrideLengthCm();
        } catch (Exception ignored) {
        }
        double estimatedDistanceMeters = (Math.max(0, currentSteps) * defaultStride) / 100.0;
        double estimatedCalories = Math.max(0, currentSteps) * 0.04;
        updateNotification(currentSteps, goal, estimatedDistanceMeters, estimatedCalories, isPaused, isNotificationEnabled);
    }

    /**
     * Posts an updated notification with full secondary metrics.
     */
    public void updateNotification(long currentSteps, long goal, double distanceMeters,
                                   double caloriesKcal, boolean isPaused, boolean isNotificationEnabled) {
        if (!isNotificationEnabled) {
            cancelNotification();
            return;
        }
        try {
            Notification notification = buildNotification(currentSteps, goal, distanceMeters, caloriesKcal, isPaused);
            notificationManager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            // Notification permission denied or revoked
        } catch (Throwable t) {
            try {
                Notification fallback = buildSilentNotification(currentSteps, goal);
                notificationManager.notify(NOTIFICATION_ID, fallback);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Compatibility overload accepting optional token (delegates to standard notification).
     */
    public void updateNotification(long currentSteps, long goal, double distanceMeters,
                                   double caloriesKcal, boolean isPaused, boolean isNotificationEnabled,
                                   @Nullable Object unusedToken) {
        updateNotification(currentSteps, goal, distanceMeters, caloriesKcal, isPaused, isNotificationEnabled);
    }
}
