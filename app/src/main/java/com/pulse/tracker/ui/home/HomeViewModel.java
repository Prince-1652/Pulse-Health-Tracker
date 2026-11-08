package com.pulse.tracker.ui.home;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.util.PermissionUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * ViewModel for the Today / Home screen destination.
 * Coordinates real-time observation of today's Room metrics, 7-day chronological history,
 * tracking state transitions, and user calculation preferences.
 */
public class HomeViewModel extends AndroidViewModel {

    public enum TrackingStatus {
        ACTIVE,
        PAUSED,
        PERMISSION_MISSING,
        SENSOR_UNSUPPORTED
    }

    private final MetricsRepository metricsRepository;
    private final SettingsRepository settingsRepository;
    private final SettingsRepository.OnSettingsChangeListener settingsChangeListener;

    private final MutableLiveData<String> currentDateLive = new MutableLiveData<>();
    private final MediatorLiveData<DailyMetricsEntity> todayMetrics = new MediatorLiveData<>();
    private final MutableLiveData<List<DailyMetricsEntity>> last7DaysMetrics = new MutableLiveData<>();
    private final MutableLiveData<TrackingStatus> trackingStatus = new MutableLiveData<>();
    private final MutableLiveData<SettingsRepository.UnitSystem> unitSystem = new MutableLiveData<>();
    private final MutableLiveData<Long> dailyStepTarget = new MutableLiveData<>();

    private LiveData<DailyMetricsEntity> currentRoomSource = null;

    public HomeViewModel(@NonNull Application application) {
        this(application, MetricsRepository.getInstance(application), SettingsRepository.getInstance(application));
    }

    public HomeViewModel(@NonNull Application application,
                         @NonNull MetricsRepository metricsRepository,
                         @NonNull SettingsRepository settingsRepository) {
        super(application);
        this.metricsRepository = Objects.requireNonNull(metricsRepository);
        this.settingsRepository = Objects.requireNonNull(settingsRepository);

        // Initialize user preference LiveData
        setLiveValue(unitSystem, settingsRepository.getUnitSystem());
        setLiveValue(dailyStepTarget, settingsRepository.getDailyStepTarget());

        // Register settings changes listener
        settingsChangeListener = key -> {
            if (SettingsRepository.KEY_TRACKING_ENABLED.equals(key) || SettingsRepository.KEY_TRACKING_PAUSED.equals(key)) {
                checkTrackingStatus();
            } else if (SettingsRepository.KEY_UNIT_SYSTEM.equals(key)) {
                postLiveValue(unitSystem, settingsRepository.getUnitSystem());
            } else if (SettingsRepository.KEY_DAILY_STEP_TARGET.equals(key)) {
                postLiveValue(dailyStepTarget, settingsRepository.getDailyStepTarget());
                reload7DaysMetrics();
            }
        };
        settingsRepository.registerListener(settingsChangeListener);

        // Switch today's metrics LiveData source when date changes
        todayMetrics.addSource(currentDateLive, date -> {
            if (currentRoomSource != null) {
                todayMetrics.removeSource(currentRoomSource);
            }
            if (date != null) {
                currentRoomSource = metricsRepository.getTodayMetricsLive(date);
                todayMetrics.addSource(currentRoomSource, entity -> {
                    setLiveValue(todayMetrics, entity);
                    reload7DaysMetrics();
                });
            }
        });

        // Initialize today's local date
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        setLiveValue(currentDateLive, today);

        checkTrackingStatus();
        reload7DaysMetrics();
    }

    @NonNull
    public LiveData<DailyMetricsEntity> getTodayMetrics() {
        return todayMetrics;
    }

    @NonNull
    public LiveData<List<DailyMetricsEntity>> getLast7DaysMetrics() {
        return last7DaysMetrics;
    }

    @NonNull
    public LiveData<TrackingStatus> getTrackingStatus() {
        return trackingStatus;
    }

    @NonNull
    public LiveData<SettingsRepository.UnitSystem> getUnitSystem() {
        return unitSystem;
    }

    @NonNull
    public LiveData<Long> getDailyStepTarget() {
        return dailyStepTarget;
    }

    /**
     * Re-queries tracking status, date, preferences, and 7-day metrics.
     */
    public void refresh() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (!Objects.equals(today, currentDateLive.getValue())) {
            setLiveValue(currentDateLive, today);
        }
        setLiveValue(unitSystem, settingsRepository.getUnitSystem());
        setLiveValue(dailyStepTarget, settingsRepository.getDailyStepTarget());
        checkTrackingStatus();
        reload7DaysMetrics();
    }

    /**
     * Evaluates current hardware and permission conditions to update tracking status.
     */
    public void checkTrackingStatus() {
        TrackingStatus status = resolveTrackingStatus();
        setLiveValue(trackingStatus, status);
    }

    /**
     * Resolves the current tracking status deterministically.
     */
    @NonNull
    public TrackingStatus resolveTrackingStatus() {
        Context context = getApplication();
        if (!PermissionUtils.isStepCounterSensorAvailable(context)) {
            return TrackingStatus.SENSOR_UNSUPPORTED;
        }
        if (!PermissionUtils.hasActivityRecognitionPermission(context)) {
            return TrackingStatus.PERMISSION_MISSING;
        }
        if (!settingsRepository.isTrackingEnabled() || settingsRepository.isTrackingPaused()) {
            return TrackingStatus.PAUSED;
        }
        return TrackingStatus.ACTIVE;
    }

    /**
     * Asynchronously reloads the 7-day chronological sequence ending on the current date.
     */
    public void reload7DaysMetrics() {
        String dateStr = currentDateLive.getValue();
        LocalDate endDate = (dateStr != null) ? LocalDate.parse(dateStr) : LocalDate.now();
        long goal = settingsRepository.getDailyStepTarget();

        metricsRepository.getLast7DaysMetrics(endDate, goal, list -> {
            setLiveValue(last7DaysMetrics, list);
        });
    }

    @VisibleForTesting
    public void setDateForTesting(@NonNull String dateStr) {
        setLiveValue(currentDateLive, dateStr);
        reload7DaysMetrics();
    }

    private <T> void setLiveValue(MutableLiveData<T> liveData, @Nullable T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            liveData.setValue(value);
        } else {
            liveData.postValue(value);
        }
    }

    private <T> void postLiveValue(MutableLiveData<T> liveData, @Nullable T value) {
        liveData.postValue(value);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        settingsRepository.unregisterListener(settingsChangeListener);
    }
}

