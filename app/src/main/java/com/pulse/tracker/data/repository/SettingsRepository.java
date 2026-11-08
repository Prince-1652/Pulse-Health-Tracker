package com.pulse.tracker.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Repository wrapping SharedPreferences for user profile and app preferences.
 * All canonical internal biometrics are stored in metric units (cm, kg).
 * Inputs are validated before saving.
 */
public class SettingsRepository {

    public static final String PREFS_NAME = "pulse_preferences";

    // Keys
    public static final String KEY_USER_NAME = "pref_user_name";
    public static final String KEY_HEIGHT_CM = "pref_height_cm";
    public static final String KEY_WEIGHT_KG = "pref_weight_kg";
    public static final String KEY_AGE = "pref_age";
    public static final String KEY_GENDER = "pref_gender";
    public static final String KEY_STRIDE_LENGTH_CM = "pref_stride_length_cm";
    public static final String KEY_DAILY_STEP_TARGET = "pref_daily_step_target";
    public static final String KEY_UNIT_SYSTEM = "pref_unit_system";
    public static final String KEY_TRACKING_LATENCY = "pref_tracking_latency";
    public static final String KEY_TRACKING_ENABLED = "pref_tracking_enabled";
    public static final String KEY_TRACKING_PAUSED = "pref_tracking_paused";
    public static final String KEY_NOTIFICATION_ENABLED = "pref_notification_enabled";
    public static final String KEY_ONBOARDING_COMPLETED = "pref_onboarding_completed";

    // Defaults
    public static final String DEFAULT_USER_NAME = "Prince";
    public static final double DEFAULT_HEIGHT_CM = 170.0;
    public static final double DEFAULT_WEIGHT_KG = 70.0;
    public static final int DEFAULT_AGE = 30;
    public static final long DEFAULT_STEP_GOAL = 10000L;
    public static final double STRIDE_HEIGHT_RATIO = 0.415;

    // Validation boundaries
    public static final double MIN_HEIGHT_CM = 50.0;
    public static final double MAX_HEIGHT_CM = 260.0;
    public static final double MIN_WEIGHT_KG = 20.0;
    public static final double MAX_WEIGHT_KG = 350.0;
    public static final int MIN_AGE = 1;
    public static final int MAX_AGE = 125;
    public static final double MIN_STRIDE_CM = 20.0;
    public static final double MAX_STRIDE_CM = 180.0;
    public static final long MIN_DAILY_GOAL = 100L;
    public static final long MAX_DAILY_GOAL = 150000L;

    public enum Gender {
        MALE,
        FEMALE,
        UNSPECIFIED
    }

    public enum UnitSystem {
        METRIC,
        IMPERIAL
    }

    public enum TrackingLatency {
        BATTERY_EFFICIENT,
        FASTER_UPDATES
    }

    public interface OnSettingsChangeListener {
        void onSettingChanged(@NonNull String key);
    }

    private static volatile SettingsRepository INSTANCE;

    private final SharedPreferences preferences;
    private final Set<OnSettingsChangeListener> listeners = new CopyOnWriteArraySet<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener internalListener;

    public SettingsRepository(@NonNull SharedPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        this.internalListener = (sharedPreferences, key) -> {
            if (key != null) {
                notifyListeners(key);
            }
        };
        this.preferences.registerOnSharedPreferenceChangeListener(this.internalListener);
    }

