package com.pulse.tracker.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.pulse.tracker.data.local.entity.TrackingStateEntity;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Unit tests verifying StepCounterProcessor delta calculations, reboot/reset handling,
 * event wall-time conversion, and boundary invariants.
 */
public class StepCounterProcessorTest {

    private StepCounterProcessor processor;
    private ZoneId testZoneId;

    @Before
    public void setUp() {
        processor = new StepCounterProcessor();
        testZoneId = ZoneId.of("UTC");
    }

    @Test
    public void firstActivation_establishesBaseline_returnsZeroDelta() {
        long incomingCumulative = 12500L;
        long eventMonotonicNanos = 10_000_000_000L; // 10s monotonic
        long bootMarker = 100_000L;
        long wallTimeMs = 1700000000000L; // Fixed epoch ms
        long elapsedNanos = 10_000_000_000L;

        StepProcessingResult result = processor.processStepEvent(
                incomingCumulative,
                eventMonotonicNanos,
                bootMarker,
                wallTimeMs,
                elapsedNanos,
                testZoneId,
                null // null state -> uncalibrated
        );

        assertNotNull(result);
        assertEquals(0L, result.getStepDelta());
        assertTrue(result.isBaselineInit());

        TrackingStateEntity state = result.getUpdatedTrackingState();
        assertNotNull(state);
        assertTrue(state.isBaselineInitialized());
        assertEquals(12500L, state.getLastCumulativeSteps());
        assertEquals(bootMarker, state.getBootSessionMarker());
        assertEquals(wallTimeMs, state.getLastEventTimestamp());
    }

    @Test
    public void sameCounterEventTwice_producesZeroDelta() {
        long bootMarker = 100_000L;
        long wallTimeMs = 1700000000000L;
        long elapsedNanos = 10_000_000_000L;

        // Establish baseline at 100
        StepProcessingResult initResult = processor.processStepEvent(
                100L,
                elapsedNanos,
                bootMarker,
                wallTimeMs,
                elapsedNanos,
                testZoneId,
                null
        );

        TrackingStateEntity state1 = initResult.getUpdatedTrackingState();

        // Second event with same counter value (100)
        StepProcessingResult secondResult = processor.processStepEvent(
                100L,
                elapsedNanos + 1_000_000_000L,
                bootMarker,
                wallTimeMs + 1000L,
                elapsedNanos + 1_000_000_000L,
                testZoneId,
                state1
        );

        assertEquals(0L, secondResult.getStepDelta());
        assertFalse(secondResult.isBaselineInit());
        assertEquals(100L, secondResult.getUpdatedTrackingState().getLastCumulativeSteps());
    }

    @Test
    public void standardDelta_incrementsStepsAccurately() {
        long bootMarker = 100_000L;
        long wallTimeMs = 1700000000000L;
        long elapsedNanos = 10_000_000_000L;

        // Baseline at 100
        TrackingStateEntity state = TrackingStateEntity.createInitial().toBuilder()
                .setBaselineInitialized(true)
                .setLastCumulativeSteps(100L)
                .setBootSessionMarker(bootMarker)
                .setLastEventTimestamp(wallTimeMs)
                .build();

        // Incoming is 108
        StepProcessingResult result = processor.processStepEvent(
                108L,
                elapsedNanos + 5_000_000_000L,
                bootMarker,
                wallTimeMs + 5000L,
                elapsedNanos + 5_000_000_000L,
                testZoneId,
                state
        );

        assertEquals(8L, result.getStepDelta());
        assertFalse(result.isBaselineInit());
        assertEquals(108L, result.getUpdatedTrackingState().getLastCumulativeSteps());
    }

    @Test
    public void deviceReboot_withLowerCounter_rebaselinesSafelyWithoutNegativeDelta() {
        long oldBootMarker = 100_000L;
        long newBootMarker = 200_000L;
        long wallTimeMs = 1700000000000L;
        long elapsedNanos = 2_000_000_000L;

        // Persisted state from before reboot: 5,420 steps
        TrackingStateEntity preRebootState = TrackingStateEntity.createInitial().toBuilder()
                .setBaselineInitialized(true)
                .setLastCumulativeSteps(5420L)
                .setBootSessionMarker(oldBootMarker)
                .setLastEventTimestamp(wallTimeMs - 100_000L)
                .build();

        // Phone rebooted: counter reset to 15 (user walked 15 steps since reboot)
        StepProcessingResult rebootResult = processor.processStepEvent(
                15L,
                elapsedNanos,
                newBootMarker,
                wallTimeMs,
                elapsedNanos,
                testZoneId,
                preRebootState
        );

        // Steps since reboot = 15; no negative delta, no lost steps
        assertEquals(15L, rebootResult.getStepDelta());
        assertFalse(rebootResult.isBaselineInit());

        TrackingStateEntity postRebootState = rebootResult.getUpdatedTrackingState();
        assertEquals(15L, postRebootState.getLastCumulativeSteps());
        assertEquals(newBootMarker, postRebootState.getBootSessionMarker());

        // Subsequent event in same new boot session: counter goes to 23
        StepProcessingResult nextResult = processor.processStepEvent(
                23L,
                elapsedNanos + 3_000_000_000L,
                newBootMarker,
                wallTimeMs + 3000L,
                elapsedNanos + 3_000_000_000L,
                testZoneId,
                postRebootState
        );

        assertEquals(8L, nextResult.getStepDelta());
        assertEquals(23L, nextResult.getUpdatedTrackingState().getLastCumulativeSteps());
    }

