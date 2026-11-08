package com.pulse.tracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Objects;

/**
 * Room entity representing daily aggregated step, distance, active time, and calorie metrics.
 * One row exists per local calendar date formatted as 'yyyy-MM-dd'.
 */
@Entity(tableName = "daily_metrics")
public class DailyMetricsEntity {

    @PrimaryKey
    @NonNull
    private String date;

    private long steps;
    private double distanceMeters;
    private long activeSeconds;
    private double activeCaloriesKcal;
    private long stepGoal;
    private long updatedAtMillis;

    /**
     * Primary constructor used by Room.
     */
    public DailyMetricsEntity(@NonNull String date, long steps, double distanceMeters,
                              long activeSeconds, double activeCaloriesKcal,
                              long stepGoal, long updatedAtMillis) {
        this.date = Objects.requireNonNull(date, "date cannot be null");
        this.steps = Math.max(0, steps);
        this.distanceMeters = Math.max(0.0, distanceMeters);
        this.activeSeconds = Math.max(0, activeSeconds);
        this.activeCaloriesKcal = Math.max(0.0, activeCaloriesKcal);
        this.stepGoal = Math.max(0, stepGoal);
        this.updatedAtMillis = updatedAtMillis;
    }

    /**
     * Creates an empty metric record for a given date with specified step goal.
     */
    @Ignore
    public static DailyMetricsEntity empty(@NonNull String date, long stepGoal) {
        return new DailyMetricsEntity(date, 0L, 0.0, 0L, 0.0, stepGoal, System.currentTimeMillis());
    }

    /**
     * Creates a copy with optionally modified values.
     */
    @Ignore
    public Builder toBuilder() {
        return new Builder(this);
    }

    @NonNull
    public String getDate() {
        return date;
    }

    public void setDate(@NonNull String date) {
        this.date = Objects.requireNonNull(date, "date cannot be null");
    }

    public long getSteps() {
        return steps;
    }

    public void setSteps(long steps) {
        this.steps = Math.max(0, steps);
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = Math.max(0.0, distanceMeters);
    }

    public long getActiveSeconds() {
        return activeSeconds;
    }

    public void setActiveSeconds(long activeSeconds) {
        this.activeSeconds = Math.max(0, activeSeconds);
    }

    public double getActiveCaloriesKcal() {
        return activeCaloriesKcal;
    }

    public void setActiveCaloriesKcal(double activeCaloriesKcal) {
        this.activeCaloriesKcal = Math.max(0.0, activeCaloriesKcal);
    }

    public long getStepGoal() {
        return stepGoal;
    }

    public void setStepGoal(long stepGoal) {
        this.stepGoal = Math.max(0, stepGoal);
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public void setUpdatedAtMillis(long updatedAtMillis) {
        this.updatedAtMillis = updatedAtMillis;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyMetricsEntity that = (DailyMetricsEntity) o;
        return steps == that.steps &&
                Double.compare(that.distanceMeters, distanceMeters) == 0 &&
                activeSeconds == that.activeSeconds &&
                Double.compare(that.activeCaloriesKcal, activeCaloriesKcal) == 0 &&
                stepGoal == that.stepGoal &&
                updatedAtMillis == that.updatedAtMillis &&
                date.equals(that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, steps, distanceMeters, activeSeconds, activeCaloriesKcal, stepGoal, updatedAtMillis);
    }

    @NonNull
    @Override
    public String toString() {
        return "DailyMetricsEntity{" +
                "date='" + date + '\'' +
                ", steps=" + steps +
                ", distanceMeters=" + distanceMeters +
                ", activeSeconds=" + activeSeconds +
                ", activeCaloriesKcal=" + activeCaloriesKcal +
                ", stepGoal=" + stepGoal +
                ", updatedAtMillis=" + updatedAtMillis +
                '}';
    }

    public static class Builder {
        private String date;
        private long steps;
        private double distanceMeters;
        private long activeSeconds;
        private double activeCaloriesKcal;
        private long stepGoal = 10000L;
        private long updatedAtMillis = System.currentTimeMillis();

        public Builder() {
        }

        public Builder(@NonNull DailyMetricsEntity existing) {
            this.date = existing.date;
            this.steps = existing.steps;
            this.distanceMeters = existing.distanceMeters;
            this.activeSeconds = existing.activeSeconds;
            this.activeCaloriesKcal = existing.activeCaloriesKcal;
            this.stepGoal = existing.stepGoal;
            this.updatedAtMillis = existing.updatedAtMillis;
        }

        public Builder setDate(@NonNull String date) {
            this.date = date;
            return this;
        }

        public Builder setSteps(long steps) {
            this.steps = Math.max(0, steps);
            return this;
        }

        public Builder setDistanceMeters(double distanceMeters) {
            this.distanceMeters = Math.max(0.0, distanceMeters);
            return this;
        }

        public Builder setActiveSeconds(long activeSeconds) {
            this.activeSeconds = Math.max(0, activeSeconds);
            return this;
        }

        public Builder setActiveCaloriesKcal(double activeCaloriesKcal) {
            this.activeCaloriesKcal = Math.max(0.0, activeCaloriesKcal);
            return this;
        }

        public Builder setStepGoal(long stepGoal) {
            this.stepGoal = Math.max(0, stepGoal);
            return this;
        }

        public Builder setUpdatedAtMillis(long updatedAtMillis) {
            this.updatedAtMillis = updatedAtMillis;
            return this;
        }

        public DailyMetricsEntity build() {
            if (date == null) {
                throw new IllegalStateException("DailyMetricsEntity requires a non-null date");
            }
            return new DailyMetricsEntity(date, steps, distanceMeters, activeSeconds, activeCaloriesKcal, stepGoal, updatedAtMillis);
        }
    }
}

