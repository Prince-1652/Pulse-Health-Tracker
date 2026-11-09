package com.pulse.tracker.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.data.repository.SettingsRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests verifying SettingsRepository input validation, canonical metric persistence,
 * default fallbacks, conversions, and change listener notifications.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsRepositoryTest {

    private SettingsRepository repository;
    private SharedPreferences sharedPreferences;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        sharedPreferences = context.getSharedPreferences("test_pulse_prefs", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
        repository = new SettingsRepository(sharedPreferences);
    }

    @Test
    public void testHeightValidation_validRangeSaved_invalidRejected() {
        // Valid height
        assertTrue(repository.setHeightCm(180.0));
        assertEquals(180.0, repository.getHeightCm(), 0.01);

        // Invalid heights (< 50 or > 260) rejected without overwriting
        assertFalse(repository.setHeightCm(40.0));
        assertEquals(180.0, repository.getHeightCm(), 0.01);

        assertFalse(repository.setHeightCm(280.0));
        assertEquals(180.0, repository.getHeightCm(), 0.01);
    }

    @Test
    public void testWeightValidation_validRangeSaved_invalidRejected() {
        assertTrue(repository.setWeightKg(65.5));
        assertEquals(65.5, repository.getWeightKg(), 0.01);

        // Invalid weights (< 20 or > 350) rejected
        assertFalse(repository.setWeightKg(15.0));
        assertEquals(65.5, repository.getWeightKg(), 0.01);

        assertFalse(repository.setWeightKg(360.0));
        assertEquals(65.5, repository.getWeightKg(), 0.01);
    }

    @Test
    public void testAgeValidation_validRangeSaved_invalidRejected() {
        assertTrue(repository.setAge(28));
        assertEquals(28, repository.getAge());

        // Invalid age (< 1 or > 120) rejected
        assertFalse(repository.setAge(0));
        assertEquals(28, repository.getAge());

        assertFalse(repository.setAge(130));
        assertEquals(28, repository.getAge());
    }

    @Test
    public void testGender_roundTripsCleanly() {
        assertEquals(SettingsRepository.Gender.UNSPECIFIED, repository.getGender());

        assertTrue(repository.setGender(SettingsRepository.Gender.MALE));
        assertEquals(SettingsRepository.Gender.MALE, repository.getGender());

        assertTrue(repository.setGender(SettingsRepository.Gender.FEMALE));
        assertEquals(SettingsRepository.Gender.FEMALE, repository.getGender());

        assertFalse(repository.setGender(null));
        assertEquals(SettingsRepository.Gender.FEMALE, repository.getGender());
    }

    @Test
    public void testStrideLength_defaultsToHeightRatio_whenUnset() {
        repository.setHeightCm(180.0);
        // Default stride is height * 0.415 = 180 * 0.415 = 74.7 cm
        assertEquals(180.0 * 0.415, repository.getStrideLengthCm(), 0.01);

        // Manual valid stride
        assertTrue(repository.setStrideLengthCm(76.0));
        assertEquals(76.0, repository.getStrideLengthCm(), 0.01);

        // Invalid stride rejected
        assertFalse(repository.setStrideLengthCm(10.0));
        assertEquals(76.0, repository.getStrideLengthCm(), 0.01);

        assertFalse(repository.setStrideLengthCm(200.0));
        assertEquals(76.0, repository.getStrideLengthCm(), 0.01);
    }

    @Test
    public void testDailyStepTarget_validSaved_invalidRejected() {
        assertEquals(SettingsRepository.DEFAULT_STEP_GOAL, repository.getDailyStepTarget());

        assertTrue(repository.setDailyStepTarget(8500L));
        assertEquals(8500L, repository.getDailyStepTarget());

        // Boundary testing
        assertFalse(repository.setDailyStepTarget(50L));
        assertEquals(8500L, repository.getDailyStepTarget());

        assertTrue(repository.setDailyStepTarget(50000L));
        assertEquals(50000L, repository.getDailyStepTarget());

        assertFalse(repository.setDailyStepTarget(50001L));
        assertEquals(50000L, repository.getDailyStepTarget());

        assertFalse(repository.setDailyStepTarget(200000L));
        assertEquals(50000L, repository.getDailyStepTarget());
    }

    @Test
    public void testUnitSystem_roundTripsCleanly() {
        assertEquals(SettingsRepository.UnitSystem.METRIC, repository.getUnitSystem());

        assertTrue(repository.setUnitSystem(SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals(SettingsRepository.UnitSystem.IMPERIAL, repository.getUnitSystem());

        assertFalse(repository.setUnitSystem(null));
        assertEquals(SettingsRepository.UnitSystem.IMPERIAL, repository.getUnitSystem());
    }

    @Test
    public void testTrackingLatency_roundTripsCleanly() {
        assertEquals(SettingsRepository.TrackingLatency.BATTERY_EFFICIENT, repository.getTrackingLatency());

        assertTrue(repository.setTrackingLatency(SettingsRepository.TrackingLatency.FASTER_UPDATES));
        assertEquals(SettingsRepository.TrackingLatency.FASTER_UPDATES, repository.getTrackingLatency());

        assertFalse(repository.setTrackingLatency(null));
        assertEquals(SettingsRepository.TrackingLatency.FASTER_UPDATES, repository.getTrackingLatency());
    }

    @Test
    public void testTrackingEnabled_toggle() {
        assertFalse(repository.isTrackingEnabled());

        repository.setTrackingEnabled(true);
        assertTrue(repository.isTrackingEnabled());

        repository.setTrackingEnabled(false);
        assertFalse(repository.isTrackingEnabled());
    }

    @Test
    public void testNotificationEnabled_defaultTrueAndPersists() {
        // Defaults to true
        assertTrue(repository.isNotificationEnabled());

        // Toggle to false
        repository.setNotificationEnabled(false);
        assertFalse(repository.isNotificationEnabled());

        // Toggle back to true
        repository.setNotificationEnabled(true);
        assertTrue(repository.isNotificationEnabled());
    }

    @Test
    public void testUnitConversions() {
        // 1 inch = 2.54 cm
        assertEquals(10.0, SettingsRepository.cmToInches(25.4), 0.001);
        assertEquals(25.4, SettingsRepository.inchesToCm(10.0), 0.001);

        // 1 lb = 0.45359237 kg
        assertEquals(100.0, SettingsRepository.lbsToKg(SettingsRepository.kgToLbs(100.0)), 0.001);
    }

    @Test
    public void testListenerNotification_firesOnChange() {
        AtomicReference<String> changedKey = new AtomicReference<>();
        AtomicInteger callCount = new AtomicInteger(0);

        SettingsRepository.OnSettingsChangeListener listener = key -> {
            changedKey.set(key);
            callCount.incrementAndGet();
        };

        repository.registerListener(listener);

        repository.setDailyStepTarget(15000L);
        assertEquals(SettingsRepository.KEY_DAILY_STEP_TARGET, changedKey.get());
        assertEquals(1, callCount.get());

        // Unregister listener
        repository.unregisterListener(listener);
        repository.setDailyStepTarget(9000L);
        assertEquals(1, callCount.get());
    }
}

