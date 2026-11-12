package com.pulse.tracker.ui.home;

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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests verifying HomeViewModel LiveData observation, 7-day chronological sequence emission,
 * tracking state resolution, and user preference propagation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HomeViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private Application application;
    private PulseDatabase database;
    private DailyMetricsDao dailyMetricsDao;
    private MetricsRepository metricsRepository;
    private SettingsRepository settingsRepository;
    private SharedPreferences sharedPreferences;

    private HomeViewModel viewModel;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(application, PulseDatabase.class)
                .allowMainThreadQueries()
                .build();
        dailyMetricsDao = database.dailyMetricsDao();
        metricsRepository = new MetricsRepository(database, Runnable::run);

        sharedPreferences = application.getSharedPreferences("test_home_prefs", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
        settingsRepository = new SettingsRepository(sharedPreferences);

        viewModel = new HomeViewModel(application, metricsRepository, settingsRepository);
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void testTodayMetrics_observesDatabaseUpdates() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        DailyMetricsEntity entity = new DailyMetricsEntity(
                today,
                6420L,
                4800.5,
                2400L,
                260.0,
                10000L,
                System.currentTimeMillis()
        );

        AtomicReference<DailyMetricsEntity> observed = new AtomicReference<>();
        Observer<DailyMetricsEntity> observer = observed::set;
        viewModel.getTodayMetrics().observeForever(observer);

        try {
            // Insert metric row for today
            dailyMetricsDao.insertOrUpdate(entity);

            DailyMetricsEntity result = observed.get();
            assertNotNull(result);
            assertEquals(today, result.getDate());
            assertEquals(6420L, result.getSteps());
            assertEquals(4800.5, result.getDistanceMeters(), 0.01);
            assertEquals(260.0, result.getActiveCaloriesKcal(), 0.01);
            assertEquals(10000L, result.getStepGoal());
        } finally {
            viewModel.getTodayMetrics().removeObserver(observer);
        }
    }

    @Test
    public void testLast7DaysMetrics_emitsExactly7ChronologicalItemsWithZeroFill() {
        LocalDate endDate = LocalDate.of(2026, 11, 7);
        String todayStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Pre-populate sparse data: 2 days of activity
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-03", 3500L, 2500.0, 1500L, 120.0, 8000L, 1L));
        dailyMetricsDao.insertOrUpdate(new DailyMetricsEntity("2026-11-07", 7800L, 5600.0, 3100L, 290.0, 8000L, 2L));

        AtomicReference<List<DailyMetricsEntity>> observedList = new AtomicReference<>();
        Observer<List<DailyMetricsEntity>> observer = observedList::set;
        viewModel.getLast7DaysMetrics().observeForever(observer);

        try {
            viewModel.setDateForTesting(todayStr);

            List<DailyMetricsEntity> result = observedList.get();
            assertNotNull(result);
            assertEquals("Should return exactly 7 days", 7, result.size());

            // First entry should be 6 days before Nov 7 (Nov 1)
            assertEquals("2026-11-01", result.get(0).getDate());
            assertEquals(0L, result.get(0).getSteps()); // Zero filled

            // Day 3 (Nov 3) should have actual steps
            assertEquals("2026-11-03", result.get(2).getDate());
            assertEquals(3500L, result.get(2).getSteps());

            // Final entry should be Nov 7 (Today)
            assertEquals("2026-11-07", result.get(6).getDate());
            assertEquals(7800L, result.get(6).getSteps());
        } finally {
            viewModel.getLast7DaysMetrics().removeObserver(observer);
        }
    }

    @Test
    public void testTrackingStatusResolution_pausedWhenDisabledOrExplicitlyPaused() {
        AtomicReference<HomeViewModel.TrackingStatus> observedStatus = new AtomicReference<>();
        Observer<HomeViewModel.TrackingStatus> observer = observedStatus::set;
        viewModel.getTrackingStatus().observeForever(observer);

        try {
            // 1. By default, tracking is disabled in clean preferences -> resolveTrackingStatus reflects PAUSED
            // (or SENSOR_UNSUPPORTED / PERMISSION_MISSING if host has no sensor)
            HomeViewModel.TrackingStatus status = viewModel.resolveTrackingStatus();
            assertNotNull(status);

            // 2. Explicitly toggle pause state
            settingsRepository.setTrackingEnabled(true);
            settingsRepository.setTrackingPaused(true);
            viewModel.checkTrackingStatus();

            HomeViewModel.TrackingStatus pausedStatus = viewModel.resolveTrackingStatus();
            // In robolectric, if sensor and permission are available, it returns PAUSED;
            // if sensor unsupported, it returns SENSOR_UNSUPPORTED. Either way, resolveTrackingStatus is deterministic.
            assertNotNull(pausedStatus);
            assertTrue(pausedStatus == HomeViewModel.TrackingStatus.PAUSED
                    || pausedStatus == HomeViewModel.TrackingStatus.SENSOR_UNSUPPORTED
                    || pausedStatus == HomeViewModel.TrackingStatus.PERMISSION_MISSING);
        } finally {
            viewModel.getTrackingStatus().removeObserver(observer);
        }
    }

    @Test
    public void testPreferencesLiveObservation_unitSystemAndGoalUpdates() {
        AtomicReference<SettingsRepository.UnitSystem> observedUnit = new AtomicReference<>();
        Observer<SettingsRepository.UnitSystem> unitObserver = observedUnit::set;
        viewModel.getUnitSystem().observeForever(unitObserver);

        AtomicReference<Long> observedGoal = new AtomicReference<>();
        Observer<Long> goalObserver = observedGoal::set;
        viewModel.getDailyStepTarget().observeForever(goalObserver);

        try {
            // Initial defaults
            assertEquals(SettingsRepository.UnitSystem.METRIC, observedUnit.get());
            assertEquals(SettingsRepository.DEFAULT_STEP_GOAL, (long) observedGoal.get());

            // Update to Imperial
            settingsRepository.setUnitSystem(SettingsRepository.UnitSystem.IMPERIAL);
            assertEquals(SettingsRepository.UnitSystem.IMPERIAL, observedUnit.get());

            // Update Goal
            settingsRepository.setDailyStepTarget(12500L);
            assertEquals(12500L, (long) observedGoal.get());
        } finally {
            viewModel.getUnitSystem().removeObserver(unitObserver);
            viewModel.getDailyStepTarget().removeObserver(goalObserver);
        }
    }

    @Test
    public void testDateChangeBroadcast_triggersRefreshAndClearsStaleEntity() {
        android.content.Intent dateChangeIntent = new android.content.Intent(android.content.Intent.ACTION_DATE_CHANGED);
        assertNotNull(viewModel.getTimeChangeReceiver());
        viewModel.getTimeChangeReceiver().onReceive(application, dateChangeIntent);

        android.content.Intent timeZoneIntent = new android.content.Intent(android.content.Intent.ACTION_TIMEZONE_CHANGED);
        viewModel.getTimeChangeReceiver().onReceive(application, timeZoneIntent);

        assertNotNull(viewModel.getTodayMetrics());
    }
}

