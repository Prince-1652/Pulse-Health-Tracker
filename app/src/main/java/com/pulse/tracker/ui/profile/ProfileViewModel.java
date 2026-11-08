package com.pulse.tracker.ui.profile;

import android.app.Application;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * ViewModel managing user profile biometrics, stride calibration, tracking latency,
 * and daily goal preferences. Handles real-time validation, metric-canonical unit conversions,
 * and synchronized updates to Room and SharedPreferences.
 */
public class ProfileViewModel extends AndroidViewModel {

    // Validation boundaries per specifications
    public static final double MIN_HEIGHT_CM = 50.0;
    public static final double MAX_HEIGHT_CM = 260.0;
    public static final double MIN_HEIGHT_INCHES = 20.0;
    public static final double MAX_HEIGHT_INCHES = 102.0;

    public static final double MIN_WEIGHT_KG = 20.0;
    public static final double MAX_WEIGHT_KG = 350.0;
    public static final double MIN_WEIGHT_LBS = 44.0;
    public static final double MAX_WEIGHT_LBS = 770.0;

    public static final int MIN_AGE = 5;
    public static final int MAX_AGE = 125;

    public static final double MIN_STRIDE_CM = 20.0;
    public static final double MAX_STRIDE_CM = 160.0;
    public static final double MIN_STRIDE_INCHES = 8.0;
    public static final double MAX_STRIDE_INCHES = 63.0;

    public static final long MIN_STEP_GOAL = 500L;
    public static final long MAX_STEP_GOAL = 100000L;

    private final SettingsRepository settingsRepository;
    private final MetricsRepository metricsRepository;
    private final SettingsRepository.OnSettingsChangeListener settingsChangeListener;

