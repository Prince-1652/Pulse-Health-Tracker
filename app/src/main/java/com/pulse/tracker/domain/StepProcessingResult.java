package com.pulse.tracker.domain;

import androidx.annotation.NonNull;

import com.pulse.tracker.data.local.entity.TrackingStateEntity;

import java.util.Objects;

/**
 * Immutable result container from StepCounterProcessor containing the calculated delta,
 * target local date string, updated checkpoint state, and baseline status flag.
 */
public class StepProcessingResult {

    private final long stepDelta;
    private final String targetDate;
    private final TrackingStateEntity updatedTrackingState;
    private final boolean isBaselineInit;

    public StepProcessingResult(long stepDelta,
                                @NonNull String targetDate,
                                @NonNull TrackingStateEntity updatedTrackingState,
                                boolean isBaselineInit) {
        this.stepDelta = Math.max(0, stepDelta);
        this.targetDate = Objects.requireNonNull(targetDate, "targetDate cannot be null");
        this.updatedTrackingState = Objects.requireNonNull(updatedTrackingState, "updatedTrackingState cannot be null");
        this.isBaselineInit = isBaselineInit;
    }

    public long getStepDelta() {
        return stepDelta;
    }

    @NonNull
    public String getTargetDate() {
        return targetDate;
    }

    @NonNull
    public TrackingStateEntity getUpdatedTrackingState() {
        return updatedTrackingState;
    }

    public boolean isBaselineInit() {
        return isBaselineInit;
    }

    @NonNull
    @Override
    public String toString() {
        return "StepProcessingResult{" +
                "stepDelta=" + stepDelta +
                ", targetDate='" + targetDate + '\'' +
                ", isBaselineInit=" + isBaselineInit +
                ", updatedTrackingState=" + updatedTrackingState +
                '}';
    }
}

