package com.pulse.tracker.ui.history;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.data.local.PulseDatabase;
import com.pulse.tracker.data.local.dao.DailyMetricsDao;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests verifying HistoryViewModel month navigation across year boundaries,
 * leap year calendar day calculations, goal-reached indicator flags, monthly analytics aggregation,
 * and reactive selected-date updates.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HistoryViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private Application application;
    private PulseDatabase database;
    private DailyMetricsDao dailyMetricsDao;
    private MetricsRepository metricsRepository;
    private SettingsRepository settingsRepository;
    private SharedPreferences sharedPreferences;

    private HistoryViewModel viewModel;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(application, PulseDatabase.class)
                .allowMainThreadQueries()
                .build();
        dailyMetricsDao = database.dailyMetricsDao();
        metricsRepository = new MetricsRepository(database, Runnable::run);

        sharedPreferences = application.getSharedPreferences("test_history_prefs", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
        settingsRepository = new SettingsRepository(sharedPreferences);

        viewModel = new HistoryViewModel(application, metricsRepository, settingsRepository);
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void testMonthNavigation_acrossYearBoundaries() {
        // Start at December 2025
        viewModel.setSelectedMonth(YearMonth.of(2025, 12));
        assertEquals(YearMonth.of(2025, 12), viewModel.getSelectedMonth().getValue());

        // Next month transitions to January 2026 across year boundary
        viewModel.nextMonth();
        assertEquals(YearMonth.of(2026, 1), viewModel.getSelectedMonth().getValue());

        // Previous month transitions back to December 2025
        viewModel.previousMonth();
        assertEquals(YearMonth.of(2025, 12), viewModel.getSelectedMonth().getValue());

        // Previous month transitions to November 2025
        viewModel.previousMonth();
        assertEquals(YearMonth.of(2025, 11), viewModel.getSelectedMonth().getValue());
    }

    @Test
    public void testLeapYearHandling_februaryDaysCount() {
        AtomicReference<List<CalendarDayModel>> observedDays = new AtomicReference<>();
        Observer<List<CalendarDayModel>> observer = observedDays::set;
        viewModel.getCalendarDays().observeForever(observer);

        try {
            // Leap year: February 2024 has 29 days
            viewModel.setSelectedMonth(YearMonth.of(2024, 2));
            List<CalendarDayModel> days2024 = observedDays.get();
            assertNotNull(days2024);

            List<CalendarDayModel> inMonth2024 = new ArrayList<>();
            for (CalendarDayModel day : days2024) {
                if (day.isInMonth()) {
                    inMonth2024.add(day);
                }
            }
            assertEquals("Leap year Feb 2024 should have exactly 29 days", 29, inMonth2024.size());
            assertEquals(29, inMonth2024.get(28).getDayNumber());
            assertEquals(LocalDate.of(2024, 2, 29), inMonth2024.get(28).getDate());

            // Non-leap year: February 2025 has 28 days
            viewModel.setSelectedMonth(YearMonth.of(2025, 2));
            List<CalendarDayModel> days2025 = observedDays.get();
            assertNotNull(days2025);

            List<CalendarDayModel> inMonth2025 = new ArrayList<>();
            for (CalendarDayModel day : days2025) {
                if (day.isInMonth()) {
                    inMonth2025.add(day);
                }
            }
            assertEquals("Non-leap year Feb 2025 should have exactly 28 days", 28, inMonth2025.size());
            assertEquals(28, inMonth2025.get(27).getDayNumber());
            assertEquals(LocalDate.of(2025, 2, 28), inMonth2025.get(27).getDate());
        } finally {
            viewModel.getCalendarDays().removeObserver(observer);
        }
    }

    @Test
    public void testCalendarDays_goalReachedFlag() {
        AtomicReference<List<CalendarDayModel>> observedDays = new AtomicReference<>();
        Observer<List<CalendarDayModel>> observer = observedDays::set;
        viewModel.getCalendarDays().observeForever(observer);

        try {
            viewModel.setSelectedMonth(YearMonth.of(2026, 11));

            // Populate room metrics:
            // Nov 4: 10,000 steps with 8,000 goal -> Goal achieved
            dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity(
                    "2026-11-04", 10000L, 7500.0, 3600L, 350.0, 8000L, 1L));

            // Nov 5: 5,000 steps with 8,000 goal -> Goal NOT reached
            dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity(
                    "2026-11-05", 5000L, 3750.0, 1800L, 175.0, 8000L, 2L));

            // Nov 6: 0 steps with 8,000 goal -> Goal NOT reached (0 steps should never trigger goal)
            dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity(
                    "2026-11-06", 0L, 0.0, 0L, 0.0, 8000L, 3L));

            List<CalendarDayModel> days = observedDays.get();
            assertNotNull(days);

            CalendarDayModel day4 = null;
            CalendarDayModel day5 = null;
            CalendarDayModel day6 = null;

            for (CalendarDayModel m : days) {
                if (m.isInMonth()) {
                    if (m.getDayNumber() == 4) day4 = m;
                    if (m.getDayNumber() == 5) day5 = m;
                    if (m.getDayNumber() == 6) day6 = m;
                }
            }

            assertNotNull(day4);
            assertTrue("Day 4 (10,000 / 8,000) should have goal reached", day4.isGoalReached());
            assertEquals(10000L, day4.getSteps());
            assertEquals(8000L, day4.getStepGoal());

            assertNotNull(day5);
            assertFalse("Day 5 (5,000 / 8,000) should NOT have goal reached", day5.isGoalReached());
            assertEquals(5000L, day5.getSteps());

            assertNotNull(day6);
            assertFalse("Day 6 (0 / 8,000) should NOT have goal reached", day6.isGoalReached());
            assertEquals(0L, day6.getSteps());
        } finally {
            viewModel.getCalendarDays().removeObserver(observer);
        }
    }

    @Test
    public void testMonthlyAnalytics_emissionMatchesRoomData() {
        AtomicReference<MetricsRepository.MonthlyAnalytics> observedAnalytics = new AtomicReference<>();
        Observer<MetricsRepository.MonthlyAnalytics> observer = observedAnalytics::set;
        viewModel.getMonthlyAnalytics().observeForever(observer);

        try {
            viewModel.setSelectedMonth(YearMonth.of(2026, 11));

            // Nov 1: 6,000 steps, 4,500m, 2,700s active (45 min)
            dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity(
                    "2026-11-01", 6000L, 4500.0, 2700L, 200.0, 10000L, 1L));

            // Nov 2: 9,000 steps, 6,750m, 4,050s active (67.5 min)
            dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity(
                    "2026-11-02", 9000L, 6750.0, 4050L, 300.0, 10000L, 2L));

            // Refresh monthly analytics
            viewModel.refreshMonthlyAnalytics(YearMonth.of(2026, 11));

            MetricsRepository.MonthlyAnalytics analytics = observedAnalytics.get();
            assertNotNull(analytics);

            assertEquals(15000L, analytics.getTotalSteps());
            assertEquals(11250.0, analytics.getTotalDistanceMeters(), 0.001);
            assertEquals(6750L, analytics.getTotalActiveSeconds());
            assertEquals(500.0, analytics.getTotalCalories(), 0.001);

            // Distance in km = 11.25 km, Active minutes = 112.5 min -> Pace = 10.0 min/km
            assertEquals(10.0, analytics.getAveragePaceMinPerKm(), 0.01);

            assertNotNull(analytics.getPersonalBest());
            assertEquals(9000L, analytics.getPersonalBest().getSteps());
            assertEquals("2026-11-02", analytics.getPersonalBest().getDate());
        } finally {
            viewModel.getMonthlyAnalytics().removeObserver(observer);
        }
    }

    @Test
    public void testSelectedDateChange_updatesSelectedDayMetricsAndSelectionState() {
        AtomicReference<DailyMetricsEntity> observedMetrics = new AtomicReference<>();
        Observer<DailyMetricsEntity> metricsObserver = observedMetrics::set;
        viewModel.getSelectedDateMetrics().observeForever(metricsObserver);

        AtomicReference<List<CalendarDayModel>> observedDays = new AtomicReference<>();
        Observer<List<CalendarDayModel>> daysObserver = observedDays::set;
        viewModel.getCalendarDays().observeForever(daysObserver);

        try {
            viewModel.setSelectedMonth(YearMonth.of(2026, 11));

            dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity(
                    "2026-11-04", 8420L, 6300.0, 3600L, 280.0, 10000L, 1L));
            dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity(
                    "2026-11-12", 12000L, 9000.0, 4800L, 400.0, 10000L, 2L));

            // Select Nov 4
            viewModel.selectDate(LocalDate.of(2026, 11, 4));

            DailyMetricsEntity selectedEntity = observedMetrics.get();
            assertNotNull(selectedEntity);
            assertEquals("2026-11-04", selectedEntity.getDate());
            assertEquals(8420L, selectedEntity.getSteps());
            assertEquals(6300.0, selectedEntity.getDistanceMeters(), 0.01);

            // Check grid selection states
            List<CalendarDayModel> days = observedDays.get();
            assertNotNull(days);
            for (CalendarDayModel m : days) {
                if (m.isInMonth() && m.getDayNumber() == 4) {
                    assertTrue("Nov 4 must be selected", m.isSelected());
                } else if (m.isInMonth() && m.getDayNumber() == 12) {
                    assertFalse("Nov 12 must not be selected", m.isSelected());
                }
            }

            // Switch selection to Nov 12
            viewModel.selectDate(LocalDate.of(2026, 11, 12));

            selectedEntity = observedMetrics.get();
            assertNotNull(selectedEntity);
            assertEquals("2026-11-12", selectedEntity.getDate());
            assertEquals(12000L, selectedEntity.getSteps());
            assertEquals(9000.0, selectedEntity.getDistanceMeters(), 0.01);

            // Check grid selection updated
            days = observedDays.get();
            assertNotNull(days);
            for (CalendarDayModel m : days) {
                if (m.isInMonth() && m.getDayNumber() == 12) {
                    assertTrue("Nov 12 must now be selected", m.isSelected());
                } else if (m.isInMonth() && m.getDayNumber() == 4) {
                    assertFalse("Nov 4 must no longer be selected", m.isSelected());
                }
            }

            // Select day with no DB record (e.g. Nov 20) -> should emit safe empty representation
            viewModel.selectDate(LocalDate.of(2026, 11, 20));
            DailyMetricsEntity emptyEntity = observedMetrics.get();
            assertNotNull(emptyEntity);
            assertEquals("2026-11-20", emptyEntity.getDate());
            assertEquals(0L, emptyEntity.getSteps());
            assertEquals(0.0, emptyEntity.getDistanceMeters(), 0.01);
            assertEquals(0L, emptyEntity.getActiveSeconds());
        } finally {
            viewModel.getSelectedDateMetrics().removeObserver(metricsObserver);
            viewModel.getCalendarDays().removeObserver(daysObserver);
        }
    }
}

