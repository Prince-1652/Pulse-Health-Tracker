package com.pulse.tracker.domain;

import com.pulse.tracker.data.local.entity.TrackingStateEntity;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for ActivitySessionCalculator verifying the 10s confirmation threshold,
 * 30s inactivity timeout (tail included), single step sessions, service restart gap capping,
 * and midnight crossover duration splitting.
 */
public class ActivitySessionCalculatorTest {

    private ActivitySessionCalculator calculator;
    private ZoneId testZoneId;

    @Before
    public void setUp() {
        calculator = new ActivitySessionCalculator();
        testZoneId = ZoneOffset.UTC;
    }

    @Test
    public void testCandidateSession_4sVs5sConfirmation() {
        long wallBase = 1_000_000L;

        // t = 0: first movement begins candidate
        ActivitySessionCalculator.SessionUpdate u1 = calculator.onMovement(0L, wallBase, testZoneId);
        // Monotonic timestamp <= 0 is rejected safely, so use 1000L as start
        ActivitySessionCalculator.SessionUpdate start = calculator.onMovement(1000L, wallBase, testZoneId);
        assertFalse(start.isSessionActive());
        assertTrue(start.isCandidateActive());
        assertEquals(0L, start.getActiveSecondsDelta());

        // t = 5,000ms (4s elapsed since start 1000ms)
        ActivitySessionCalculator.SessionUpdate u4s = calculator.onMovement(5_000L, wallBase + 4000L, testZoneId);
        // 4s is less than 5s threshold: still unconfirmed candidate!
        assertFalse(u4s.isSessionActive());
        assertTrue(u4s.isCandidateActive());
        assertFalse(u4s.isSessionJustConfirmed());
        assertEquals(0L, u4s.getActiveSecondsDelta());

        // Now advance to t = 6,000ms (5s elapsed since start 1000ms)
        ActivitySessionCalculator.SessionUpdate u5s = calculator.onMovement(6_000L, wallBase + 5_000L, testZoneId);
        // 5s reached: CONFIRMED!
        assertTrue(u5s.isSessionActive());
        assertFalse(u5s.isCandidateActive());
        assertTrue(u5s.isSessionJustConfirmed());
        // Candidate start time included: 5 full seconds credited
        assertEquals(5L, u5s.getActiveSecondsDelta());
    }

    @Test
    public void testCandidateSession_batchedSteps_confirmsImmediately() {
        long wallBase = 1_000_000L;

        // Batched movement steps (>= 5 steps) confirms session immediately
        ActivitySessionCalculator.SessionUpdate start = calculator.onMovement(1000L, wallBase, testZoneId, 8L);
        assertTrue(start.isSessionActive());
        assertFalse(start.isCandidateActive());
        assertTrue(start.isSessionJustConfirmed());
    }

    @Test
    public void testCandidateSession_timeoutCreditsCandidateMovementDuration() {
        long wallBase = 1_000_000L;

        // Candidate starts at 1000ms
        calculator.onMovement(1000L, wallBase, testZoneId);
        // Movement event at 4000ms (3s duration, less than 5s threshold)
        calculator.onMovement(4000L, wallBase + 3000L, testZoneId);

        // Inactivity timeout occurs 30s after last movement (34_000ms)
        ActivitySessionCalculator.SessionUpdate timeout = calculator.checkInactivityTimeout(
                34_000L, wallBase + 33_000L, testZoneId
        );

        // Actual movement duration of candidate (4000 - 1000 = 3s) is credited instead of discarded
        assertFalse(timeout.isSessionActive());
        assertFalse(timeout.isCandidateActive());
        assertEquals(3L, timeout.getActiveSecondsDelta());
        assertEquals(1, timeout.getDateSplits().size());
    }

