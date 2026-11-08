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
 * Manages periodic daytime hydration notifications and alarm scheduling.
 * Reminders occur every 2 hours between 8:00 AM and 10:00 PM when the user is below daily goal.
 */
public final class WaterReminderManager {

    private static final String TAG = "WaterReminderManager";

    public static final String CHANNEL_ID = "pulse_water_channel";
    public static final String CHANNEL_NAME = "Hydration Reminders";
    public static final int NOTIFICATION_ID = 2001;

    // Daytime schedule bounds: 8:00 AM to 10:00 PM in 2-hour increments
    public static final int START_HOUR = 8;
    public static final int END_HOUR = 22;
    public static final int INTERVAL_HOURS = 2;

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
     * Computes the epoch millisecond timestamp for the next scheduled daytime slot.
     * Slots: 8:00, 10:00, 12:00, 14:00, 16:00, 18:00, 20:00, 22:00.
     */
    public static long getNextReminderTimeMillis() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);

        int[] slots = {8, 10, 12, 14, 16, 18, 20, 22};
        for (int slot : slots) {
            if (slot > hour || (slot == hour && minute < 1)) {
                Calendar target = Calendar.getInstance();
                target.set(Calendar.HOUR_OF_DAY, slot);
                target.set(Calendar.MINUTE, 0);
                target.set(Calendar.SECOND, 0);
                target.set(Calendar.MILLISECOND, 0);
                return target.getTimeInMillis();
            }
        }

        // Past 22:00 -> schedule for 8:00 AM tomorrow
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        tomorrow.set(Calendar.HOUR_OF_DAY, START_HOUR);
        tomorrow.set(Calendar.MINUTE, 0);
        tomorrow.set(Calendar.SECOND, 0);
        tomorrow.set(Calendar.MILLISECOND, 0);
        return tomorrow.getTimeInMillis();
    }

    /**
     * Schedules the next periodic daytime reminder alarm.
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

        long nextTrigger = getNextReminderTimeMillis();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
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
     * Calculates today's scientific target for the user.
     */
    public static int resolveTodayTargetMl(@NonNull Context context) {
        SettingsRepository settings = SettingsRepository.getInstance(context);
        double height = settings.getHeightCm();
        double weight = settings.getWeightKg();
        int age = settings.getAge();
        SettingsRepository.Gender gender = settings.getGender();

        long steps = 0L;
        try {
            DailyMetricsEntity todayEntity = MetricsRepository.getInstance(context)
                    .getMetricsForDateSync(WaterRepository.getTodayDateString());
            if (todayEntity != null) {
                steps = todayEntity.getSteps();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read today's steps: " + e.getMessage());
        }

        return WaterIntakeCalculator.calculateTarget(weight, height, age, gender, steps);
    }

    /**
     * Builds and posts the interval reminder notification with "+250 ml" and "Open Pulse" actions.
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

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());
        String currentStr = nf.format(currentMl);
        String targetStr = nf.format(targetMl);

        String title = "Time to hydrate";
        String message = "You have logged " + currentStr + " ml of your " + targetStr + " ml goal today. Drink a glass of water!";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setColor(ContextCompat.getColor(context, R.color.pulse_sky_blue))
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_water_drop, "+250 ml", addPendingIntent)
                .addAction(R.drawable.ic_tab_water, "Open Pulse", openPendingIntent);

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
        String message = "Today: " + totalStr + " of " + targetStr + " ml reached.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setColor(ContextCompat.getColor(context, R.color.pulse_sky_blue))
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(6000L) // Auto-dismiss after 6 seconds
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
