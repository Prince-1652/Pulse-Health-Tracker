package com.pulse.tracker.receiver;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.data.repository.WaterRepository;
import com.pulse.tracker.domain.WaterIntakeCalculator;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Manages periodic daytime hydration notifications and dynamic alarm scheduling
 * based on clinical hydration research (EFSA, Mayo Clinic, IOM fluid pacing).
 * Distributes fluid intake between 8:00 AM and 10:00 PM to avoid nocturia and sleep disruption.
 */
public final class WaterReminderManager {

    private static final String TAG = "WaterReminderManager";

    public static final String CHANNEL_ID = "pulse_water_channel";
    public static final String CHANNEL_NAME = "Hydration Reminders";
    public static final int NOTIFICATION_ID = 2001;

    // Daytime schedule bounds: 8:00 AM to 10:00 PM
    public static final int START_HOUR = 8;
    public static final int END_HOUR = 22;
    public static final int SERVING_SIZE_ML = 250;
    public static final int MIN_INTERVAL_MINUTES = 60;
    public static final int MAX_INTERVAL_MINUTES = 150;

    private WaterReminderManager() {}

    /**
     * Initializes the notification channel on Android 8.0+.
     */
    public static void createNotificationChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Periodic daytime hydration reminders");
            channel.setShowBadge(false);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Computes the epoch millisecond timestamp for the next dynamically scheduled reminder.
     * Backwards-compatible overload using default state.
     */
    public static long getNextReminderTimeMillis() {
        return calculateNextReminderMillis(Calendar.getInstance(), 0, 2500);
    }

    /**
     * Computes the epoch millisecond timestamp for the next dynamically scheduled reminder
     * using current intake and resolved target.
     */
    public static long getNextReminderTimeMillis(@NonNull Context context) {
        int currentIntake = WaterRepository.getTodayIntakeMl(context);
        int targetMl = resolveTodayTargetMl(context);
        return calculateNextReminderMillis(Calendar.getInstance(), currentIntake, targetMl);
    }

    /**
     * Calculates the exact next trigger time in epoch millis based on clinical hydration pacing:
     * - Daytime window: 8:00 AM to 10:00 PM (clamped between 60m and 150m)
     * - Nighttime buffer: after 10:00 PM or before 8:00 AM schedules to 8:00 AM
     * - Goal reached: schedules to 8:00 AM next day
     */
    public static long calculateNextReminderMillis(@NonNull Calendar now, int currentIntake, int dailyGoal) {
        int hour = now.get(Calendar.HOUR_OF_DAY);

        // If goal already reached or exceeded for today, silence until tomorrow morning
        if (dailyGoal > 0 && currentIntake >= dailyGoal) {
            return getMorningTimestamp(now, true);
        }

        // Pre-daytime (before 8:00 AM) -> trigger at 8:00 AM today
        if (hour < START_HOUR) {
            return getMorningTimestamp(now, false);
        }

        // Post-daytime (at or after 10:00 PM) -> trigger at 8:00 AM tomorrow
        if (hour >= END_HOUR) {
            return getMorningTimestamp(now, true);
        }

        // Within daytime window [8:00 AM, 10:00 PM)
        int remainingMl = Math.max(0, dailyGoal - currentIntake);
        int remainingServings = (int) Math.ceil((double) remainingMl / (double) SERVING_SIZE_ML);
        if (remainingServings <= 0) {
            return getMorningTimestamp(now, true);
        }

        // Calculate remaining waking milliseconds until 22:00 today
        Calendar endOfDay = (Calendar) now.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, END_HOUR);
        endOfDay.set(Calendar.MINUTE, 0);
        endOfDay.set(Calendar.SECOND, 0);
        endOfDay.set(Calendar.MILLISECOND, 0);

        long remainingMillis = Math.max(0, endOfDay.getTimeInMillis() - now.getTimeInMillis());
        double remainingHours = (double) remainingMillis / (1000.0 * 60.0 * 60.0);