    @Test
    public void sameBootProcessRestart_restoresBaselineAndDelta() {
        long bootMarker = 100_000L;
        long wallTimeMs = 1700000000000L;
        long elapsedNanos = 50_000_000_000L;

        // Persisted Room state when service was killed
        TrackingStateEntity checkpoint = TrackingStateEntity.createInitial().toBuilder()
                .setBaselineInitialized(true)
                .setLastCumulativeSteps(3200L)
                .setBootSessionMarker(bootMarker)
                .setLastEventTimestamp(wallTimeMs)
                .build();

        // Service restarted in same boot session, receives sensor event 3242
        StepProcessingResult result = processor.processStepEvent(
                3242L,
                elapsedNanos + 10_000_000_000L,
                bootMarker,
                wallTimeMs + 10_000L,
                elapsedNanos + 10_000_000_000L,
                testZoneId,
                checkpoint
        );

        assertEquals(42L, result.getStepDelta());
        assertEquals(3242L, result.getUpdatedTrackingState().getLastCumulativeSteps());
    }

    @Test
    public void timestampConversion_nearMidnightBoundary_correctlyAttributesYesterday() {
        // Construct wall time 5 seconds past midnight UTC: 2026-11-08 00:00:05.000
        Instant midnightPlus5 = Instant.parse("2026-11-08T00:00:05.000Z");
        long wallTimeMs = midnightPlus5.toEpochMilli();

        // Sensor event occurred 8 seconds ago (23:59:57 UTC on 2026-11-07)
        long elapsedRealtimeNanos = 100_000_000_000L; // 100s
        long eventMonotonicNanos = elapsedRealtimeNanos - (8_000_000_000L); // 92s

        long bootMarker = 1000L;
        TrackingStateEntity state = TrackingStateEntity.createInitial().toBuilder()
                .setBaselineInitialized(true)
                .setLastCumulativeSteps(1000L)
                .setBootSessionMarker(bootMarker)
                .setLastEventTimestamp(wallTimeMs - 20_000L)
                .build();

        StepProcessingResult result = processor.processStepEvent(
                1020L,
                eventMonotonicNanos,
                bootMarker,
                wallTimeMs,
                elapsedRealtimeNanos,
                testZoneId,
                state
        );

        // Date must be yesterday (2026-11-07) because the physical event occurred before midnight
        assertEquals("2026-11-07", result.getTargetDate());
        assertEquals(20L, result.getStepDelta());
    }

    @Test
    public void futureOrJitterTimestamp_clampedSafelyToCurrentWallTime() {
        long wallTimeMs = Instant.parse("2026-11-07T12:00:00.000Z").toEpochMilli();
        long elapsedRealtimeNanos = 50_000_000_000L;
        // Event timestamp 1 second in the future due to sensor clock sync jitter
        long eventMonotonicNanos = elapsedRealtimeNanos + 1_000_000_000L;

        TrackingStateEntity state = TrackingStateEntity.createInitial().toBuilder()
                .setBaselineInitialized(true)
                .setLastCumulativeSteps(500L)
                .setBootSessionMarker(1L)
                .build();

        StepProcessingResult result = processor.processStepEvent(
                510L,
                eventMonotonicNanos,
                1L,
                wallTimeMs,
                elapsedRealtimeNanos,
                testZoneId,
                state
        );

        assertEquals("2026-11-07", result.getTargetDate());
        assertEquals(10L, result.getStepDelta());
        assertEquals(wallTimeMs, result.getUpdatedTrackingState().getLastEventTimestamp());
    }

    @Test
    public void implausibleLargeStepJump_clampedWithoutInventingSteps() {
        long bootMarker = 1L;
        long wallTimeMs = 1700000000000L;
        long elapsedNanos = 10_000_000_000L;

        TrackingStateEntity state = TrackingStateEntity.createInitial().toBuilder()
                .setBaselineInitialized(true)
                .setLastCumulativeSteps(1000L)
                .setBootSessionMarker(bootMarker)
                .build();

        // Implausible sensor spike jumping by 1,000,000 steps
        StepProcessingResult result = processor.processStepEvent(
                1_001_000L,
                elapsedNanos,
                bootMarker,
                wallTimeMs,
                elapsedNanos,
                testZoneId,
                state
        );

        // Clamped: delta = 0, but checkpoint updated to new counter value
        assertEquals(0L, result.getStepDelta());
        assertEquals(1_001_000L, result.getUpdatedTrackingState().getLastCumulativeSteps());
    }
}

