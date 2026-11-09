package com.pulse.tracker.ui.profile;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests verifying ProfileViewModel validation rules, canonical metric conversions,
 * LiveData state propagation, and Room today's goal synchronization.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ProfileViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private Application application;
    private PulseDatabase database;
    private DailyMetricsDao dailyMetricsDao;
    private MetricsRepository metricsRepository;
    private SettingsRepository settingsRepository;
    private SharedPreferences sharedPreferences;

    private ProfileViewModel viewModel;

    @Before
    public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(application, PulseDatabase.class)
                .allowMainThreadQueries()
                .build();
        dailyMetricsDao = database.dailyMetricsDao();
        metricsRepository = new MetricsRepository(database, Runnable::run);

        sharedPreferences = application.getSharedPreferences("test_profile_prefs", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
        settingsRepository = new SettingsRepository(sharedPreferences);

        viewModel = new ProfileViewModel(application, settingsRepository, metricsRepository);
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void testInitialValues_loadedFromRepository() {
        assertEquals("Prince", viewModel.getUserName().getValue());
        assertEquals(SettingsRepository.DEFAULT_HEIGHT_CM, viewModel.getHeightCm().getValue(), 0.01);
        assertEquals(SettingsRepository.DEFAULT_WEIGHT_KG, viewModel.getWeightKg().getValue(), 0.01);
        assertEquals(SettingsRepository.DEFAULT_AGE, (int) viewModel.getAge().getValue());
        assertEquals(SettingsRepository.Gender.UNSPECIFIED, viewModel.getGender().getValue());
        assertEquals(SettingsRepository.DEFAULT_HEIGHT_CM * SettingsRepository.STRIDE_HEIGHT_RATIO,
                viewModel.getStrideLengthCm().getValue(), 0.01);
        assertFalse(viewModel.getIsManualStride().getValue());
        assertEquals(SettingsRepository.TrackingLatency.BATTERY_EFFICIENT, viewModel.getTrackingLatency().getValue());
        assertEquals(SettingsRepository.DEFAULT_STEP_GOAL, (long) viewModel.getDailyStepGoal().getValue());
        assertEquals(SettingsRepository.UnitSystem.METRIC, viewModel.getUnitSystem().getValue());
    }

    @Test
    public void testSaveUserName_persistsAndPropagates() {
        viewModel.saveUserName("David");
        assertEquals("David", viewModel.getUserName().getValue());
        assertEquals("David", settingsRepository.getUserName());

        // Empty string falls back to default
        viewModel.saveUserName("");
        assertEquals(SettingsRepository.DEFAULT_USER_NAME, viewModel.getUserName().getValue());
        assertEquals(SettingsRepository.DEFAULT_USER_NAME, settingsRepository.getUserName());
    }

    @Test
    public void testSaveHeight_metricValidationAndCanonicalPersistence() {
        // Valid metric height (50 to 260 cm)
        assertTrue(viewModel.saveHeight(175.5, SettingsRepository.UnitSystem.METRIC));
        assertEquals(175.5, viewModel.getHeightCm().getValue(), 0.01);
        assertEquals(175.5, settingsRepository.getHeightCm(), 0.01);

        // When stride is not manual, stride is updated automatically
        assertEquals(175.5 * SettingsRepository.STRIDE_HEIGHT_RATIO, viewModel.getStrideLengthCm().getValue(), 0.01);

        // Invalid metric heights rejected; valid value preserved
        assertFalse(viewModel.saveHeight(49.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals(175.5, viewModel.getHeightCm().getValue(), 0.01);

        assertFalse(viewModel.saveHeight(261.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals(175.5, viewModel.getHeightCm().getValue(), 0.01);

        assertFalse(viewModel.saveHeight(Double.NaN, SettingsRepository.UnitSystem.METRIC));
        assertEquals(175.5, viewModel.getHeightCm().getValue(), 0.01);
    }

    @Test
    public void testSaveHeight_imperialValidationAndCanonicalConversion() {
        // Valid imperial height (20 to 102 inches)
        // 70 inches = 177.8 cm
        assertTrue(viewModel.saveHeight(70.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(177.8, viewModel.getHeightCm().getValue(), 0.01);
        assertEquals(177.8, settingsRepository.getHeightCm(), 0.01);

        // Invalid imperial heights rejected
        assertFalse(viewModel.saveHeight(19.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(177.8, viewModel.getHeightCm().getValue(), 0.01);

        assertFalse(viewModel.saveHeight(103.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(177.8, viewModel.getHeightCm().getValue(), 0.01);
    }

    @Test
    public void testSaveWeight_metricValidationAndCanonicalPersistence() {
        // Valid metric weight (20 to 350 kg)
        assertTrue(viewModel.saveWeight(68.5, SettingsRepository.UnitSystem.METRIC));
        assertEquals(68.5, viewModel.getWeightKg().getValue(), 0.01);
        assertEquals(68.5, settingsRepository.getWeightKg(), 0.01);

        // Invalid metric weights rejected
        assertFalse(viewModel.saveWeight(19.5, SettingsRepository.UnitSystem.METRIC));
        assertEquals(68.5, viewModel.getWeightKg().getValue(), 0.01);

        assertFalse(viewModel.saveWeight(351.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals(68.5, viewModel.getWeightKg().getValue(), 0.01);

        assertFalse(viewModel.saveWeight(Double.NaN, SettingsRepository.UnitSystem.METRIC));
        assertEquals(68.5, viewModel.getWeightKg().getValue(), 0.01);
    }

    @Test
    public void testSaveWeight_imperialValidationAndCanonicalConversion() {
        // Valid imperial weight (44 to 770 lbs)
        // 154 lbs = ~69.85 kg
        double expectedKg = SettingsRepository.lbsToKg(154.0);
        assertTrue(viewModel.saveWeight(154.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(expectedKg, viewModel.getWeightKg().getValue(), 0.01);
        assertEquals(expectedKg, settingsRepository.getWeightKg(), 0.01);

        // Invalid imperial weights rejected
        assertFalse(viewModel.saveWeight(43.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(expectedKg, viewModel.getWeightKg().getValue(), 0.01);

        assertFalse(viewModel.saveWeight(771.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(expectedKg, viewModel.getWeightKg().getValue(), 0.01);
    }

    @Test
    public void testSaveAge_validationAndPersistence() {
        // Valid age (5 to 125 years)
        assertTrue(viewModel.saveAge(5));
        assertEquals(5, (int) viewModel.getAge().getValue());

        assertTrue(viewModel.saveAge(35));
        assertEquals(35, (int) viewModel.getAge().getValue());

        assertTrue(viewModel.saveAge(125));
        assertEquals(125, (int) viewModel.getAge().getValue());

        // Invalid age rejected
        assertFalse(viewModel.saveAge(4));
        assertEquals(125, (int) viewModel.getAge().getValue());

        assertFalse(viewModel.saveAge(126));
        assertEquals(125, (int) viewModel.getAge().getValue());
    }

    @Test
    public void testSaveGender_persistence() {
        assertTrue(viewModel.saveGender(SettingsRepository.Gender.MALE));
        assertEquals(SettingsRepository.Gender.MALE, viewModel.getGender().getValue());

        assertTrue(viewModel.saveGender(SettingsRepository.Gender.FEMALE));
        assertEquals(SettingsRepository.Gender.FEMALE, viewModel.getGender().getValue());

        assertTrue(viewModel.saveGender(SettingsRepository.Gender.UNSPECIFIED));
        assertEquals(SettingsRepository.Gender.UNSPECIFIED, viewModel.getGender().getValue());

        assertFalse(viewModel.saveGender(null));
        assertEquals(SettingsRepository.Gender.UNSPECIFIED, viewModel.getGender().getValue());
    }

    @Test
    public void testSaveStrideLength_metricValidationAndCanonicalPersistence() {
        // Valid metric stride (20 to 160 cm)
        assertTrue(viewModel.saveStrideLength(78.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals(78.0, viewModel.getStrideLengthCm().getValue(), 0.01);
        assertTrue(viewModel.getIsManualStride().getValue());
        assertEquals(78.0, settingsRepository.getStrideLengthCm(), 0.01);
        assertTrue(settingsRepository.isManualStride());

        // Invalid metric stride rejected
        assertFalse(viewModel.saveStrideLength(19.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals(78.0, viewModel.getStrideLengthCm().getValue(), 0.01);

        assertFalse(viewModel.saveStrideLength(161.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals(78.0, viewModel.getStrideLengthCm().getValue(), 0.01);
    }

    @Test
    public void testSaveStrideLength_imperialValidationAndCanonicalConversion() {
        // Valid imperial stride (8 to 63 inches)
        // 30 inches = 76.2 cm
        assertTrue(viewModel.saveStrideLength(30.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(76.2, viewModel.getStrideLengthCm().getValue(), 0.01);
        assertTrue(viewModel.getIsManualStride().getValue());

        // Invalid imperial stride rejected
        assertFalse(viewModel.saveStrideLength(7.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(76.2, viewModel.getStrideLengthCm().getValue(), 0.01);

        assertFalse(viewModel.saveStrideLength(64.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(76.2, viewModel.getStrideLengthCm().getValue(), 0.01);
    }

    @Test
    public void testResetStrideToDefault_clearsManualStrideAndRevertsToHeightDerived() {
        viewModel.saveHeight(180.0, SettingsRepository.UnitSystem.METRIC);
        viewModel.saveStrideLength(85.0, SettingsRepository.UnitSystem.METRIC);
        assertTrue(viewModel.getIsManualStride().getValue());
        assertEquals(85.0, viewModel.getStrideLengthCm().getValue(), 0.01);

        // Reset to default
        viewModel.resetStrideToDefault();
        assertFalse(viewModel.getIsManualStride().getValue());
        assertFalse(settingsRepository.isManualStride());
        assertEquals(180.0 * SettingsRepository.STRIDE_HEIGHT_RATIO, viewModel.getStrideLengthCm().getValue(), 0.01);
    }

    @Test
    public void testSaveTrackingLatency_persistence() {
        assertTrue(viewModel.saveTrackingLatency(SettingsRepository.TrackingLatency.FASTER_UPDATES));
        assertEquals(SettingsRepository.TrackingLatency.FASTER_UPDATES, viewModel.getTrackingLatency().getValue());
        assertEquals(SettingsRepository.TrackingLatency.FASTER_UPDATES, settingsRepository.getTrackingLatency());

        assertTrue(viewModel.saveTrackingLatency(SettingsRepository.TrackingLatency.BATTERY_EFFICIENT));
        assertEquals(SettingsRepository.TrackingLatency.BATTERY_EFFICIENT, viewModel.getTrackingLatency().getValue());

        assertFalse(viewModel.saveTrackingLatency(null));
        assertEquals(SettingsRepository.TrackingLatency.BATTERY_EFFICIENT, viewModel.getTrackingLatency().getValue());
    }

    @Test
    public void testSaveDailyStepGoal_validationAndRoomUpdate() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Valid goal (500 to 50,000 steps)
        assertTrue(viewModel.saveDailyStepGoal(8500L));
        assertEquals(8500L, (long) viewModel.getDailyStepGoal().getValue());
        assertEquals(8500L, settingsRepository.getDailyStepTarget());

        // Verify Room today's row was created/updated with the new goal
        DailyMetricsEntity todayEntity = dailyMetricsDao.getMetricsForDate(today);
        assertNotNull(todayEntity);
        assertEquals(8500L, todayEntity.getStepGoal());

        // Update goal to upper bound
        assertTrue(viewModel.saveDailyStepGoal(50000L));
        todayEntity = dailyMetricsDao.getMetricsForDate(today);
        assertNotNull(todayEntity);
        assertEquals(50000L, todayEntity.getStepGoal());

        // Invalid goals rejected; existing preserved
        assertFalse(viewModel.saveDailyStepGoal(499L));
        assertEquals(50000L, (long) viewModel.getDailyStepGoal().getValue());
        assertEquals(50000L, dailyMetricsDao.getMetricsForDate(today).getStepGoal());

        assertFalse(viewModel.saveDailyStepGoal(50001L));
        assertEquals(50000L, (long) viewModel.getDailyStepGoal().getValue());
        assertEquals(50000L, dailyMetricsDao.getMetricsForDate(today).getStepGoal());
    }

    @Test
    public void testUnitSystemToggle_preservesCanonicalMetricValues() {
        viewModel.saveHeight(175.0, SettingsRepository.UnitSystem.METRIC);
        viewModel.saveWeight(72.0, SettingsRepository.UnitSystem.METRIC);
        viewModel.saveStrideLength(72.5, SettingsRepository.UnitSystem.METRIC);

        // Toggle to Imperial
        assertTrue(viewModel.saveUnitSystem(SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(SettingsRepository.UnitSystem.IMPERIAL, viewModel.getUnitSystem().getValue());

        // Check canonical internal values remain uncorrupted
        assertEquals(175.0, settingsRepository.getHeightCm(), 0.01);
        assertEquals(72.0, settingsRepository.getWeightKg(), 0.01);
        assertEquals(72.5, settingsRepository.getStrideLengthCm(), 0.01);

        // Toggle back to Metric
        assertTrue(viewModel.saveUnitSystem(SettingsRepository.UnitSystem.METRIC));
        assertEquals(SettingsRepository.UnitSystem.METRIC, viewModel.getUnitSystem().getValue());

        assertEquals(175.0, settingsRepository.getHeightCm(), 0.01);
        assertEquals(72.0, settingsRepository.getWeightKg(), 0.01);
        assertEquals(72.5, settingsRepository.getStrideLengthCm(), 0.01);
    }

    @Test
    public void testExternalSettingsChange_updatesViewModelLiveData() {
        AtomicReference<Double> observedHeight = new AtomicReference<>();
        Observer<Double> observer = observedHeight::set;
        viewModel.getHeightCm().observeForever(observer);

        try {
            settingsRepository.setHeightCm(192.0);
            assertEquals(192.0, observedHeight.get(), 0.01);
        } finally {
            viewModel.getHeightCm().removeObserver(observer);
        }
    }
}
