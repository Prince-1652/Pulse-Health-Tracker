package com.pulse.tracker.data;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.data.local.PulseDatabase;
import com.pulse.tracker.data.local.dao.DailyMetricsDao;
import com.pulse.tracker.data.local.dao.TrackingStateDao;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.local.entity.TrackingStateEntity;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.domain.StepCounterProcessor;
import com.pulse.tracker.domain.StepProcessingResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying database edge cases (zero steps, midnight crossovers, date rollovers),
 * reboot resets, and atomic state checkpoint integrity.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EdgeCasesAndStorageTest {

    private PulseDatabase database;
    private DailyMetricsDao dailyMetricsDao;
    private TrackingStateDao trackingStateDao;
    private MetricsRepository repository;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, PulseDatabase.class)
                .allowMainThreadQueries()
                .build();
        dailyMetricsDao = database.dailyMetricsDao();
        trackingStateDao = database.trackingStateDao();
        repository = new MetricsRepository(database, Runnable::run);
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void testZeroStepsHandling_persistsAndReadsCleanlyWithoutNegativeNumbers() {
        String testDate = "2026-11-07";
        DailyMetricsEntity emptyEntity = DailyMetricsEntity.empty(testDate, 10000L);

        dailyMetricsDao.insertOrUpdate(emptyEntity);

        DailyMetricsEntity fetched = dailyMetricsDao.getMetricsForDate(testDate);
        assertNotNull(fetched);
        assertEquals(0L, fetched.getSteps());
        assertEquals(0.0, fetched.getDistanceMeters(), 0.001);
        assertEquals(0L, fetched.getActiveSeconds());
        assertEquals(0.0, fetched.getActiveCaloriesKcal(), 0.001);
        assertEquals(10000L, fetched.getStepGoal());
        assertTrue(fetched.getUpdatedAtMillis() > 0);
    }

    @Test
    public void testDateRolloverAndAtomicCheckpoint() {
        String day1 = "2026-11-07";
        String day2 = "2026-11-08";

        TrackingStateEntity checkpoint1 = new TrackingStateEntity.Builder()
                .setLastCumulativeSteps(5000L)
                .setBootSessionMarker(12345L)
                .setLastEventTimestamp(1000L)
                .setAccumulatedSessionSeconds(30L)
                .setSessionActive(true)
                .build();

        // Increment day 1
        repository.applyStepDeltaAtomicSync(day1, 500L, 380.0, 300L, 25.0, 10000L, checkpoint1);

        DailyMetricsEntity resultDay1 = dailyMetricsDao.getMetricsForDate(day1);
        assertNotNull(resultDay1);
        assertEquals(500L, resultDay1.getSteps());
        assertEquals(380.0, resultDay1.getDistanceMeters(), 0.001);

        TrackingStateEntity savedState = trackingStateDao.getTrackingState();
        assertNotNull(savedState);
        assertEquals(5000L, savedState.getLastCumulativeSteps());

        // Date rolls over to day 2
        TrackingStateEntity checkpoint2 = new TrackingStateEntity.Builder()
                .setLastCumulativeSteps(5200L)
                .setBootSessionMarker(12345L)
                .setLastEventTimestamp(2000L)
                .setAccumulatedSessionSeconds(15L)
                .setSessionActive(true)
                .build();

        repository.applyStepDeltaAtomicSync(day2, 200L, 150.0, 120L, 10.0, 10000L, checkpoint2);

        DailyMetricsEntity resultDay2 = dailyMetricsDao.getMetricsForDate(day2);
        assertNotNull(resultDay2);
        assertEquals(200L, resultDay2.getSteps());
        assertEquals(150.0, resultDay2.getDistanceMeters(), 0.001);

        // Day 1 remains untouched
        DailyMetricsEntity unchangedDay1 = dailyMetricsDao.getMetricsForDate(day1);
        assertNotNull(unchangedDay1);
        assertEquals(500L, unchangedDay1.getSteps());

        // Checkpoint is updated to Day 2 state
        TrackingStateEntity updatedCheckpoint = trackingStateDao.getTrackingState();
        assertNotNull(updatedCheckpoint);
        assertEquals(5200L, updatedCheckpoint.getLastCumulativeSteps());
    }

    @Test
    public void testMidnightCrossover_multiDateSplitWritesBothDaysCorrectly() {
        String date1 = "2026-11-07";
        String date2 = "2026-11-08";

        // Pre-populate date1 with some steps
        repository.applyStepDeltaAtomicSync(date1, 4000L, 3000.0, 1800L, 150.0, 10000L, null);

        // A walk begins at 23:59:45 and completes at 00:00:15
        // 15 seconds attributed to date1, 15 seconds attributed to date2
        repository.applyStepDeltaAtomicSync(date1, 20L, 15.0, 15L, 1.2, 10000L, null);
        repository.applyStepDeltaAtomicSync(date2, 25L, 18.0, 15L, 1.2, 10000L, null);

        DailyMetricsEntity entity1 = dailyMetricsDao.getMetricsForDate(date1);
        assertNotNull(entity1);
        assertEquals(4020L, entity1.getSteps());
        assertEquals(3015.0, entity1.getDistanceMeters(), 0.01);
        assertEquals(1815L, entity1.getActiveSeconds());

        DailyMetricsEntity entity2 = dailyMetricsDao.getMetricsForDate(date2);
        assertNotNull(entity2);
        assertEquals(25L, entity2.getSteps());
        assertEquals(18.0, entity2.getDistanceMeters(), 0.01);
        assertEquals(15L, entity2.getActiveSeconds());
    }

    @Test
    public void testRebootCounterResetHandling_handlesCounterDecreasingWithoutNegativeDeltas() {
        StepCounterProcessor processor = new StepCounterProcessor();
        ZoneId zone = ZoneOffset.UTC;

        // Step 1: Normal operation before reboot
        long bootMarkerBefore = 100000L;
        TrackingStateEntity stateBefore = new TrackingStateEntity.Builder()
                .setLastCumulativeSteps(4500L)
                .setBootSessionMarker(bootMarkerBefore)
                .setBaselineInitialized(true)
                .setLastEventTimestamp(5_000_000_000L)
                .build();

        // Phone reboots. Step counter resets to 12 steps (hardware sensor reboot reset)
        long bootMarkerAfter = 200000L; // New boot marker
        long rawStepsAfterReboot = 12L;  // Resets from 4500 to 12
        long eventTimestampNanos = 1_000_000_000L; // Monotonic uptime resets
        long wallTime = 1_700_001_000_000L;
        long elapsedRealtimeNanos = 1_500_000_000L;

        StepProcessingResult rebootResult = processor.processStepEvent(
                rawStepsAfterReboot,
                eventTimestampNanos,
                bootMarkerAfter,
                wallTime,
                elapsedRealtimeNanos,
                zone,
                stateBefore
        );

        // Crucial: Step delta is 12 (steps walked since reboot), no negative delta, and baseline is preserved
        assertEquals(12L, rebootResult.getStepDelta());
        assertFalse(rebootResult.isBaselineInit());

        TrackingStateEntity stateAfter = rebootResult.getUpdatedTrackingState();
        assertNotNull(stateAfter);
        assertEquals(12L, stateAfter.getLastCumulativeSteps());
        assertEquals(bootMarkerAfter, stateAfter.getBootSessionMarker());

        // Subsequent step event after reboot: rawSteps increases from 12 to 20 (+8 steps)
        StepProcessingResult nextResult = processor.processStepEvent(
                20L,
                2_000_000_000L,
                bootMarkerAfter,
                wallTime + 1000L,
                elapsedRealtimeNanos + 1_000_000_000L,
                zone,
                stateAfter
        );

        assertEquals(8L, nextResult.getStepDelta());
        assertFalse(nextResult.isBaselineInit());
    }
}
