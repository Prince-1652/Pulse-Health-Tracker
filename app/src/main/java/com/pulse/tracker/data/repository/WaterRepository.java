package com.pulse.tracker.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Repository managing date-keyed daily hydration logs and reminder configuration in SharedPreferences.
 */
public final class WaterRepository {

    public static final String PREFS_NAME = "pulse_water_prefs";
    public static final String KEY_WATER_INTAKE = "water_intake_ml";
    public static final String KEY_WATER_DATE = "water_intake_date";
    public static final String KEY_REMINDERS_ENABLED = "water_reminders_enabled";

    private WaterRepository() {}

    @NonNull
    public static String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static SharedPreferences getPrefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Retrieves today's logged water intake in ml. Automatically resets to 0 if date changed.
     */
    public static int getTodayIntakeMl(@NonNull Context context) {
        SharedPreferences prefs = getPrefs(context);
        String savedDate = prefs.getString(KEY_WATER_DATE, "");
        String today = getTodayDateString();
        if (today.equals(savedDate)) {
            return prefs.getInt(KEY_WATER_INTAKE, 0);
        } else {
            prefs.edit().putString(KEY_WATER_DATE, today).putInt(KEY_WATER_INTAKE, 0).apply();
            return 0;
        }
    }

    /**
     * Adds an amount of water in ml to today's logged intake.
     *
     * @return The updated total intake in ml.
     */
    public static int addWaterIntake(@NonNull Context context, int amountMl) {
        SharedPreferences prefs = getPrefs(context);
        String today = getTodayDateString();
        String savedDate = prefs.getString(KEY_WATER_DATE, "");
        int current = today.equals(savedDate) ? prefs.getInt(KEY_WATER_INTAKE, 0) : 0;
        int updated = Math.max(0, current + amountMl);
        prefs.edit()
                .putString(KEY_WATER_DATE, today)
                .putInt(KEY_WATER_INTAKE, updated)
                .apply();
        return updated;
    }

    /**
     * Directly sets today's logged water intake in ml.
     */
    public static void setWaterIntake(@NonNull Context context, int amountMl) {
        SharedPreferences prefs = getPrefs(context);
        prefs.edit()
                .putString(KEY_WATER_DATE, getTodayDateString())
                .putInt(KEY_WATER_INTAKE, Math.max(0, amountMl))
                .apply();
    }

    /**
     * Resets today's water intake back to 0 ml.
     */
    public static void resetTodayWater(@NonNull Context context) {
        setWaterIntake(context, 0);
    }

    /**
     * Returns whether interval water reminders are enabled. Defaults to true.
     */
    public static boolean isRemindersEnabled(@NonNull Context context) {
        return getPrefs(context).getBoolean(KEY_REMINDERS_ENABLED, true);
    }

    /**
     * Updates interval water reminder preference.
     */
    public static void setRemindersEnabled(@NonNull Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply();
    }
}
