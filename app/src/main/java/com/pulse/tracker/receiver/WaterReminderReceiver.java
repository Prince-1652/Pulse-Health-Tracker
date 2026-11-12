package com.pulse.tracker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.pulse.tracker.data.repository.WaterRepository;

import java.util.Calendar;

/**
 * BroadcastReceiver triggered by AlarmManager ticks for interval reminders
 * and by Notification Quick Log action buttons (+250 ml).
 */
public class WaterReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "WaterReminderReceiver";

    public static final String ACTION_REMINDER_TICK = "com.pulse.tracker.action.WATER_REMINDER_TICK";
    public static final String ACTION_QUICK_ADD_WATER = "com.pulse.tracker.action.QUICK_ADD_WATER";
    public static final String EXTRA_AMOUNT_ML = "extra_amount_ml";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "onReceive action: " + action);

        if (ACTION_REMINDER_TICK.equals(action)) {
            handleReminderTick(context);
        } else if (ACTION_QUICK_ADD_WATER.equals(action)) {
            handleQuickAddWater(context, intent);
        }
    }

    private void handleReminderTick(Context context) {
        // 1. Immediately schedule the next dynamic alarm in the chain
        WaterReminderManager.scheduleNextReminder(context);

        if (!WaterRepository.isRemindersEnabled(context)) {
            Log.d(TAG, "Reminders disabled; skipping notification");
            return;
        }

        // 2. Daytime window gate: 8:00 AM to 10:00 PM (end of window is 22:00; do not post at or after 22:00)
        // If alarm fires slightly before 8:00 AM (7:55 - 7:59 AM due to AlarmManager jitter), treat 7:55+ as morning reminder.
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        if (!isDaytimeNotificationWindow(hour, minute)) {
            Log.d(TAG, "Current time (" + hour + ":" + minute + ") is outside daytime window [8, 22); skipping notification");
            return;
        }

        // 3. Check current intake against daily scientific target
        int currentIntake = WaterRepository.getTodayIntakeMl(context);
        int targetMl = WaterReminderManager.resolveTodayTargetMl(context);

        Log.d(TAG, "Current water intake: " + currentIntake + " ml / Target: " + targetMl + " ml");

        // 4. Fire notification: if under target, show dynamic reminder; if reached, show celebratory notification
        if (currentIntake < targetMl) {
            WaterReminderManager.showHydrationNotification(context, currentIntake, targetMl);
        } else {
            Log.i(TAG, "Target reached (" + currentIntake + "/" + targetMl + " ml); celebratory notification");
            WaterReminderManager.showGoalAchievedNotification(context, currentIntake, targetMl);
        }
    }

    static boolean isDaytimeNotificationWindow(int hour, int minute) {
        return (hour == 7 && minute >= 55) || (hour >= WaterReminderManager.START_HOUR && hour < WaterReminderManager.END_HOUR);
    }

    private void handleQuickAddWater(Context context, Intent intent) {
        int amount = intent.getIntExtra(EXTRA_AMOUNT_ML, 250);
        int updatedTotal = WaterRepository.addWaterIntake(context, amount);
        int targetMl = WaterReminderManager.resolveTodayTargetMl(context);

        Log.i(TAG, "Quick logged " + amount + " ml from notification. New total: " + updatedTotal + " ml");

        // Instantly refresh notification drawer with auto-dismissing logged confirmation or celebration
        if (updatedTotal >= targetMl) {
            // Goal reached: silence future reminders with celebratory notification
            WaterReminderManager.showGoalAchievedNotification(context, updatedTotal, targetMl);
        } else {
            // Show 3-second auto-dismissing logged confirmation showing current intake and target
            WaterReminderManager.showIntakeLoggedNotification(context, updatedTotal, targetMl, amount);
        }

        // Dynamically reschedule next alarm based on remaining intake
        WaterReminderManager.scheduleNextReminder(context);
    }
}
