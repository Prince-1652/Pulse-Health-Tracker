package com.pulse.tracker;

import android.app.Application;

import com.pulse.tracker.data.local.PulseDatabase;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;

/**
 * Base Application class for Pulse.
 * Initializes the Room database and repositories.
 */
public class PulseApplication extends Application {

    private static PulseApplication sInstance;

    private PulseDatabase database;
    private MetricsRepository metricsRepository;
    private SettingsRepository settingsRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        // Eagerly initialize local persistence and repositories on startup
        database = PulseDatabase.getInstance(this);
        metricsRepository = MetricsRepository.getInstance(this);
        settingsRepository = SettingsRepository.getInstance(this);

        // Initialize hydration reminders channel and schedule interval alerts
        com.pulse.tracker.receiver.WaterReminderManager.createNotificationChannel(this);
        com.pulse.tracker.receiver.WaterReminderManager.scheduleNextReminder(this);
    }

    public static PulseApplication getInstance() {
        return sInstance;
    }

    public PulseDatabase getDatabase() {
        return database;
    }

    public MetricsRepository getMetricsRepository() {
        return metricsRepository;
    }

    public SettingsRepository getSettingsRepository() {
        return settingsRepository;
    }
}