    @Test
    public void testInactivityTimeout_29sVs30s() {
        long wallBase = 1_000_000L;

        // Start and confirm session at t = 11_000ms (10s active)
        calculator.onMovement(1000L, wallBase, testZoneId);
        calculator.onMovement(11_000L, wallBase + 10_000L, testZoneId);
        assertTrue(calculator.isSessionActive());

        // Check timeout at 29s of inactivity (11_000 + 29_000 = 40_000ms)
        ActivitySessionCalculator.SessionUpdate u29s = calculator.checkInactivityTimeout(
                40_000L, wallBase + 39_000L, testZoneId
        );
        // 29s elapsed: session remains ACTIVE (not timed out)
        assertTrue(u29s.isSessionActive());
        assertFalse(u29s.isSessionJustEnded());
        assertEquals(0L, u29s.getActiveSecondsDelta());

        // Check timeout at 30s of inactivity (11_000 + 30_000 = 41_000ms)
        ActivitySessionCalculator.SessionUpdate u30s = calculator.checkInactivityTimeout(
                41_000L, wallBase + 40_000L, testZoneId
        );
        // 30s elapsed: session ENDS and credits 30s tail
        assertFalse(u30s.isSessionActive());
        assertTrue(u30s.isSessionJustEnded());
        assertEquals(30L, u30s.getActiveSecondsDelta());
    }

    @Test
    public void testSingleStepSession_neverConfirmed() {
        long wallBase = 1_000_000L;

        // Single step event
        ActivitySessionCalculator.SessionUpdate start = calculator.onMovement(1000L, wallBase, testZoneId);
        assertTrue(start.isCandidateActive());
        assertFalse(start.isSessionActive());

        // Inactivity timeout occurs 30s later without any more steps
        ActivitySessionCalculator.SessionUpdate timeout = calculator.checkInactivityTimeout(
                31_000L, wallBase + 30_000L, testZoneId
        );

        // Candidate discarded cleanly; zero active seconds credited
        assertFalse(timeout.isSessionActive());
        assertFalse(timeout.isCandidateActive());
        assertFalse(timeout.isSessionJustEnded());
        assertEquals(0L, timeout.getActiveSecondsDelta());
        assertTrue(timeout.getDateSplits().isEmpty());
    }

    @Test
    public void testServiceRestart_capsLongDowntimeGaps() {
        long lastMovementMono = 50_000L;
        long lastMovementWall = 1_700_000_000_000L;

        // Persisted state indicates an active session was running before service shutdown
        TrackingStateEntity savedState = new TrackingStateEntity.Builder()
                .setSessionActive(true)
                .setSessionStartMonotonicMs(40_000L)
                .setLastMovementMonotonicMs(lastMovementMono)
                .setAccumulatedSessionSeconds(10L)
                .setUpdatedAtMillis(lastMovementWall)
                .build();

        // Service restarts 2 hours (7,200,000ms) later
        long restartMono = lastMovementMono + 7_200_000L;
        long restartWall = lastMovementWall + 7_200_000L;

        ActivitySessionCalculator.SessionUpdate update = calculator.restoreState(
                savedState, restartMono, restartWall, testZoneId
        );

        // Downtime gap is capped at maximum 30s tail! 2 hours of downtime are NEVER counted as walking.
        assertFalse(update.isSessionActive());
        assertTrue(update.isSessionJustEnded());
        assertEquals(30L, update.getActiveSecondsDelta());
        assertFalse(calculator.isSessionActive());
    }

