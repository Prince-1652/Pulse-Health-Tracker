package com.pulse.tracker.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.local.entity.TrackingStateEntity;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.domain.ActivitySessionCalculator;
import com.pulse.tracker.domain.CalorieCalculator;
import com.pulse.tracker.domain.DistanceCalculator;
import com.pulse.tracker.domain.StepCounterProcessor;
import com.pulse.tracker.domain.StepProcessingResult;
import com.pulse.tracker.util.BootUtils;
import com.pulse.tracker.util.PermissionUtils;
import com.pulse.tracker.util.UnitFormatter;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Ongoing foreground health service hosting hardware step counter sensor listeners,
 * calculating continuous step, distance, active time, and calorie metrics across process recycles,
 * and maintaining the persistent system notification.
 */
public class StepTrackingService extends Service implements SensorEventListener, SettingsRepository.OnSettingsChangeListener {

    private static final String TAG = "StepTrackingService";

    public static final String ACTION_START = "com.pulse.tracker.action.START";
    public static final String ACTION_PAUSE = "com.pulse.tracker.action.PAUSE";
    public static final String ACTION_RESUME = "com.pulse.tracker.action.RESUME";
    public static final String ACTION_STOP = "com.pulse.tracker.action.STOP";
    public static final String ACTION_UPDATE_NOTIFICATION = "com.pulse.tracker.action.UPDATE_NOTIFICATION";

    private SensorManager sensorManager;
    private SettingsRepository settingsRepository;
    private MetricsRepository metricsRepository;
    private StepNotificationManager notificationManager;
    private StepCounterProcessor stepProcessor;
    private ActivitySessionCalculator sessionCalculator;

    private HandlerThread sensorThread;
    private Handler sensorHandler;
    private PowerManager.WakeLock wakeLock;

