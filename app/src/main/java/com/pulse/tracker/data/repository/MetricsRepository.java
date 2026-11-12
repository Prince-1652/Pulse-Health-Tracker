package com.pulse.tracker.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;

import com.pulse.tracker.data.local.PulseDatabase;
import com.pulse.tracker.data.local.dao.DailyMetricsDao;
import com.pulse.tracker.data.local.dao.TrackingStateDao;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.local.entity.TrackingStateEntity;
import com.pulse.tracker.domain.CalorieCalculator;
import com.pulse.tracker.domain.DistanceCalculator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository coordinating Room database operations for daily step metrics and tracking state checkpoints.
 * All write and non-LiveData database operations are serialized on a dedicated background executor.
 */
public class MetricsRepository {

    private static volatile MetricsRepository INSTANCE;

    private final PulseDatabase database;
    private final DailyMetricsDao dailyMetricsDao;
    private final TrackingStateDao trackingStateDao;
    private final Executor executor;

    public interface Callback<T> {
        void onResult(T result);
    }

    /**
     * Value object containing aggregated monthly analytics.
     */
    public static class MonthlyAnalytics {
        private final long totalSteps;
        private final double totalDistanceMeters;
        private final long totalActiveSeconds;
        private final double totalCalories;
        private final double averagePaceMinPerKm;
        @Nullable
        private final DailyMetricsEntity personalBest;

        public MonthlyAnalytics(long totalSteps, double totalDistanceMeters,
                                long totalActiveSeconds, double totalCalories,
                                double averagePaceMinPerKm, @Nullable DailyMetricsEntity personalBest) {
            this.totalSteps = totalSteps;
            this.totalDistanceMeters = totalDistanceMeters;
            this.totalActiveSeconds = totalActiveSeconds;
            this.totalCalories = totalCalories;
            this.averagePaceMinPerKm = averagePaceMinPerKm;
            this.personalBest = personalBest;
        }

        public long getTotalSteps() {
            return totalSteps;
        }

        public double getTotalDistanceMeters() {
            return totalDistanceMeters;
        }

        public long getTotalActiveSeconds() {
            return totalActiveSeconds;
        }

        public double getTotalCalories() {
            return totalCalories;
        }

        public double getAveragePaceMinPerKm() {
            return averagePaceMinPerKm;
        }

        @Nullable
        public DailyMetricsEntity getPersonalBest() {
            return personalBest;
        }
    }

    @VisibleForTesting
    public MetricsRepository(@NonNull PulseDatabase database, @NonNull Executor executor) {
        this.database = Objects.requireNonNull(database);
        this.dailyMetricsDao = database.dailyMetricsDao();
        this.trackingStateDao = database.trackingStateDao();
        this.executor = Objects.requireNonNull(executor);
    }

