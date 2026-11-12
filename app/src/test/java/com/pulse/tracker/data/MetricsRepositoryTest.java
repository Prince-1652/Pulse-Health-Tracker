package com.pulse.tracker.data;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.data.local.PulseDatabase;
import com.pulse.tracker.data.local.dao.DailyMetricsDao;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.MetricsRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests verifying MetricsRepository 7-day zero-fill logic, monthly analytics,
 * and leap-year / calendar month boundary handling.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MetricsRepositoryTest {

    private PulseDatabase database;
    private DailyMetricsDao dao;
    private MetricsRepository repository;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, PulseDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.dailyMetricsDao();
        repository = new MetricsRepository(database, Runnable::run);
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void testGetLast7DaysMetrics_emptyDatabase_synthesizes7ZeroStepEntries() {
        LocalDate endDate = LocalDate.of(2026, 11, 7);
        List<DailyMetricsEntity> result = repository.getLast7DaysMetricsSync(endDate, 10000L);

        assertEquals(7, result.size());
        assertEquals("2026-11-01", result.get(0).getDate());
        assertEquals("2026-11-02", result.get(1).getDate());
        assertEquals("2026-11-03", result.get(2).getDate());
        assertEquals("2026-11-04", result.get(3).getDate());
        assertEquals("2026-11-05", result.get(4).getDate());
        assertEquals("2026-11-06", result.get(5).getDate());
        assertEquals("2026-11-07", result.get(6).getDate());

        for (DailyMetricsEntity entry : result) {
            assertEquals(0L, entry.getSteps());
            assertEquals(0.0, entry.getDistanceMeters(), 0.001);
            assertEquals(10000L, entry.getStepGoal());
        }
    }

    @Test
    public void testGetLast7DaysMetrics_sparseData_fillsMissingDays() {
        LocalDate endDate = LocalDate.of(2026, 11, 7);

        // Populate only days 1, 4, 7
        dao.insertOrUpdate(new DailyMetricsEntity("2026-11-01", 5000L, 3500.0, 1800L, 160.0, 10000L, 1L));
        dao.insertOrUpdate(new DailyMetricsEntity("2026-11-04", 8000L, 6000.0, 3000L, 270.0, 10000L, 2L));
        dao.insertOrUpdate(new DailyMetricsEntity("2026-11-07", 12000L, 9000.0, 4200L, 400.0, 10000L, 3L));

        List<DailyMetricsEntity> result = repository.getLast7DaysMetricsSync(endDate, 8000L);

        assertEquals(7, result.size());
        // Day 1 (Nov 1)
        assertEquals("2026-11-01", result.get(0).getDate());
        assertEquals(5000L, result.get(0).getSteps());

        // Day 2 (Nov 2) - synthesized zero
        assertEquals("2026-11-02", result.get(1).getDate());
        assertEquals(0L, result.get(1).getSteps());
        assertEquals(8000L, result.get(1).getStepGoal());

        // Day 3 (Nov 3) - synthesized zero
        assertEquals("2026-11-03", result.get(2).getDate());
        assertEquals(0L, result.get(2).getSteps());

        // Day 4 (Nov 4) - real data
        assertEquals("2026-11-04", result.get(3).getDate());
        assertEquals(8000L, result.get(3).getSteps());

        // Day 7 (Nov 7) - real data
        assertEquals("2026-11-07", result.get(6).getDate());
        assertEquals(12000L, result.get(6).getSteps());
    }

    @Test
    public void testYearBoundaryTransition_spansDecemberToJanuaryCorrectly() {
        // Range: 2025-12-28 to 2026-01-03
        LocalDate endDate = LocalDate.of(2026, 1, 3);
        dao.insertOrUpdate(new DailyMetricsEntity("2025-12-31", 10000L, 7500.0, 3600L, 350.0, 10000L, 1L));
        dao.insertOrUpdate(new DailyMetricsEntity("2026-01-01", 6000L, 4500.0, 2000L, 200.0, 10000L, 2L));

        List<DailyMetricsEntity> result = repository.getLast7DaysMetricsSync(endDate, 10000L);

        assertEquals(7, result.size());
        assertEquals("2025-12-28", result.get(0).getDate());
        assertEquals("2025-12-31", result.get(3).getDate());
        assertEquals(10000L, result.get(3).getSteps());

        assertEquals("2026-01-01", result.get(4).getDate());
        assertEquals(6000L, result.get(4).getSteps());
        assertEquals("2026-01-03", result.get(6).getDate());
    }

    @Test
    public void testLeapYearMonthBoundaries() {
        // Leap year: February 2024 has 29 days
        YearMonth feb2024 = YearMonth.of(2024, 2);
        assertEquals(29, feb2024.lengthOfMonth());

        // Non-leap year: February 2026 has 28 days
        YearMonth feb2026 = YearMonth.of(2026, 2);
        assertEquals(28, feb2026.lengthOfMonth());

        // Test 7-day window ending on March 1, 2024 (spans into Feb 29)
        LocalDate endOfLeapFeb = LocalDate.of(2024, 3, 2);
        List<DailyMetricsEntity> leapWindow = repository.getLast7DaysMetricsSync(endOfLeapFeb, 10000L);
        assertEquals(7, leapWindow.size());
        assertEquals("2024-02-25", leapWindow.get(0).getDate());
        assertEquals("2024-02-29", leapWindow.get(4).getDate());
        assertEquals("2024-03-01", leapWindow.get(5).getDate());
        assertEquals("2024-03-02", leapWindow.get(6).getDate());
    }

    @Test
    public void testCalculateMonthlyAnalytics_sumsAndCalculatesPace() {
        dao.insertOrUpdate(new DailyMetricsEntity("2026-11-01", 6000L, 4500.0, 2700L, 200.0, 10000L, 1L));
        dao.insertOrUpdate(new DailyMetricsEntity("2026-11-02", 9000L, 6750.0, 4050L, 300.0, 10000L, 2L));

        MetricsRepository.MonthlyAnalytics analytics = repository.calculateMonthlyAnalyticsSync("2026-11");

        assertEquals(15000L, analytics.getTotalSteps());
        assertEquals(11250.0, analytics.getTotalDistanceMeters(), 0.001);
        assertEquals(6750L, analytics.getTotalActiveSeconds());
        assertEquals(500.0, analytics.getTotalCalories(), 0.001);

        // Distance in km = 11.25 km
        // Active time in min = 6750 / 60 = 112.5 min
        // Pace = 112.5 / 11.25 = 10.0 min/km
        assertEquals(10.0, analytics.getAveragePaceMinPerKm(), 0.01);

        assertNotNull(analytics.getPersonalBest());
        assertEquals(9000L, analytics.getPersonalBest().getSteps());
        assertEquals("2026-11-02", analytics.getPersonalBest().getDate());
    }

    @Test
    public void testCalculateMonthlyAnalytics_zeroDistance_handlesZeroPaceCleanly() {
        MetricsRepository.MonthlyAnalytics emptyAnalytics = repository.calculateMonthlyAnalyticsSync("2026-11");

        assertEquals(0L, emptyAnalytics.getTotalSteps());
        assertEquals(0.0, emptyAnalytics.getTotalDistanceMeters(), 0.001);
        assertEquals(0L, emptyAnalytics.getTotalActiveSeconds());
        assertEquals(0.0, emptyAnalytics.getAveragePaceMinPerKm(), 0.001);
        assertNull(emptyAnalytics.getPersonalBest());
    }

    @Test
    public void testCalculateMonthlyAnalytics_missingSubMetrics_usesFallbacksWhenStepsPresent() {
        dao.insertOrUpdate(new DailyMetricsEntity("2026-11-01", 2000L, 0.0, 0L, 0.0, 10000L, 1L));

        MetricsRepository.MonthlyAnalytics analytics = repository.calculateMonthlyAnalyticsSync("2026-11");

        assertEquals(2000L, analytics.getTotalSteps());

        // Fallback distance: 2000 steps * 70.0cm = 1400.0m
        assertEquals(1400.0, analytics.getTotalDistanceMeters(), 0.001);

        // Fallback active seconds: Math.max(1, Math.round(2000 * 0.55)) = 1100s
        assertEquals(1100L, analytics.getTotalActiveSeconds());

        // Fallback calories: CalorieCalculator.calculateStepCalories(2000, 70.0, 70.0)
        double expectedKcal = com.pulse.tracker.domain.CalorieCalculator.calculateStepCalories(2000L, 70.0, 70.0);
        assertEquals(expectedKcal, analytics.getTotalCalories(), 0.001);

        // Average pace: 1100s / 1.4km = 785.71s / km = 13.095 min/km
        double expectedPace = (1100.0 / 60.0) / 1.4;
        assertEquals(expectedPace, analytics.getAveragePaceMinPerKm(), 0.01);
    }
}

