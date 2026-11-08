package com.pulse.tracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Objects;

/**
 * Room entity representing the singleton tracking state checkpoint.
 * Ensures step counter continuity across service restarts, reboots, and active movement sessions.
 */
@Entity(tableName = "tracking_state")
public class TrackingStateEntity {

    public static final int SINGLETON_ID = 1;

    @PrimaryKey
    private int id = SINGLETON_ID;

    private long lastCumulativeSteps;
    private long bootSessionMarker;
    private boolean baselineInitialized;
    private long lastEventTimestamp;
    private boolean sessionActive;
    private long sessionStartMonotonicMs;
    private long lastMovementMonotonicMs;
    private long accumulatedSessionSeconds;
    private long updatedAtMillis;

    /**
     * Primary constructor used by Room.
     */
    public TrackingStateEntity(int id, long lastCumulativeSteps, long bootSessionMarker,
                               boolean baselineInitialized, long lastEventTimestamp,
                               boolean sessionActive, long sessionStartMonotonicMs,
                               long lastMovementMonotonicMs, long accumulatedSessionSeconds,
                               long updatedAtMillis) {
        this.id = id;
        this.lastCumulativeSteps = Math.max(0, lastCumulativeSteps);
        this.bootSessionMarker = bootSessionMarker;
        this.baselineInitialized = baselineInitialized;
        this.lastEventTimestamp = lastEventTimestamp;
        this.sessionActive = sessionActive;
        this.sessionStartMonotonicMs = sessionStartMonotonicMs;
        this.lastMovementMonotonicMs = lastMovementMonotonicMs;
        this.accumulatedSessionSeconds = Math.max(0, accumulatedSessionSeconds);
        this.updatedAtMillis = updatedAtMillis;
    }

    /**
     * Factory for initial uncalibrated state.
     */
    @Ignore
    public static TrackingStateEntity createInitial() {
        return new TrackingStateEntity(
                SINGLETON_ID,
                0L,
                0L,
                false,
                0L,
                false,
                0L,
                0L,
                0L,
                System.currentTimeMillis()
        );
    }

    @Ignore
    public Builder toBuilder() {
        return new Builder(this);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getLastCumulativeSteps() {
        return lastCumulativeSteps;
    }

    public void setLastCumulativeSteps(long lastCumulativeSteps) {
        this.lastCumulativeSteps = Math.max(0, lastCumulativeSteps);
    }

    public long getBootSessionMarker() {
        return bootSessionMarker;
    }

    public void setBootSessionMarker(long bootSessionMarker) {
        this.bootSessionMarker = bootSessionMarker;
    }

    public boolean isBaselineInitialized() {
        return baselineInitialized;
    }

    public void setBaselineInitialized(boolean baselineInitialized) {
        this.baselineInitialized = baselineInitialized;
    }

    public long getLastEventTimestamp() {
        return lastEventTimestamp;
    }

    public void setLastEventTimestamp(long lastEventTimestamp) {
        this.lastEventTimestamp = lastEventTimestamp;
    }

    public boolean isSessionActive() {
        return sessionActive;
    }

    public void setSessionActive(boolean sessionActive) {
        this.sessionActive = sessionActive;
    }

    public long getSessionStartMonotonicMs() {
        return sessionStartMonotonicMs;
    }

    public void setSessionStartMonotonicMs(long sessionStartMonotonicMs) {
        this.sessionStartMonotonicMs = sessionStartMonotonicMs;
    }

    public long getLastMovementMonotonicMs() {
        return lastMovementMonotonicMs;
    }

    public void setLastMovementMonotonicMs(long lastMovementMonotonicMs) {
        this.lastMovementMonotonicMs = lastMovementMonotonicMs;
    }

    public long getAccumulatedSessionSeconds() {
        return accumulatedSessionSeconds;
    }

    public void setAccumulatedSessionSeconds(long accumulatedSessionSeconds) {
        this.accumulatedSessionSeconds = Math.max(0, accumulatedSessionSeconds);
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
        TrackingStateEntity that = (TrackingStateEntity) o;
        return id == that.id &&
                lastCumulativeSteps == that.lastCumulativeSteps &&
                bootSessionMarker == that.bootSessionMarker &&
                baselineInitialized == that.baselineInitialized &&
                lastEventTimestamp == that.lastEventTimestamp &&
                sessionActive == that.sessionActive &&
                sessionStartMonotonicMs == that.sessionStartMonotonicMs &&
                lastMovementMonotonicMs == that.lastMovementMonotonicMs &&
                accumulatedSessionSeconds == that.accumulatedSessionSeconds &&
                updatedAtMillis == that.updatedAtMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, lastCumulativeSteps, bootSessionMarker, baselineInitialized,
                lastEventTimestamp, sessionActive, sessionStartMonotonicMs,
                lastMovementMonotonicMs, accumulatedSessionSeconds, updatedAtMillis);
    }