    private synchronized void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pulse:StepTrackingWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire();
                Log.i(TAG, "WakeLock acquired");
            } catch (Exception e) {
                Log.w(TAG, "Failed to acquire WakeLock: " + e.getMessage());
            }
        }
    }

    private synchronized void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                Log.i(TAG, "WakeLock released");
            } catch (Exception e) {
                Log.w(TAG, "Failed to release WakeLock: " + e.getMessage());
            }
        }
    }

    private volatile boolean isSensorRegistered = false;
    private volatile boolean isPaused = false;
    private volatile TrackingStateEntity cachedTrackingState = null;
    private volatile long currentTodaySteps = 0L;
    private volatile double currentTodayDistanceMeters = 0.0;
    private volatile double currentTodayCaloriesKcal = 0.0;
    private volatile long lastNotificationTimeMs = 0L;
    private volatile long lastNotificationSteps = 0L;
    private volatile boolean goalReachedNotified = false;

    private final Runnable sessionTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            handleSessionTimeout();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Creating StepTrackingService");

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        settingsRepository = SettingsRepository.getInstance(this);
        metricsRepository = MetricsRepository.getInstance(this);
        notificationManager = new StepNotificationManager(this);
        stepProcessor = new StepCounterProcessor();
        sessionCalculator = new ActivitySessionCalculator();

        settingsRepository.registerListener(this);
        notificationManager.createNotificationChannel();

        // Create dedicated background thread to serialize sensor events
        sensorThread = new HandlerThread("StepSensorThread");
        sensorThread.start();
        sensorHandler = new Handler(sensorThread.getLooper());

        // Pre-load tracking state and today's steps asynchronously
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        metricsRepository.getExecutor().execute(() -> {
            cachedTrackingState = metricsRepository.getTrackingStateSync();
            if (cachedTrackingState != null) {
                ActivitySessionCalculator.SessionUpdate restoreUpdate = sessionCalculator.restoreState(
                        cachedTrackingState,
                        SystemClock.elapsedRealtime(),
                        System.currentTimeMillis(),
                        ZoneId.systemDefault()
                );
                if (restoreUpdate.getActiveSecondsDelta() > 0) {
                    applySessionUpdateToDatabase(restoreUpdate);
                }
                if (sessionCalculator.isSessionActive() && sensorHandler != null) {
                    long elapsedSinceLast = SystemClock.elapsedRealtime() - sessionCalculator.getLastMovementMonotonicMs();
                    long remaining = Math.max(0, ActivitySessionCalculator.INACTIVITY_TIMEOUT_MS - elapsedSinceLast);
                    sensorHandler.postDelayed(sessionTimeoutRunnable, remaining);
                }
            }

            DailyMetricsEntity todayEntity = metricsRepository.getMetricsForDateSync(today);
            if (todayEntity != null) {
                currentTodaySteps = todayEntity.getSteps();
                currentTodayDistanceMeters = todayEntity.getDistanceMeters();
                currentTodayCaloriesKcal = todayEntity.getActiveCaloriesKcal();
                updateNotificationView(true);
            }
        });
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "onStartCommand with action: " + action);

        if (ACTION_STOP.equals(action)) {
            handleActionStop();
            return START_NOT_STICKY;
        } else if (ACTION_PAUSE.equals(action)) {
            handleActionPause();
            return START_STICKY;
        }

        boolean notifEnabled = settingsRepository != null && settingsRepository.isNotificationEnabled();
        if (notifEnabled) {
            promoteToForeground();
        } else {
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                if (notificationManager != null) {
                    notificationManager.cancelNotification();
                }
            } catch (Exception ignored) {
            }
        }

        if (intent == null) {
            // Service recreated by OS (START_STICKY)
            if (settingsRepository != null && settingsRepository.isTrackingEnabled()) {
                isPaused = settingsRepository.isTrackingPaused();
                if (!isPaused) {
                    registerSensor();
                } else {
                    handleActionPause();
                }
            } else {
                stopForeground(ServiceCompat.STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }

        if (ACTION_RESUME.equals(action)) {
            handleActionResume();
        } else if (ACTION_UPDATE_NOTIFICATION.equals(action)) {
            if (notifEnabled) {
                promoteToForeground();
            }
            updateNotificationView(true);
        } else {
            // Default: ACTION_START or null action
            handleActionStart();
        }

        return START_STICKY;
    }

    private void promoteToForeground() {
        if (settingsRepository != null && !settingsRepository.isNotificationEnabled()) {
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                if (notificationManager != null) {
                    notificationManager.cancelNotification();
                }
            } catch (Exception ignored) {
            }
            return;
        }

        long goal = settingsRepository != null ? settingsRepository.getDailyStepTarget() : 10000L;
        double distanceMeters = currentTodayDistanceMeters;
        double caloriesKcal = currentTodayCaloriesKcal;

        if (distanceMeters <= 0.0 && currentTodaySteps > 0 && settingsRepository != null) {
            distanceMeters = DistanceCalculator.calculateDistanceMeters(
                    currentTodaySteps, settingsRepository.getStrideLengthCm()
            );
        }
        if (caloriesKcal <= 0.0 && currentTodaySteps > 0 && settingsRepository != null) {
            caloriesKcal = CalorieCalculator.calculateStepCalories(
                    currentTodaySteps, settingsRepository.getWeightKg(), settingsRepository.getStrideLengthCm()
            );
        }

        Notification notification = notificationManager.buildNotification(
                currentTodaySteps, goal, distanceMeters, caloriesKcal, isPaused
        );

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                        this,
                        StepNotificationManager.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                );
            } else {
                startForeground(StepNotificationManager.NOTIFICATION_ID, notification);
            }
            lastNotificationTimeMs = SystemClock.elapsedRealtime();
            lastNotificationSteps = currentTodaySteps;
        } catch (Exception e) {
            Log.e(TAG, "ForegroundService promotion failed: " + e.getMessage(), e);
            stopSelf();
        }
    }

    private synchronized void handleActionStart() {
        settingsRepository.setTrackingEnabled(true);
        isPaused = settingsRepository.isTrackingPaused();
        if (!isPaused) {
            registerSensor();
        }
        updateNotificationView(true);
    }

    private synchronized void handleActionPause() {
        isPaused = true;
        settingsRepository.setTrackingPaused(true);
        unregisterSensor();
        if (sensorHandler != null) {
            sensorHandler.removeCallbacks(sessionTimeoutRunnable);
        }
        if (sessionCalculator != null && sessionCalculator.isSessionActive()) {
            ActivitySessionCalculator.SessionUpdate update = sessionCalculator.forceEndSession(
                    SystemClock.elapsedRealtime(),
                    System.currentTimeMillis(),
                    ZoneId.systemDefault()
            );
            if (update.getActiveSecondsDelta() > 0) {
                applySessionUpdateToDatabase(update);
            }
        }
        if (settingsRepository.isNotificationEnabled()) {
            promoteToForeground();
        }
        updateNotificationView(true);
    }

    private synchronized void handleActionResume() {
        isPaused = false;
        settingsRepository.setTrackingPaused(false);
        if (settingsRepository.isNotificationEnabled()) {
            promoteToForeground();
        }
        registerSensor();
        updateNotificationView(true);
    }

    private synchronized void handleActionStop() {
        settingsRepository.setTrackingEnabled(false);
        settingsRepository.setTrackingPaused(false);
        isPaused = false;
        unregisterSensor();
        if (sensorHandler != null) {
            sensorHandler.removeCallbacks(sessionTimeoutRunnable);
        }
        if (sessionCalculator != null && sessionCalculator.isSessionActive()) {
            ActivitySessionCalculator.SessionUpdate update = sessionCalculator.forceEndSession(
                    SystemClock.elapsedRealtime(),
                    System.currentTimeMillis(),
                    ZoneId.systemDefault()
            );
            if (update.getActiveSecondsDelta() > 0) {
                applySessionUpdateToDatabase(update);
            }
        }
        stopForeground(ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private synchronized void registerSensor() {
        if (isSensorRegistered) {
            return;
        }

        if (!PermissionUtils.hasActivityRecognitionPermission(this)) {
            Log.w(TAG, "Missing ACTIVITY_RECOGNITION permission. Step sensor cannot be registered.");
            return;
        }

        if (sensorManager == null) {
            Log.e(TAG, "SensorManager is null");
            return;
        }

        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (stepSensor == null) {
            Log.e(TAG, "Device does not have a TYPE_STEP_COUNTER hardware sensor.");
            return;
        }

        boolean isBatteryEfficient = settingsRepository.getTrackingLatency() == SettingsRepository.TrackingLatency.BATTERY_EFFICIENT;
        int maxReportLatencyUs = isBatteryEfficient ? 10_000_000 : 0; // 10s batching or immediate

        boolean registered = sensorManager.registerListener(
                this,
                stepSensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                maxReportLatencyUs,
                sensorHandler
        );

        if (registered) {
            isSensorRegistered = true;
            acquireWakeLock();
            Log.i(TAG, "Sensor.TYPE_STEP_COUNTER registered successfully (latency: " + maxReportLatencyUs + " us)");
        } else {
            Log.e(TAG, "Failed to register Sensor.TYPE_STEP_COUNTER");
        }
    }

    private synchronized void unregisterSensor() {
        if (!isSensorRegistered) {
            return;
        }

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            isSensorRegistered = false;
            releaseWakeLock();
            Log.i(TAG, "Sensor.TYPE_STEP_COUNTER unregistered");
        }
    }

    private void updateNotificationView() {
        updateNotificationView(false);
    }

    private void updateNotificationView(boolean forceImmediate) {
        if (settingsRepository == null || notificationManager == null) {
            return;
        }
        if (!settingsRepository.isNotificationEnabled()) {
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                notificationManager.cancelNotification();
            } catch (Exception ignored) {
            }
            return;
        }

        long goal = settingsRepository.getDailyStepTarget();
        long stepDelta = Math.abs(currentTodaySteps - lastNotificationSteps);
        long elapsedMs = SystemClock.elapsedRealtime() - lastNotificationTimeMs;
        boolean justReachedGoal = (goal > 0 && currentTodaySteps >= goal && !goalReachedNotified);

        if (justReachedGoal) {
            goalReachedNotified = true;
        } else if (goal > 0 && currentTodaySteps < goal) {
            goalReachedNotified = false;
        }

        // Throttle: update at most once per 1.5s or 5 steps unless forced or goal reached
        if (!forceImmediate && !justReachedGoal) {
            if (stepDelta < 5 && elapsedMs < 1500L) {
                return;
            }
        }

        lastNotificationTimeMs = SystemClock.elapsedRealtime();
        lastNotificationSteps = currentTodaySteps;

        double distanceMeters = currentTodayDistanceMeters;
        double caloriesKcal = currentTodayCaloriesKcal;

        if (distanceMeters <= 0.0 && currentTodaySteps > 0 && settingsRepository != null) {
            distanceMeters = DistanceCalculator.calculateDistanceMeters(
                    currentTodaySteps, settingsRepository.getStrideLengthCm()
            );
        }
        if (caloriesKcal <= 0.0 && currentTodaySteps > 0 && settingsRepository != null) {
            caloriesKcal = CalorieCalculator.calculateStepCalories(
                    currentTodaySteps, settingsRepository.getWeightKg(), settingsRepository.getStrideLengthCm()
            );
        }

        notificationManager.updateNotification(
                currentTodaySteps, goal, distanceMeters, caloriesKcal, isPaused, true
        );
    }

    @Override
    public void onSettingChanged(@NonNull String key) {
        if (sensorHandler != null) {
            sensorHandler.post(() -> {
                if (SettingsRepository.KEY_TRACKING_LATENCY.equals(key)) {
                    if (isSensorRegistered && !isPaused) {
                        Log.i(TAG, "Tracking latency changed; re-registering sensor listener with new batching parameters");
                        unregisterSensor();
                        registerSensor();
                    }
                } else if (SettingsRepository.KEY_NOTIFICATION_ENABLED.equals(key)) {
                    if (settingsRepository != null && settingsRepository.isNotificationEnabled()) {
                        promoteToForeground();
                    } else {
                        try {
                            ServiceCompat.stopForeground(StepTrackingService.this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                            if (notificationManager != null) {
                                notificationManager.cancelNotification();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                updateNotificationView(true);
            });
        }
    }

    private void handleSessionTimeout() {
        long nowMonotonic = SystemClock.elapsedRealtime();
        long nowWall = System.currentTimeMillis();
        ActivitySessionCalculator.SessionUpdate update = sessionCalculator.checkInactivityTimeout(
                nowMonotonic, nowWall, ZoneId.systemDefault()
        );

        if (update.getActiveSecondsDelta() > 0) {
            applySessionUpdateToDatabase(update);
        } else if (cachedTrackingState != null) {
            cachedTrackingState = sessionCalculator.syncToTrackingState(cachedTrackingState, nowWall);
            metricsRepository.saveTrackingStateSync(cachedTrackingState);
        }
    }

    private void applySessionUpdateToDatabase(@NonNull ActivitySessionCalculator.SessionUpdate update) {
        if (update.getDateSplits().isEmpty()) {
            return;
        }

        double bmr = CalorieCalculator.calculateBmr(
                settingsRepository.getWeightKg(),
                settingsRepository.getHeightCm(),
                settingsRepository.getAge(),
                settingsRepository.getGender()
        );

        for (Map.Entry<String, Long> entry : update.getDateSplits().entrySet()) {
            String date = entry.getKey();
            long activeSec = entry.getValue();
            double calDelta = CalorieCalculator.calculateNetActiveCalories(bmr, activeSec);

            if (cachedTrackingState != null) {
                cachedTrackingState = sessionCalculator.syncToTrackingState(cachedTrackingState, System.currentTimeMillis());
            }

            metricsRepository.applyStepDeltaAtomicSync(
                    date,
                    0L,
                    0.0,
                    activeSec,
                    calDelta,
                    settingsRepository.getDailyStepTarget(),
                    cachedTrackingState
            );
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null || event.values == null || event.values.length == 0) {
            return;
        }

        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) {
            return;
        }

        long rawSteps = (long) event.values[0];
        long eventTimestampNanos = event.timestamp;
        long elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
        long currentWallTimeMs = System.currentTimeMillis();
        // Ensure state is loaded
        if (cachedTrackingState == null) {
            cachedTrackingState = metricsRepository.getTrackingStateSync();
        }

        long persistedBootMarker = (cachedTrackingState != null) ? cachedTrackingState.getBootSessionMarker() : 0L;
        long bootMarker = BootUtils.getBootSessionMarker(persistedBootMarker);

        StepProcessingResult result = stepProcessor.processStepEvent(
                rawSteps,
                eventTimestampNanos,
                bootMarker,
                currentWallTimeMs,
                elapsedRealtimeNanos,
                ZoneId.systemDefault(),
                cachedTrackingState
        );

        cachedTrackingState = result.getUpdatedTrackingState();

        if (result.getStepDelta() > 0) {
            double distanceDelta = DistanceCalculator.calculateDistanceMeters(
                    result.getStepDelta(),
                    settingsRepository.getStrideLengthCm()
            );

            long eventMonotonicMs = (eventTimestampNanos > 0 && elapsedRealtimeNanos > 0)
                    ? SystemClock.elapsedRealtime() - ((elapsedRealtimeNanos - eventTimestampNanos) / 1_000_000L)
                    : SystemClock.elapsedRealtime();

            ActivitySessionCalculator.SessionUpdate sessionUpdate = sessionCalculator.onMovement(
                    eventMonotonicMs,
                    currentWallTimeMs,
                    ZoneId.systemDefault(),
                    result.getStepDelta()
            );

            // Reschedule 30s session timeout check on sensor thread
            if (sensorHandler != null) {
                sensorHandler.removeCallbacks(sessionTimeoutRunnable);
                sensorHandler.postDelayed(sessionTimeoutRunnable, ActivitySessionCalculator.INACTIVITY_TIMEOUT_MS);
            }

            if (cachedTrackingState != null) {
                cachedTrackingState = sessionCalculator.syncToTrackingState(cachedTrackingState, currentWallTimeMs);
            }

            double bmr = CalorieCalculator.calculateBmr(
                    settingsRepository.getWeightKg(),
                    settingsRepository.getHeightCm(),
                    settingsRepository.getAge(),
                    settingsRepository.getGender()
            );

            double stepCaloriesFloor = CalorieCalculator.calculateStepCalories(
                    result.getStepDelta(),
                    settingsRepository.getWeightKg(),
                    settingsRepository.getStrideLengthCm()
            );

            Map<String, Long> dateSplits = sessionUpdate.getDateSplits();
            if (dateSplits.size() > 1) {
                // Multi-date session attribution (crossover at local midnight)
                for (Map.Entry<String, Long> entry : dateSplits.entrySet()) {
                    String splitDate = entry.getKey();
                    long activeSec = entry.getValue();
                    double calDelta = CalorieCalculator.calculateNetActiveCalories(bmr, activeSec);

                    boolean isTargetDate = splitDate.equals(result.getTargetDate());
                    long stepsForDate = isTargetDate ? result.getStepDelta() : 0L;
                    double distForDate = isTargetDate ? distanceDelta : 0.0;
                    if (isTargetDate && stepsForDate > 0) {
                        calDelta = Math.max(calDelta, stepCaloriesFloor);
                    }

                    metricsRepository.applyStepDeltaAtomicSync(
                            splitDate,
                            stepsForDate,
                            distForDate,
                            activeSec,
                            calDelta,
                            settingsRepository.getDailyStepTarget(),
                            cachedTrackingState
                    );
                }
            } else {
                long activeSecondsDelta = sessionUpdate.getActiveSecondsDelta();
                if (activeSecondsDelta == 0) {
                    long minActive = Math.max(1L, Math.round(result.getStepDelta() * 0.55));
                    if (cachedTrackingState != null && cachedTrackingState.getUpdatedAtMillis() > 0) {
                        long elapsedSec = (currentWallTimeMs - cachedTrackingState.getUpdatedAtMillis()) / 1000L;
                        if (elapsedSec > 0) {
                            minActive = Math.min(minActive, Math.max(1L, elapsedSec));
                        }
                    }
                    activeSecondsDelta = minActive;
                }

                double caloriesDelta = Math.max(
                        CalorieCalculator.calculateNetActiveCalories(bmr, activeSecondsDelta),
                        stepCaloriesFloor
                );

                metricsRepository.applyStepDeltaAtomicSync(
                        result.getTargetDate(),
                        result.getStepDelta(),
                        distanceDelta,
                        activeSecondsDelta,
                        caloriesDelta,
                        settingsRepository.getDailyStepTarget(),
                        cachedTrackingState
                );
            }

            String todayDate = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);
            DailyMetricsEntity updatedDaily = metricsRepository.getMetricsForDateSync(todayDate);
            if (updatedDaily != null) {
                currentTodaySteps = updatedDaily.getSteps();
                currentTodayDistanceMeters = updatedDaily.getDistanceMeters();
                currentTodayCaloriesKcal = updatedDaily.getActiveCaloriesKcal();
            } else if (todayDate.equals(result.getTargetDate())) {
                currentTodaySteps += result.getStepDelta();
                currentTodayDistanceMeters += distanceDelta;
                long activeSec = sessionUpdate.getActiveSecondsDelta();
                if (activeSec == 0) {
                    activeSec = Math.max(1L, Math.round(result.getStepDelta() * 0.55));
                }
                double calDelta = Math.max(CalorieCalculator.calculateNetActiveCalories(bmr, activeSec), stepCaloriesFloor);
                currentTodayCaloriesKcal += calDelta;
            }

            updateNotificationView();
        } else if (result.isBaselineInit()) {
            if (cachedTrackingState != null) {
                cachedTrackingState = sessionCalculator.syncToTrackingState(cachedTrackingState, currentWallTimeMs);
                metricsRepository.saveTrackingStateSync(cachedTrackingState);
            }
            String todayDate = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);
            DailyMetricsEntity todayMetrics = metricsRepository.getMetricsForDateSync(todayDate);
            if (todayMetrics != null) {
                currentTodaySteps = todayMetrics.getSteps();
                currentTodayDistanceMeters = todayMetrics.getDistanceMeters();
                currentTodayCaloriesKcal = todayMetrics.getActiveCaloriesKcal();
            }
            updateNotificationView();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Step counter accuracy changes are ignored
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Destroying StepTrackingService");

        unregisterSensor();
        releaseWakeLock();

        if (settingsRepository != null) {
            settingsRepository.unregisterListener(this);
        }

        if (sensorHandler != null) {
            sensorHandler.removeCallbacks(sessionTimeoutRunnable);
        }

        if (sessionCalculator != null && sessionCalculator.isSessionActive()) {
            ActivitySessionCalculator.SessionUpdate update = sessionCalculator.forceEndSession(
                    SystemClock.elapsedRealtime(),
                    System.currentTimeMillis(),
                    ZoneId.systemDefault()
            );
            if (update.getActiveSecondsDelta() > 0) {
                applySessionUpdateToDatabase(update);
            }
        }

        if (sensorThread != null) {
            sensorThread.quitSafely();
            sensorThread = null;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.i(TAG, "onTaskRemoved - swiped away from recents; ensuring service persistence");
        if (settingsRepository != null && settingsRepository.isTrackingEnabled()) {
            Intent restartServiceIntent = new Intent(getApplicationContext(), StepTrackingService.class);
            restartServiceIntent.setAction(ACTION_START);
            restartServiceIntent.setPackage(getPackageName());
            int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent restartPendingIntent = PendingIntent.getService(
                    getApplicationContext(),
                    1002,
                    restartServiceIntent,
                    flags
            );
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                long triggerAtMillis = SystemClock.elapsedRealtime() + 1000;
                try {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, restartPendingIntent);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to schedule restart alarm: " + e.getMessage());
                }
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
