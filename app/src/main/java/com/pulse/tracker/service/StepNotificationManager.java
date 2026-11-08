package com.pulse.tracker.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Manages the ongoing, non-dismissible notification for StepTrackingService.
 * Configures the low-importance notification channel and handles dynamic
 * step count, goal remaining, and pause/resume action updates.
 */
public class StepNotificationManager {

    public static final String CHANNEL_ID = "pulse_tracking_channel";
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
            int importance = NotificationManager.IMPORTANCE_LOW;

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
     * Builds the ongoing notification reflecting the current tracking state and step progress.
     */
    @NonNull
    public Notification buildNotification(long currentSteps, long goal, boolean isPaused) {
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault());
        String formattedSteps = numberFormat.format(Math.max(0, currentSteps));

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

        if (isPaused) {
            title = "Pulse • " + pausedStatus;
            contentText = formattedSteps + " steps today • Paused";
        } else {
            title = "Pulse • " + activeStatus;
            if (goal > 0 && currentSteps >= goal) {
                contentText = formattedSteps + " steps today • " + goalReached;
            } else if (goal > 0) {
                long remaining = Math.max(0, goal - currentSteps);
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_footprints)
                .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.pulse_primary))
                .setContentTitle(title)
                .setContentText(contentText)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        // Action button: Pause / Resume
        Intent serviceActionIntent = new Intent(context, StepTrackingService.class);
        serviceActionIntent.setAction(isPaused ? StepTrackingService.ACTION_RESUME : StepTrackingService.ACTION_PAUSE);
        PendingIntent actionPendingIntent = PendingIntent.getService(
                context,
                REQUEST_CODE_ACTION,
                serviceActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String actionTitle = isPaused ? "Resume" : "Pause";
        builder.addAction(0, actionTitle, actionPendingIntent);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        return notification;
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
                .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.pulse_primary))
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
        if (!isNotificationEnabled) {
            cancelNotification();
            return;
        }
        try {
            Notification notification = buildNotification(currentSteps, goal, isPaused);
            notificationManager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            // Notification permission denied or revoked
        }
    }
}