    private final MutableLiveData<String> userName = new MutableLiveData<>();
    private final MutableLiveData<Double> heightCm = new MutableLiveData<>();
    private final MutableLiveData<Double> weightKg = new MutableLiveData<>();
    private final MutableLiveData<Integer> age = new MutableLiveData<>();
    private final MutableLiveData<SettingsRepository.Gender> gender = new MutableLiveData<>();
    private final MutableLiveData<Double> strideLengthCm = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isManualStride = new MutableLiveData<>();
    private final MutableLiveData<SettingsRepository.TrackingLatency> trackingLatency = new MutableLiveData<>();
    private final MutableLiveData<Long> dailyStepGoal = new MutableLiveData<>();
    private final MutableLiveData<SettingsRepository.UnitSystem> unitSystem = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        this(application, SettingsRepository.getInstance(application), MetricsRepository.getInstance(application));
    }

    public ProfileViewModel(@NonNull Application application,
                            @NonNull SettingsRepository settingsRepository,
                            @NonNull MetricsRepository metricsRepository) {
        super(application);
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository cannot be null");
        this.metricsRepository = Objects.requireNonNull(metricsRepository, "metricsRepository cannot be null");

        // Load initial values from repository
        refreshFromRepository();

        // Listen for external preferences modifications
        settingsChangeListener = key -> {
            if (SettingsRepository.KEY_USER_NAME.equals(key)) {
                postLiveValue(userName, settingsRepository.getUserName());
            } else if (SettingsRepository.KEY_HEIGHT_CM.equals(key)) {
                postLiveValue(heightCm, settingsRepository.getHeightCm());
                if (!settingsRepository.isManualStride()) {
                    postLiveValue(strideLengthCm, settingsRepository.getStrideLengthCm());
                }
            } else if (SettingsRepository.KEY_WEIGHT_KG.equals(key)) {
                postLiveValue(weightKg, settingsRepository.getWeightKg());
            } else if (SettingsRepository.KEY_AGE.equals(key)) {
                postLiveValue(age, settingsRepository.getAge());
            } else if (SettingsRepository.KEY_GENDER.equals(key)) {
                postLiveValue(gender, settingsRepository.getGender());
            } else if (SettingsRepository.KEY_STRIDE_LENGTH_CM.equals(key)) {
                postLiveValue(strideLengthCm, settingsRepository.getStrideLengthCm());
                postLiveValue(isManualStride, settingsRepository.isManualStride());
            } else if (SettingsRepository.KEY_TRACKING_LATENCY.equals(key)) {
                postLiveValue(trackingLatency, settingsRepository.getTrackingLatency());
            } else if (SettingsRepository.KEY_DAILY_STEP_TARGET.equals(key)) {
                postLiveValue(dailyStepGoal, settingsRepository.getDailyStepTarget());
            } else if (SettingsRepository.KEY_UNIT_SYSTEM.equals(key)) {
                postLiveValue(unitSystem, settingsRepository.getUnitSystem());
            }
        };
        settingsRepository.registerListener(settingsChangeListener);
    }

    private void refreshFromRepository() {
        setLiveValue(userName, settingsRepository.getUserName());
        setLiveValue(heightCm, settingsRepository.getHeightCm());
        setLiveValue(weightKg, settingsRepository.getWeightKg());
        setLiveValue(age, settingsRepository.getAge());
        setLiveValue(gender, settingsRepository.getGender());
        setLiveValue(strideLengthCm, settingsRepository.getStrideLengthCm());
        setLiveValue(isManualStride, settingsRepository.isManualStride());
        setLiveValue(trackingLatency, settingsRepository.getTrackingLatency());
        setLiveValue(dailyStepGoal, settingsRepository.getDailyStepTarget());
        setLiveValue(unitSystem, settingsRepository.getUnitSystem());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        settingsRepository.unregisterListener(settingsChangeListener);
    }

    // Observable LiveData getters
    @NonNull
    public LiveData<String> getUserName() {
        return userName;
    }

    public void saveUserName(@Nullable String name) {
        settingsRepository.setUserName(name);
        setLiveValue(userName, settingsRepository.getUserName());
    }

    @NonNull
    public LiveData<Double> getHeightCm() {
        return heightCm;
    }

    @NonNull
    public LiveData<Double> getWeightKg() {
        return weightKg;
    }

    @NonNull
    public LiveData<Integer> getAge() {
        return age;
    }

    @NonNull
    public LiveData<SettingsRepository.Gender> getGender() {
        return gender;
    }

    @NonNull
    public LiveData<Double> getStrideLengthCm() {
        return strideLengthCm;
    }

    @NonNull
    public LiveData<Boolean> getIsManualStride() {
        return isManualStride;
    }

    @NonNull
    public LiveData<SettingsRepository.TrackingLatency> getTrackingLatency() {
        return trackingLatency;
    }

    @NonNull
    public LiveData<Long> getDailyStepGoal() {
        return dailyStepGoal;
    }

    @NonNull
    public LiveData<SettingsRepository.UnitSystem> getUnitSystem() {
        return unitSystem;
    }

    // Validation & Persistence Methods

    /**
     * Validates and saves height.
     * Metric range: 50–260 cm. Imperial range: 20–102 inches.
     * Always converts to canonical centimeters before saving.
     */
    public boolean saveHeight(double height, @NonNull SettingsRepository.UnitSystem unit) {
        if (Double.isNaN(height) || Double.isInfinite(height)) {
            return false;
        }

        double canonicalCm;
        if (unit == SettingsRepository.UnitSystem.IMPERIAL) {
            if (height < MIN_HEIGHT_INCHES || height > MAX_HEIGHT_INCHES) {
                return false;
            }
            canonicalCm = SettingsRepository.inchesToCm(height);
        } else {
            if (height < MIN_HEIGHT_CM || height > MAX_HEIGHT_CM) {
                return false;
            }
            canonicalCm = height;
        }

        if (canonicalCm < MIN_HEIGHT_CM || canonicalCm > MAX_HEIGHT_CM) {
            return false;
        }

        boolean saved = settingsRepository.setHeightCm(canonicalCm);
        if (saved) {
            setLiveValue(heightCm, canonicalCm);
            if (!settingsRepository.isManualStride()) {
                double autoStride = canonicalCm * SettingsRepository.STRIDE_HEIGHT_RATIO;
                setLiveValue(strideLengthCm, autoStride);
            }
        }
        return saved;
    }

    /**
     * Validates and saves weight.
     * Metric range: 20–350 kg. Imperial range: 44–770 lb.
     * Always converts to canonical kilograms before saving.
     */
    public boolean saveWeight(double weight, @NonNull SettingsRepository.UnitSystem unit) {
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            return false;
        }

        double canonicalKg;
        if (unit == SettingsRepository.UnitSystem.IMPERIAL) {
            if (weight < MIN_WEIGHT_LBS || weight > MAX_WEIGHT_LBS) {
                return false;
            }
            canonicalKg = SettingsRepository.lbsToKg(weight);
        } else {
            if (weight < MIN_WEIGHT_KG || weight > MAX_WEIGHT_KG) {
                return false;
            }
            canonicalKg = weight;
        }

        if (canonicalKg < MIN_WEIGHT_KG || canonicalKg > MAX_WEIGHT_KG) {
            return false;
        }

        boolean saved = settingsRepository.setWeightKg(canonicalKg);
        if (saved) {
            setLiveValue(weightKg, canonicalKg);
        }
        return saved;
    }

    /**
     * Validates and saves age.
     * Valid range: 5–125 years.
     */
    public boolean saveAge(int age) {
        if (age < MIN_AGE || age > MAX_AGE) {
            return false;
        }

        boolean saved = settingsRepository.setAge(age);
        if (saved) {
            setLiveValue(this.age, age);
        }
        return saved;
    }

    /**
     * Saves calorie estimation formula gender selection.
     */
    public boolean saveGender(@Nullable SettingsRepository.Gender gender) {
        if (gender == null) {
            return false;
        }

        boolean saved = settingsRepository.setGender(gender);
        if (saved) {
            setLiveValue(this.gender, gender);
        }
        return saved;
    }

    /**
     * Validates and saves manual stride length.
     * Metric range: 20–160 cm. Imperial range: 8–63 inches.
     * Always converts to canonical centimeters before saving.
     */
    public boolean saveStrideLength(double stride, @NonNull SettingsRepository.UnitSystem unit) {
        if (Double.isNaN(stride) || Double.isInfinite(stride)) {
            return false;
        }

        double canonicalCm;
        if (unit == SettingsRepository.UnitSystem.IMPERIAL) {
            if (stride < MIN_STRIDE_INCHES || stride > MAX_STRIDE_INCHES) {
                return false;
            }
            canonicalCm = SettingsRepository.inchesToCm(stride);
        } else {
            if (stride < MIN_STRIDE_CM || stride > MAX_STRIDE_CM) {
                return false;
            }
            canonicalCm = stride;
        }

        if (canonicalCm < MIN_STRIDE_CM || canonicalCm > MAX_STRIDE_CM) {
            return false;
        }

        boolean saved = settingsRepository.setStrideLengthCm(canonicalCm);
        if (saved) {
            setLiveValue(strideLengthCm, canonicalCm);
            setLiveValue(isManualStride, true);
        }
        return saved;
    }

    /**
     * Resets stride length to automatic estimation derived from height (height * 0.415).
     */
    public void resetStrideToDefault() {
        settingsRepository.clearManualStride();
        double autoStride = settingsRepository.getStrideLengthCm();
        setLiveValue(strideLengthCm, autoStride);
        setLiveValue(isManualStride, false);
    }

    /**
     * Saves tracking latency batching preference.
     */
    public boolean saveTrackingLatency(@Nullable SettingsRepository.TrackingLatency latency) {
        if (latency == null) {
            return false;
        }

        boolean saved = settingsRepository.setTrackingLatency(latency);
        if (saved) {
            setLiveValue(this.trackingLatency, latency);
        }
        return saved;
    }

    /**
     * Validates and saves daily step target (500–100,000 steps).
     * Synchronously persists to SharedPreferences and updates today's Room entity
     * so hero ring and history reflect the new target immediately.
     */
    public boolean saveDailyStepGoal(long goal) {
        if (goal < MIN_STEP_GOAL || goal > MAX_STEP_GOAL) {
            return false;
        }

        boolean saved = settingsRepository.setDailyStepTarget(goal);
        if (saved) {
            setLiveValue(dailyStepGoal, goal);
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            metricsRepository.updateDailyStepGoal(today, goal, null);
        }
        return saved;
    }

    /**
     * Saves measurement unit system preference (Metric vs Imperial).
     */
    public boolean saveUnitSystem(@Nullable SettingsRepository.UnitSystem unit) {
        if (unit == null) {
            return false;
        }

        boolean saved = settingsRepository.setUnitSystem(unit);
        if (saved) {
            setLiveValue(this.unitSystem, unit);
        }
        return saved;
    }

    private <T> void setLiveValue(@NonNull MutableLiveData<T> liveData, @Nullable T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            liveData.setValue(value);
        } else {
            liveData.postValue(value);
        }
    }

    private <T> void postLiveValue(@NonNull MutableLiveData<T> liveData, @Nullable T value) {
        liveData.postValue(value);
    }
}