    public static MetricsRepository getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (MetricsRepository.class) {
                if (INSTANCE == null) {
                    PulseDatabase db = PulseDatabase.getInstance(context);
                    Executor executor = Executors.newSingleThreadExecutor();
                    INSTANCE = new MetricsRepository(db, executor);
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    public PulseDatabase getDatabase() {
        return database;
    }

    @NonNull
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Exposes observable LiveData for today's metrics row.
     */
    public LiveData<DailyMetricsEntity> getTodayMetricsLive(@NonNull String todayDate) {
        return dailyMetricsDao.getTodayMetrics(todayDate);
    }

    /**
     * Exposes observable LiveData for all metric rows in a month prefix (e.g. '2026-11').
     */
    public LiveData<List<DailyMetricsEntity>> getMonthMetricsLive(@NonNull String yearMonth) {
        String pattern = yearMonth.endsWith("%") ? yearMonth : yearMonth + "%";
        return dailyMetricsDao.getMetricsForMonthLive(pattern);
    }

    /**
     * Synchronously retrieves a single date's metrics (must be called from a background thread).
     */
    @Nullable
    public DailyMetricsEntity getMetricsForDateSync(@NonNull String date) {
        return dailyMetricsDao.getMetricsForDate(date);
    }

    /**
     * Asynchronously retrieves a single date's metrics.
     */
    public void getMetricsForDate(@NonNull String date, @NonNull Callback<DailyMetricsEntity> callback) {
        executor.execute(() -> {
            DailyMetricsEntity entity = dailyMetricsDao.getMetricsForDate(date);
            callback.onResult(entity);
        });
    }

    /**
     * Synchronously retrieves the tracking state checkpoint (must be called on background thread).
     */
    @Nullable
    public TrackingStateEntity getTrackingStateSync() {
        return trackingStateDao.getTrackingState();
    }

    /**
     * Asynchronously retrieves the tracking state checkpoint.
     */
    public void getTrackingState(@NonNull Callback<TrackingStateEntity> callback) {
        executor.execute(() -> {
            TrackingStateEntity state = trackingStateDao.getTrackingState();
            callback.onResult(state);
        });
    }

    /**
     * Saves or replaces tracking state synchronously.
     */
    public void saveTrackingStateSync(@NonNull TrackingStateEntity state) {
        trackingStateDao.insertOrReplace(state);
    }

    /**
     * Saves or replaces tracking state asynchronously.
     */
    public void saveTrackingState(@NonNull TrackingStateEntity state, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            saveTrackingStateSync(state);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Atomically increments today's metrics and persists the tracker checkpoint inside a single Room transaction.
     * Prevents negative metrics and ensures database consistency during crashes or restarts.
     */
    public void applyStepDeltaAtomicSync(@NonNull String date, long stepDelta, double distanceDelta,
                                         long activeSecondsDelta, double caloriesDelta,
                                         long defaultGoal, @Nullable TrackingStateEntity updatedState) {
        database.runInTransaction(() -> {
            DailyMetricsEntity existing = dailyMetricsDao.getMetricsForDate(date);
            long safeGoal = defaultGoal > 0 ? defaultGoal : 10000L;
            long now = System.currentTimeMillis();

            if (existing == null) {
                DailyMetricsEntity newEntity = new DailyMetricsEntity(
                        date,
                        Math.max(0, stepDelta),
                        Math.max(0.0, distanceDelta),
                        Math.max(0, activeSecondsDelta),
                        Math.max(0.0, caloriesDelta),
                        safeGoal,
                        now
                );
                dailyMetricsDao.insertOrUpdate(newEntity);
            } else {
                long newSteps = Math.max(0, existing.getSteps() + stepDelta);
                double newDistance = Math.max(0.0, existing.getDistanceMeters() + distanceDelta);
                long newActiveSeconds = Math.max(0, existing.getActiveSeconds() + activeSecondsDelta);
                double newCalories = Math.max(0.0, existing.getActiveCaloriesKcal() + caloriesDelta);
                long goal = existing.getStepGoal() > 0 ? existing.getStepGoal() : safeGoal;

                existing.setSteps(newSteps);
                existing.setDistanceMeters(newDistance);
                existing.setActiveSeconds(newActiveSeconds);
                existing.setActiveCaloriesKcal(newCalories);
                existing.setStepGoal(goal);
                existing.setUpdatedAtMillis(now);

                dailyMetricsDao.insertOrUpdate(existing);
            }

            if (updatedState != null) {
                trackingStateDao.insertOrReplace(updatedState);
            }
        });
    }

    /**
     * Asynchronous overload for applyStepDeltaAtomic.
     */
    public void applyStepDeltaAtomic(@NonNull String date, long stepDelta, double distanceDelta,
                                     long activeSecondsDelta, double caloriesDelta,
                                     long defaultGoal, @Nullable TrackingStateEntity updatedState,
                                     @Nullable Runnable onComplete) {
        executor.execute(() -> {
            applyStepDeltaAtomicSync(date, stepDelta, distanceDelta, activeSecondsDelta, caloriesDelta, defaultGoal, updatedState);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Synchronously updates the step goal for the given date (typically today).
     * If a row for the date already exists, updates stepGoal and updatedAtMillis.
     * If no row exists yet, inserts a new empty entity for the date with the given goal.
     */
    public void updateDailyStepGoalSync(@NonNull String date, long goal) {
        database.runInTransaction(() -> {
            DailyMetricsEntity existing = dailyMetricsDao.getMetricsForDate(date);
            long safeGoal = Math.max(1, goal);
            long now = System.currentTimeMillis();
            if (existing == null) {
                DailyMetricsEntity newEntity = DailyMetricsEntity.empty(date, safeGoal);
                dailyMetricsDao.insertOrUpdate(newEntity);
            } else {
                existing.setStepGoal(safeGoal);
                existing.setUpdatedAtMillis(now);
                dailyMetricsDao.insertOrUpdate(existing);
            }
        });
    }

    /**
     * Asynchronous overload for updateDailyStepGoalSync.
     */
    public void updateDailyStepGoal(@NonNull String date, long goal, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            updateDailyStepGoalSync(date, goal);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Returns exactly 7 entries ending on the specified endDate (inclusive).
     * Missing days are synthesized with 0 steps so UI and charts always receive 7 points.
     */
    @NonNull
    public List<DailyMetricsEntity> getLast7DaysMetricsSync(@NonNull LocalDate endDate, long defaultGoal) {
        LocalDate startDate = endDate.minusDays(6);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        String startStr = startDate.format(formatter);
        String endStr = endDate.format(formatter);

        List<DailyMetricsEntity> dbList = dailyMetricsDao.getMetricsBetweenDates(startStr, endStr);
        Map<String, DailyMetricsEntity> map = new HashMap<>();
        if (dbList != null) {
            for (DailyMetricsEntity entity : dbList) {
                map.put(entity.getDate(), entity);
            }
        }

        List<DailyMetricsEntity> result = new ArrayList<>(7);
        long safeGoal = defaultGoal > 0 ? defaultGoal : 10000L;

        for (int i = 6; i >= 0; i--) {
            LocalDate current = endDate.minusDays(i);
            String dateKey = current.format(formatter);
            DailyMetricsEntity entity = map.get(dateKey);
            if (entity == null) {
                entity = DailyMetricsEntity.empty(dateKey, safeGoal);
            }
            result.add(entity);
        }

        return result;
    }

    /**
     * Asynchronous overload for getLast7DaysMetrics.
     */
    public void getLast7DaysMetrics(@NonNull LocalDate endDate, long defaultGoal,
                                    @NonNull Callback<List<DailyMetricsEntity>> callback) {
        executor.execute(() -> {
            List<DailyMetricsEntity> metrics = getLast7DaysMetricsSync(endDate, defaultGoal);
            callback.onResult(metrics);
        });
    }

    /**
     * Calculates monthly analytics (total steps, distance, active time, calories, pace, personal best) synchronously.
     */
    @NonNull
    public MonthlyAnalytics calculateMonthlyAnalyticsSync(@NonNull String yearMonth) {
        String pattern = yearMonth.endsWith("%") ? yearMonth : yearMonth + "%";
        List<DailyMetricsEntity> monthMetrics = dailyMetricsDao.getMetricsForMonth(pattern);

        long totalSteps = 0L;
        double totalDistance = 0.0;
        long totalActiveSeconds = 0L;
        double totalCalories = 0.0;

        if (monthMetrics != null) {
            for (DailyMetricsEntity entity : monthMetrics) {
                long steps = entity.getSteps();
                double distance = entity.getDistanceMeters();
                long activeSeconds = entity.getActiveSeconds();
                double calories = entity.getActiveCaloriesKcal();

                if (steps > 0) {
                    if (distance <= 0.0) {
                        distance = DistanceCalculator.calculateDistanceMeters(steps, 70.0);
                    }
                    if (activeSeconds <= 0) {
                        activeSeconds = Math.max(1L, Math.round(steps * 0.55));
                    }
                    if (calories <= 0.0) {
                        calories = CalorieCalculator.calculateStepCalories(steps, 70.0, 70.0);
                    }
                }

                totalSteps += steps;
                totalDistance += distance;
                totalActiveSeconds += activeSeconds;
                totalCalories += calories;
            }
        }

        double averagePaceMinPerKm = 0.0;
        if (totalDistance > 0.0 && totalActiveSeconds > 0) {
            double distanceKm = totalDistance / 1000.0;
            double activeMinutes = totalActiveSeconds / 60.0;
            averagePaceMinPerKm = activeMinutes / distanceKm;
        }

        DailyMetricsEntity personalBest = dailyMetricsDao.getPersonalBest();

        return new MonthlyAnalytics(totalSteps, totalDistance, totalActiveSeconds, totalCalories, averagePaceMinPerKm, personalBest);
    }

    /**
     * Asynchronous overload for calculateMonthlyAnalytics.
     */
    public void calculateMonthlyAnalytics(@NonNull String yearMonth, @NonNull Callback<MonthlyAnalytics> callback) {
        executor.execute(() -> {
            MonthlyAnalytics analytics = calculateMonthlyAnalyticsSync(yearMonth);
            callback.onResult(analytics);
        });
    }
}

