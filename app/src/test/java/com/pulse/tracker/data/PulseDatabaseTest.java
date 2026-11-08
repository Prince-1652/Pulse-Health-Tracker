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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;

/**
 * In-memory Room database tests verifying atomic transactions,
 * date-range queries, personal best resolution, and zero-negative steps enforcement.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PulseDatabaseTest {

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
    public void testAtomicStepDelta_createsAndAccumulatesMetricsWithCheckpoint() {
        TrackingStateEntity checkpoint1 = new TrackingStateEntity.Builder()
                .setId(1)
                .setLastCumulativeSteps(1500L)
                .setBootSessionMarker(100L)
                .setBaselineInitialized(true)
                .setLastEventTimestamp(1000L)
                .build();

        // First batch of the day
        repository.applyStepDeltaAtomicSync(
                "2026-11-07",
                1500L,
                1125.0,
                720L,
                65.0,
                10000L,
                checkpoint1
        );

        DailyMetricsEntity day1 = dailyMetricsDao.getMetricsForDate("2026-11-07");
        assertNotNull(day1);
        assertEquals(1500L, day1.getSteps());
        assertEquals(1125.0, day1.getDistanceMeters(), 0.001);
        assertEquals(720L, day1.getActiveSeconds());
        assertEquals(65.0, day1.getActiveCaloriesKcal(), 0.001);
        assertEquals(10000L, day1.getStepGoal());

        TrackingStateEntity savedState1 = trackingStateDao.getTrackingState();
        assertNotNull(savedState1);
        assertEquals(1500L, savedState1.getLastCumulativeSteps());
        assertTrue(savedState1.isBaselineInitialized());

        // Second batch on the same day
        TrackingStateEntity checkpoint2 = checkpoint1.toBuilder()
                .setLastCumulativeSteps(2200L)
                .setLastEventTimestamp(2000L)
                .build();

        repository.applyStepDeltaAtomicSync(
                "2026-11-07",
                700L,
                525.0,
                300L,
                30.0,
                10000L,
                checkpoint2
        );

        DailyMetricsEntity day1Updated = dailyMetricsDao.getMetricsForDate("2026-11-07");
        assertNotNull(day1Updated);
        assertEquals(2200L, day1Updated.getSteps());
        assertEquals(1650.0, day1Updated.getDistanceMeters(), 0.001);
        assertEquals(1020L, day1Updated.getActiveSeconds());
        assertEquals(95.0, day1Updated.getActiveCaloriesKcal(), 0.001);

        TrackingStateEntity savedState2 = trackingStateDao.getTrackingState();
        assertNotNull(savedState2);
        assertEquals(2200L, savedState2.getLastCumulativeSteps());
    }

    @Test
    public void testZeroNegativeSteps_clampedAtZero() {
        // Initial 500 steps
        repository.applyStepDeltaAtomicSync(
                "2026-11-07",
                500L,
                350.0,
                200L,
                20.0,
                10000L,
                null
        );

        // Apply negative delta exceeding current value
        repository.applyStepDeltaAtomicSync(
                "2026-11-07",
                -2000L,
                -1000.0,
                -500L,
                -50.0,
                10000L,
                null
        );

        DailyMetricsEntity result = dailyMetricsDao.getMetricsForDate("2026-11-07");
        assertNotNull(result);
        assertEquals(0L, result.getSteps());
        assertEquals(0.0, result.getDistanceMeters(), 0.001);
        assertEquals(0L, result.getActiveSeconds());
        assertEquals(0.0, result.getActiveCaloriesKcal(), 0.001);
    }

    @Test
    public void testDateRangeQueries_orderedAscending() {
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-01", 3000L, 2000.0, 1000L, 100.0, 10000L, 1L));
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-05", 8000L, 6000.0, 3000L, 300.0, 10000L, 2L));
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-03", 5000L, 3500.0, 1800L, 180.0, 10000L, 3L));
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-10", 12000L, 9000.0, 4500L, 450.0, 10000L, 4L));

        List<DailyMetricsEntity> range = dailyMetricsDao.getMetricsBetweenDates("2026-11-02", "2026-11-06");
        assertEquals(2, range.size());
        assertEquals("2026-11-03", range.get(0).getDate());
        assertEquals("2026-11-05", range.get(1).getDate());
    }

    @Test
    public void testMonthPrefixQuery() {
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-10-31", 7000L, 5000.0, 2500L, 250.0, 10000L, 1L));
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-01", 6000L, 4500.0, 2200L, 220.0, 10000L, 2L));
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-02", 9500L, 7000.0, 3500L, 350.0, 10000L, 3L));
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-12-01", 4000L, 3000.0, 1500L, 150.0, 10000L, 4L));

        List<DailyMetricsEntity> nov = dailyMetricsDao.getMetricsForMonth("2026-11%");
        assertEquals(2, nov.size());
        assertEquals("2026-11-01", nov.get(0).getDate());
        assertEquals("2026-11-02", nov.get(1).getDate());
    }

    @Test
    public void testPersonalBestQuery() {
        assertNull(dailyMetricsDao.getPersonalBest());

        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-01", 5000L, 3500.0, 1800L, 180.0, 10000L, 1L));
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-02", 14500L, 11000.0, 5200L, 520.0, 10000L, 2L));
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-03", 9000L, 6500.0, 3100L, 310.0, 10000L, 3L));

        DailyMetricsEntity pb = dailyMetricsDao.getPersonalBest();
        assertNotNull(pb);
        assertEquals("2026-11-02", pb.getDate());
        assertEquals(14500L, pb.getSteps());
    }
}