    @Test
    public void testServiceRestart_quickRestartContinuesSession() {
        long lastMovementMono = 50_000L;
        long lastMovementWall = 1_700_000_000_000L;

        TrackingStateEntity savedState = new TrackingStateEntity.Builder()
                .setSessionActive(true)
                .setSessionStartMonotonicMs(40_000L)
                .setLastMovementMonotonicMs(lastMovementMono)
                .setAccumulatedSessionSeconds(10L)
                .setUpdatedAtMillis(lastMovementWall)
                .build();

        // Service restarts 5 seconds (5000ms) later (< 30s)
        long restartMono = lastMovementMono + 5000L;
        long restartWall = lastMovementWall + 5000L;

        ActivitySessionCalculator.SessionUpdate restore = calculator.restoreState(
                savedState, restartMono, restartWall, testZoneId
        );

        // Session is restored as active
        assertTrue(restore.isSessionActive());
        assertEquals(0L, restore.getActiveSecondsDelta());
        assertTrue(calculator.isSessionActive());

        // Movement arrives at t = 58_000ms (8s after last movement): session continues
        ActivitySessionCalculator.SessionUpdate nextStep = calculator.onMovement(
                58_000L, restartWall + 3000L, testZoneId
        );
        assertTrue(nextStep.isSessionActive());
        assertEquals(8L, nextStep.getActiveSecondsDelta());
    }

    @Test
    public void testMidnightCrossover_splitsDurationBetweenDates() {
        // Start: 2026-11-07 23:59:45 UTC (15s before midnight)
        LocalDateTime startDt = LocalDateTime.of(2026, 11, 7, 23, 59, 45);
        long startWallMs = startDt.toInstant(ZoneOffset.UTC).toEpochMilli();

        // End: 2026-11-08 00:00:10 UTC (10s after midnight)
        LocalDateTime endDt = LocalDateTime.of(2026, 11, 8, 0, 0, 10);
        long endWallMs = endDt.toInstant(ZoneOffset.UTC).toEpochMilli();

        Map<String, Long> splits = ActivitySessionCalculator.splitDurationByDate(startWallMs, endWallMs, testZoneId);

        assertEquals(2, splits.size());
        assertEquals(Long.valueOf(15L), splits.get("2026-11-07"));
        assertEquals(Long.valueOf(10L), splits.get("2026-11-08"));
    }

    @Test
    public void testContinuousWalkingSession_accumulatesSecondsCorrectly() {
        long wall = 1_000_000L;

        // t = 1000: Start candidate
        calculator.onMovement(1000L, wall, testZoneId);

        // t = 11_000: Confirm session (+10s)
        ActivitySessionCalculator.SessionUpdate u1 = calculator.onMovement(11_000L, wall + 10_000L, testZoneId);
        assertEquals(10L, u1.getActiveSecondsDelta());

        // t = 21_000: Continuous movement (+10s)
        ActivitySessionCalculator.SessionUpdate u2 = calculator.onMovement(21_000L, wall + 20_000L, testZoneId);
        assertEquals(10L, u2.getActiveSecondsDelta());

        // t = 36_000: Movement after 15s pause (+15s)
        ActivitySessionCalculator.SessionUpdate u3 = calculator.onMovement(36_000L, wall + 35_000L, testZoneId);
        assertEquals(15L, u3.getActiveSecondsDelta());

        // Timeout 30s after last movement (t = 66_000): (+30s tail)
        ActivitySessionCalculator.SessionUpdate uEnd = calculator.checkInactivityTimeout(
                66_000L, wall + 65_000L, testZoneId
        );
        assertEquals(30L, uEnd.getActiveSecondsDelta());
        assertTrue(uEnd.isSessionJustEnded());

        // Total accumulated = 10 + 10 + 15 + 30 = 65 seconds
        assertEquals(65L, calculator.getAccumulatedSessionSeconds());
    }

    @Test
    public void testSyncToTrackingState() {
        calculator.onMovement(1000L, 1000L, testZoneId);
        calculator.onMovement(11_000L, 11_000L, testZoneId);

        TrackingStateEntity initial = TrackingStateEntity.createInitial();
        TrackingStateEntity synced = calculator.syncToTrackingState(initial, 12_000L);

        assertTrue(synced.isSessionActive());
        assertEquals(1000L, synced.getSessionStartMonotonicMs());
        assertEquals(11_000L, synced.getLastMovementMonotonicMs());
        assertEquals(10L, synced.getAccumulatedSessionSeconds());
        assertEquals(12_000L, synced.getUpdatedAtMillis());
    }
}