        // Dynamic interval = remaining waking hours / remaining servings
        double intervalMinutesExact = (remainingHours / (double) remainingServings) * 60.0;
        long intervalMinutes = Math.round(intervalMinutesExact);
        intervalMinutes = Math.max(MIN_INTERVAL_MINUTES, Math.min(MAX_INTERVAL_MINUTES, intervalMinutes));

        long nextTrigger = now.getTimeInMillis() + (intervalMinutes * 60L * 1000L);

        // If next trigger lands at or past 22:00, clamp to tomorrow morning to avoid sleep disruption
        if (nextTrigger >= endOfDay.getTimeInMillis()) {
            return getMorningTimestamp(now, true);
        }

        return nextTrigger;
    }

    private static long getMorningTimestamp(@NonNull Calendar base, boolean tomorrow) {
        Calendar morning = (Calendar) base.clone();
        if (tomorrow) {
            morning.add(Calendar.DAY_OF_YEAR, 1);
        }
        morning.set(Calendar.HOUR_OF_DAY, START_HOUR);
        morning.set(Calendar.MINUTE, 0);
        morning.set(Calendar.SECOND, 0);
        morning.set(Calendar.MILLISECOND, 0);
        return morning.getTimeInMillis();
    }

    /**
     * Schedules the next periodic daytime reminder alarm dynamically.
     */
    public static void scheduleNextReminder(@NonNull Context context) {
        if (!WaterRepository.isRemindersEnabled(context)) {
            Log.d(TAG, "Water reminders disabled; skipping alarm schedule");
            return;
        }

        createNotificationChannel(context);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.w(TAG, "AlarmManager unavailable");
            return;
        }

        Intent intent = new Intent(context, WaterReminderReceiver.class);
        intent.setAction(WaterReminderReceiver.ACTION_REMINDER_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long nextTrigger = getNextReminderTimeMillis(context);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
            }
            Log.i(TAG, "Scheduled next water reminder for timestamp " + nextTrigger);
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule water reminder alarm: " + e.getMessage(), e);
        }
    }

    /**
     * Cancels any active water reminder alarms.
     */
    public static void cancelReminders(@NonNull Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, WaterReminderReceiver.class);
        intent.setAction(WaterReminderReceiver.ACTION_REMINDER_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    /**
     * Calculates today's scientific target for the user synchronously from SettingsRepository
     * without blocking Room database queries on the main/receiver thread.
     */
    public static int resolveTodayTargetMl(@NonNull Context context) {
        SettingsRepository settings = SettingsRepository.getInstance(context);
        double height = settings.getHeightCm();
        double weight = settings.getWeightKg();
        int age = settings.getAge();
        SettingsRepository.Gender gender = settings.getGender();

        return WaterIntakeCalculator.calculateTarget(weight, height, age, gender, 0L);
    }

    /**
     * Structure holding evidence-based title and body copy.
     */
    public static final class EvidenceMessage {
        @NonNull public final String title;
        @NonNull public final String body;

        public EvidenceMessage(@NonNull String title, @NonNull String body) {
            this.title = title;
            this.body = body;
        }
    }

    /**
     * Resolves evidence-based notification copy adapted to current time-of-day.
     */
    @NonNull
    public static EvidenceMessage resolveEvidenceMessage(@NonNull Context context, int currentMl,
                                                          int targetMl, int hourOfDay) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());
        String currentStr = nf.format(currentMl);
        String targetStr = nf.format(targetMl);

        if (currentMl >= targetMl && targetMl > 0) {
            String title = context.getString(R.string.water_notification_goal_reached_title);
            String body = context.getString(R.string.water_notification_goal_reached_body, targetStr);
            return new EvidenceMessage(title, body);
        }

        if (hourOfDay >= 8 && hourOfDay < 12) {
            String title = context.getString(R.string.water_notification_morning_title);
            String body = context.getString(R.string.water_notification_morning_body, currentStr, targetStr);
            return new EvidenceMessage(title, body);
        } else if (hourOfDay >= 12 && hourOfDay < 15) {
            String title = context.getString(R.string.water_notification_midday_title);
            String body = context.getString(R.string.water_notification_midday_body, currentStr, targetStr);
            return new EvidenceMessage(title, body);
        } else if (hourOfDay >= 15 && hourOfDay < 18) {
            String title = context.getString(R.string.water_notification_afternoon_title);
            String body = context.getString(R.string.water_notification_afternoon_body, currentStr, targetStr);
            return new EvidenceMessage(title, body);
        } else {
            String title = context.getString(R.string.water_notification_evening_title);
            String body = context.getString(R.string.water_notification_evening_body, currentStr, targetStr);
            return new EvidenceMessage(title, body);
        }
    }

    /**
     * Builds and posts the interval reminder notification with "+250 ml" and "Open App" actions
     * and evidence-based message copy adapted to the time of day.
     */
    public static void showHydrationNotification(@NonNull Context context, int currentMl, int targetMl) {
        createNotificationChannel(context);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // Content intent: open Pulse directly to Water destination
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra(MainActivity.KEY_ACTIVE_POSITION, MainActivity.POSITION_WATER);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action 1: Quick log +250 ml directly from notification drawer
        Intent addIntent = new Intent(context, WaterReminderReceiver.class);
        addIntent.setAction(WaterReminderReceiver.ACTION_QUICK_ADD_WATER);
        addIntent.putExtra(WaterReminderReceiver.EXTRA_AMOUNT_ML, 250);
        PendingIntent addPendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        EvidenceMessage message = resolveEvidenceMessage(context, currentMl, targetMl, currentHour);

        String addActionTitle = "+250";
        String openActionTitle = "Open Pulse";
        try {
            addActionTitle = context.getString(R.string.water_notification_action_add_250);
            openActionTitle = context.getString(R.string.water_notification_action_open);
        } catch (Exception ignored) {
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setColor(ContextCompat.getColor(context, R.color.pulse_sky_blue))
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_water_drop, addActionTitle, addPendingIntent)
                .addAction(R.drawable.ic_tab_water, openActionTitle, openPendingIntent);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Builds and posts celebratory notification when the daily hydration goal is reached.
     */
    public static void showGoalAchievedNotification(@NonNull Context context, int currentMl, int targetMl) {
        createNotificationChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra(MainActivity.KEY_ACTIVE_POSITION, MainActivity.POSITION_WATER);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        EvidenceMessage message = resolveEvidenceMessage(context, currentMl, targetMl, 12);
        String openActionTitle = "Open App";
        try {
            openActionTitle = context.getString(R.string.water_notification_action_open);
        } catch (Exception ignored) {
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setColor(ContextCompat.getColor(context, R.color.pulse_green))
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message.body))
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_tab_water, openActionTitle, openPendingIntent);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Updates notification with instant tactile confirmation after "+250 ml" quick log.
     */
    public static void showIntakeLoggedNotification(@NonNull Context context, int newTotalMl, int targetMl, int addedMl) {
        createNotificationChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra(MainActivity.KEY_ACTIVE_POSITION, MainActivity.POSITION_WATER);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());
        String totalStr = nf.format(newTotalMl);
        String targetStr = nf.format(targetMl);

        String title = (newTotalMl >= targetMl) ? "Goal achieved!" : "Logged +" + addedMl + " ml";
        String message;
        try {
            message = context.getString(R.string.water_notification_logged_body, totalStr, targetStr);
        } catch (Exception e) {
            message = "Nice work! " + totalStr + " of " + targetStr + " ml reached today.";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setColor(ContextCompat.getColor(context, R.color.pulse_sky_blue))
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(6000L)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Dismisses the active reminder notification.
     */
    public static void cancelNotification(@NonNull Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }
}