    @NonNull
    @Override
    public String toString() {
        return "TrackingStateEntity{" +
                "id=" + id +
                ", lastCumulativeSteps=" + lastCumulativeSteps +
                ", bootSessionMarker=" + bootSessionMarker +
                ", baselineInitialized=" + baselineInitialized +
                ", lastEventTimestamp=" + lastEventTimestamp +
                ", sessionActive=" + sessionActive +
                ", sessionStartMonotonicMs=" + sessionStartMonotonicMs +
                ", lastMovementMonotonicMs=" + lastMovementMonotonicMs +
                ", accumulatedSessionSeconds=" + accumulatedSessionSeconds +
                ", updatedAtMillis=" + updatedAtMillis +
                '}';
    }

    public static class Builder {
        private int id = SINGLETON_ID;
        private long lastCumulativeSteps;
        private long bootSessionMarker;
        private boolean baselineInitialized;
        private long lastEventTimestamp;
        private boolean sessionActive;
        private long sessionStartMonotonicMs;
        private long lastMovementMonotonicMs;
        private long accumulatedSessionSeconds;
        private long updatedAtMillis = System.currentTimeMillis();

        public Builder() {
        }

        public Builder(@NonNull TrackingStateEntity existing) {
            this.id = existing.id;
            this.lastCumulativeSteps = existing.lastCumulativeSteps;
            this.bootSessionMarker = existing.bootSessionMarker;
            this.baselineInitialized = existing.baselineInitialized;
            this.lastEventTimestamp = existing.lastEventTimestamp;
            this.sessionActive = existing.sessionActive;
            this.sessionStartMonotonicMs = existing.sessionStartMonotonicMs;
            this.lastMovementMonotonicMs = existing.lastMovementMonotonicMs;
            this.accumulatedSessionSeconds = existing.accumulatedSessionSeconds;
            this.updatedAtMillis = existing.updatedAtMillis;
        }

        public Builder setId(int id) {
            this.id = id;
            return this;
        }

        public Builder setLastCumulativeSteps(long lastCumulativeSteps) {
            this.lastCumulativeSteps = Math.max(0, lastCumulativeSteps);
            return this;
        }

        public Builder setBootSessionMarker(long bootSessionMarker) {
            this.bootSessionMarker = bootSessionMarker;
            return this;
        }

        public Builder setBaselineInitialized(boolean baselineInitialized) {
            this.baselineInitialized = baselineInitialized;
            return this;
        }

        public Builder setLastEventTimestamp(long lastEventTimestamp) {
            this.lastEventTimestamp = lastEventTimestamp;
            return this;
        }

        public Builder setSessionActive(boolean sessionActive) {
            this.sessionActive = sessionActive;
            return this;
        }

        public Builder setSessionStartMonotonicMs(long sessionStartMonotonicMs) {
            this.sessionStartMonotonicMs = sessionStartMonotonicMs;
            return this;
        }

        public Builder setLastMovementMonotonicMs(long lastMovementMonotonicMs) {
            this.lastMovementMonotonicMs = lastMovementMonotonicMs;
            return this;
        }

        public Builder setAccumulatedSessionSeconds(long accumulatedSessionSeconds) {
            this.accumulatedSessionSeconds = Math.max(0, accumulatedSessionSeconds);
            return this;
        }

        public Builder setUpdatedAtMillis(long updatedAtMillis) {
            this.updatedAtMillis = updatedAtMillis;
            return this;
        }

        public TrackingStateEntity build() {
            return new TrackingStateEntity(
                    id,
                    lastCumulativeSteps,
                    bootSessionMarker,
                    baselineInitialized,
                    lastEventTimestamp,
                    sessionActive,
                    sessionStartMonotonicMs,
                    lastMovementMonotonicMs,
                    accumulatedSessionSeconds,
                    updatedAtMillis
            );
        }
    }
}