    public static SettingsRepository getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (SettingsRepository.class) {
                if (INSTANCE == null) {
                    SharedPreferences prefs = context.getApplicationContext()
                            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    INSTANCE = new SettingsRepository(prefs);
                }
            }
        }
        return INSTANCE;
    }

    public void registerListener(@NonNull OnSettingsChangeListener listener) {
        listeners.add(listener);
    }

    public void unregisterListener(@NonNull OnSettingsChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(@NonNull String key) {
        for (OnSettingsChangeListener listener : listeners) {
            listener.onSettingChanged(key);
        }
    }

    // Height (cm)
    public double getHeightCm() {
        return preferences.getFloat(KEY_HEIGHT_CM, (float) DEFAULT_HEIGHT_CM);
    }

    public boolean setHeightCm(double heightCm) {
        if (heightCm < MIN_HEIGHT_CM || heightCm > MAX_HEIGHT_CM) {
            return false;
        }
        preferences.edit().putFloat(KEY_HEIGHT_CM, (float) heightCm).apply();
        return true;
    }

    // Weight (kg)
    public double getWeightKg() {
        return preferences.getFloat(KEY_WEIGHT_KG, (float) DEFAULT_WEIGHT_KG);
    }

    public boolean setWeightKg(double weightKg) {
        if (weightKg < MIN_WEIGHT_KG || weightKg > MAX_WEIGHT_KG) {
            return false;
        }
        preferences.edit().putFloat(KEY_WEIGHT_KG, (float) weightKg).apply();
        return true;
    }

    // Age
    public int getAge() {
        return preferences.getInt(KEY_AGE, DEFAULT_AGE);
    }

    public boolean setAge(int age) {
        if (age < MIN_AGE || age > MAX_AGE) {
            return false;
        }
        preferences.edit().putInt(KEY_AGE, age).apply();
        return true;
    }

    // Gender
    @NonNull
    public Gender getGender() {
        String name = preferences.getString(KEY_GENDER, Gender.UNSPECIFIED.name());
        try {
            return Gender.valueOf(name);
        } catch (Exception e) {
            return Gender.UNSPECIFIED;
        }
    }

    public boolean setGender(@Nullable Gender gender) {
        if (gender == null) {
            return false;
        }
        preferences.edit().putString(KEY_GENDER, gender.name()).apply();
        return true;
    }

    // Stride length (cm). If not manually calibrated, defaults to height * 0.415.
    public double getStrideLengthCm() {
        float stored = preferences.getFloat(KEY_STRIDE_LENGTH_CM, 0.0f);
        if (stored >= MIN_STRIDE_CM && stored <= MAX_STRIDE_CM) {
            return stored;
        }
        return getHeightCm() * STRIDE_HEIGHT_RATIO;
    }

    public boolean setStrideLengthCm(double strideLengthCm) {
        if (strideLengthCm < MIN_STRIDE_CM || strideLengthCm > MAX_STRIDE_CM) {
            return false;
        }
        preferences.edit().putFloat(KEY_STRIDE_LENGTH_CM, (float) strideLengthCm).apply();
        return true;
    }

    public boolean isManualStride() {
        float stored = preferences.getFloat(KEY_STRIDE_LENGTH_CM, 0.0f);
        return stored >= MIN_STRIDE_CM && stored <= MAX_STRIDE_CM;
    }

    public void clearManualStride() {
        preferences.edit().remove(KEY_STRIDE_LENGTH_CM).apply();
    }

    // Daily step target
    public long getDailyStepTarget() {
        return preferences.getLong(KEY_DAILY_STEP_TARGET, DEFAULT_STEP_GOAL);
    }

    public boolean setDailyStepTarget(long target) {
        if (target < MIN_DAILY_GOAL || target > MAX_DAILY_GOAL) {
            return false;
        }
        preferences.edit().putLong(KEY_DAILY_STEP_TARGET, target).apply();
        return true;
    }

    // Unit system
    @NonNull
    public UnitSystem getUnitSystem() {
        String name = preferences.getString(KEY_UNIT_SYSTEM, UnitSystem.METRIC.name());
        try {
            return UnitSystem.valueOf(name);
        } catch (Exception e) {
            return UnitSystem.METRIC;
        }
    }

    public boolean setUnitSystem(@Nullable UnitSystem unitSystem) {
        if (unitSystem == null) {
            return false;
        }
        preferences.edit().putString(KEY_UNIT_SYSTEM, unitSystem.name()).apply();
        return true;
    }

    // Tracking latency preference
    @NonNull
    public TrackingLatency getTrackingLatency() {
        String name = preferences.getString(KEY_TRACKING_LATENCY, TrackingLatency.BATTERY_EFFICIENT.name());
        try {
            return TrackingLatency.valueOf(name);
        } catch (Exception e) {
            return TrackingLatency.BATTERY_EFFICIENT;
        }
    }

    public boolean setTrackingLatency(@Nullable TrackingLatency latency) {
        if (latency == null) {
            return false;
        }
        preferences.edit().putString(KEY_TRACKING_LATENCY, latency.name()).apply();
        return true;
    }

    // Tracking enabled
    public boolean isTrackingEnabled() {
        return preferences.getBoolean(KEY_TRACKING_ENABLED, false);
    }

    public void setTrackingEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_TRACKING_ENABLED, enabled).apply();
    }

    // Tracking paused
    public boolean isTrackingPaused() {
        return preferences.getBoolean(KEY_TRACKING_PAUSED, false);
    }

    public void setTrackingPaused(boolean paused) {
        preferences.edit().putBoolean(KEY_TRACKING_PAUSED, paused).apply();
    }

    // Notification enabled (defaults to true)
    public boolean isNotificationEnabled() {
        return preferences.getBoolean(KEY_NOTIFICATION_ENABLED, true);
    }

    public void setNotificationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply();
    }

    // User display name
    @NonNull
    public String getUserName() {
        return preferences.getString(KEY_USER_NAME, DEFAULT_USER_NAME);
    }

    public void setUserName(@Nullable String name) {
        String cleanName = (name != null && !name.trim().isEmpty()) ? name.trim() : DEFAULT_USER_NAME;
        preferences.edit().putString(KEY_USER_NAME, cleanName).apply();
    }

    // Onboarding status
    public boolean hasCompletedOnboarding() {
        return preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }

    // Unit conversion helpers
    public static double cmToInches(double cm) {
        return cm / 2.54;
    }

    public static double inchesToCm(double inches) {
        return inches * 2.54;
    }

    public static double kgToLbs(double kg) {
        return kg / 0.45359237;
    }

    public static double lbsToKg(double lbs) {
        return lbs * 0.45359237;
    }
}

