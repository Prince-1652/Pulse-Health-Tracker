package com.pulse.tracker.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pulse.tracker.data.local.entity.TrackingStateEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Pure domain step calculation processor.
 * Computes step deltas from low-power hardware step counter sensor events,
 * converts monotonic event timestamps to local calendar dates,
 * and maintains continuous tracking across device reboots and service restarts.
 */
public class StepCounterProcessor {

    /**
     * Maximum plausible delta in a single sensor delivery batch (e.g. 50,000 steps).
     * Guards against corrupted driver events or massive integer jumps without inventing steps.
     */
    public static final long MAX_PLAUSIBLE_SINGLE_DELTA = 50_000L;

    public StepCounterProcessor() {
    }

    /**
     * Processes a cumulative step counter event.
     *
     * @param incomingCumulativeSteps        Raw cumulative step count reported by Sensor.TYPE_STEP_COUNTER.
     * @param eventMonotonicTimestampNanos   Monotonic timestamp of the sensor event in nanoseconds (SensorEvent.timestamp).
     * @param currentBootSessionMarker       Boot session identifier (e.g. boot time ms or kernel boot ID).
     * @param currentWallTimeMs              Current wall-clock time in milliseconds (System.currentTimeMillis()).
     * @param elapsedRealtimeNanos           Current elapsed realtime in nanoseconds (SystemClock.elapsedRealtimeNanos()).
     * @param zoneId                         Time zone for local date assignment (defaults to systemDefault if null).
     * @param persistedState                 Last persisted tracking state checkpoint (or null if first run).
     * @return StepProcessingResult containing step delta, target date string, and updated tracking state.
     */
    @NonNull
    public StepProcessingResult processStepEvent(
            long incomingCumulativeSteps,
            long eventMonotonicTimestampNanos,
            long currentBootSessionMarker,
            long currentWallTimeMs,
            long elapsedRealtimeNanos,
            @Nullable ZoneId zoneId,
            @Nullable TrackingStateEntity persistedState) {

        long safeCumulative = Math.max(0, incomingCumulativeSteps);
        ZoneId effectiveZoneId = (zoneId != null) ? zoneId : ZoneId.systemDefault();

        // 1. Calculate event wall-clock time from monotonic event timestamp
        long eventWallTimeMs;
        if (eventMonotonicTimestampNanos <= 0 || elapsedRealtimeNanos <= 0) {
            eventWallTimeMs = currentWallTimeMs;
        } else {
            long ageNanos = elapsedRealtimeNanos - eventMonotonicTimestampNanos;
            if (ageNanos < 0) {
                // Sensor event timestamp is slightly ahead of elapsedRealtime due to clock jitter
                eventWallTimeMs = currentWallTimeMs;
            } else {
                eventWallTimeMs = currentWallTimeMs - (ageNanos / 1_000_000L);
                if (eventWallTimeMs > currentWallTimeMs) {
                    eventWallTimeMs = currentWallTimeMs;
                }
            }
        }

        // 2. Derive target local calendar date (yyyy-MM-dd)
        LocalDate localDate = Instant.ofEpochMilli(eventWallTimeMs)
                .atZone(effectiveZoneId)
                .toLocalDate();
        String targetDate = localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 3. First-ever activation / uncalibrated state
        if (persistedState == null || !persistedState.isBaselineInitialized()) {
            TrackingStateEntity.Builder builder = (persistedState != null)
                    ? persistedState.toBuilder()
                    : new TrackingStateEntity.Builder();

            TrackingStateEntity newState = builder
                    .setBaselineInitialized(true)
                    .setLastCumulativeSteps(safeCumulative)
                    .setBootSessionMarker(currentBootSessionMarker)
                    .setLastEventTimestamp(eventWallTimeMs)
                    .setUpdatedAtMillis(currentWallTimeMs)
                    .build();

            return new StepProcessingResult(0L, targetDate, newState, true);
        }

        // 4. Baseline is already established. Detect counter reset or device reboot.
        long previousCumulative = persistedState.getLastCumulativeSteps();
        long previousBootMarker = persistedState.getBootSessionMarker();

        boolean bootChanged = (currentBootSessionMarker != 0L && previousBootMarker != 0L
                && currentBootSessionMarker != previousBootMarker);
        boolean counterReset = (safeCumulative < previousCumulative);

        long stepDelta;
        if (bootChanged || counterReset) {
            // Device rebooted or hardware counter rolled over / reset to zero:
            // Re-baseline safely. All cumulative steps since this new boot are counted as new steps.
            stepDelta = safeCumulative;
        } else {
            // Normal event in same boot session
            long delta = safeCumulative - previousCumulative;
            if (delta < 0) {
                stepDelta = 0L;
            } else if (delta > MAX_PLAUSIBLE_SINGLE_DELTA) {
                // Implausible sensor anomaly: recover without inventing phantom steps
                stepDelta = 0L;
            } else {
                stepDelta = delta;
            }
        }

        // Guard against any negative delta
        stepDelta = Math.max(0, stepDelta);

        TrackingStateEntity newState = persistedState.toBuilder()
                .setBaselineInitialized(true)
                .setLastCumulativeSteps(safeCumulative)
                .setBootSessionMarker(currentBootSessionMarker)
                .setLastEventTimestamp(eventWallTimeMs)
                .setUpdatedAtMillis(currentWallTimeMs)
                .build();

        return new StepProcessingResult(stepDelta, targetDate, newState, false);
    }
}

