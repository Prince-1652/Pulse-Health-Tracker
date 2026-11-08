package com.pulse.tracker.util;

import com.pulse.tracker.data.repository.SettingsRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for UnitFormatter verifying number grouping, metric and imperial conversions,
 * pace calculations, zero/negative distance safety, and active time formatting.
 */
public class UnitFormatterTest {

    @Before
    public void setUp() {
        Locale.setDefault(Locale.US);
    }

    @Test
    public void testFormatSteps_groupingAndBounds() {
        assertEquals("8,420", UnitFormatter.formatSteps(8420L));
        assertEquals("0", UnitFormatter.formatSteps(0L));
        assertEquals("0", UnitFormatter.formatSteps(-500L));
        assertEquals("1,234,567", UnitFormatter.formatSteps(1_234_567L));
    }

    @Test
    public void testFormatDistance_metricAndImperial() {
        // 5240 meters = 5.24 km
        assertEquals("5.24 km", UnitFormatter.formatDistance(5240.0, SettingsRepository.UnitSystem.METRIC));

        // 5240 meters = 3.256 miles -> 3.26 mi
        assertEquals("3.26 mi", UnitFormatter.formatDistance(5240.0, SettingsRepository.UnitSystem.IMPERIAL));

        // Null unit system defaults to metric
        assertEquals("5.24 km", UnitFormatter.formatDistance(5240.0, null));

        // Zero, negative, and NaN distances
        assertEquals("0.00 km", UnitFormatter.formatDistance(0.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals("0.00 mi", UnitFormatter.formatDistance(-100.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals("0.00 km", UnitFormatter.formatDistance(Double.NaN, SettingsRepository.UnitSystem.METRIC));
    }

    @Test
    public void testFormatCalories_positiveAndUnavailable() {
        assertEquals("342 kcal", UnitFormatter.formatCalories(342.3));
        assertEquals("1,200 kcal", UnitFormatter.formatCalories(1200.0));

        // Zero, negative, or NaN calories must display em-dash placeholder
        assertEquals(UnitFormatter.EM_DASH, UnitFormatter.formatCalories(0.0));
        assertEquals(UnitFormatter.EM_DASH, UnitFormatter.formatCalories(-50.0));
        assertEquals(UnitFormatter.EM_DASH, UnitFormatter.formatCalories(Double.NaN));
    }

    @Test
    public void testFormatActiveTime_variousDurations() {
        assertEquals("0 min", UnitFormatter.formatActiveTime(0L));
        assertEquals("0 min", UnitFormatter.formatActiveTime(-10L));
        assertEquals("< 1 min", UnitFormatter.formatActiveTime(45L));
        assertEquals("45 min", UnitFormatter.formatActiveTime(2700L));
        assertEquals("1h 15m", UnitFormatter.formatActiveTime(4500L));
        assertEquals("1h", UnitFormatter.formatActiveTime(3600L));
        assertEquals("2h 30m", UnitFormatter.formatActiveTime(9000L));
    }

    @Test
    public void testFormatPace_metricAndImperial() {
        // 1000m in 330 seconds = 5'30" /km
        assertEquals("5'30\" /km", UnitFormatter.formatPace(1000.0, 330L, SettingsRepository.UnitSystem.METRIC));

        // 1609.344m (1 mile) in 532 seconds = 8'52" /mi
        assertEquals("8'52\" /mi", UnitFormatter.formatPace(1609.344, 532L, SettingsRepository.UnitSystem.IMPERIAL));

        // Zero distance safety
        assertEquals("— min/km", UnitFormatter.formatPace(0.0, 330L, SettingsRepository.UnitSystem.METRIC));
        assertEquals("— min/mi", UnitFormatter.formatPace(0.0, 532L, SettingsRepository.UnitSystem.IMPERIAL));

        // Negative or zero time safety
        assertEquals("— min/km", UnitFormatter.formatPace(1000.0, 0L, SettingsRepository.UnitSystem.METRIC));
        assertEquals("— min/km", UnitFormatter.formatPace(1000.0, -10L, SettingsRepository.UnitSystem.METRIC));

        // NaN distance safety
        assertEquals("— min/km", UnitFormatter.formatPace(Double.NaN, 330L, SettingsRepository.UnitSystem.METRIC));
    }

    @Test
    public void testFormatStride_metricAndImperial() {
        assertEquals("71 cm", UnitFormatter.formatStride(71.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals("28.0 in", UnitFormatter.formatStride(71.12, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals("0 cm", UnitFormatter.formatStride(0.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals("0.0 in", UnitFormatter.formatStride(-10.0, SettingsRepository.UnitSystem.IMPERIAL));
    }

    @Test
    public void testFormatWeight_metricAndImperial() {
        assertEquals("70.0 kg", UnitFormatter.formatWeight(70.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals("154.3 lb", UnitFormatter.formatWeight(70.0, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals("0.0 kg", UnitFormatter.formatWeight(0.0, SettingsRepository.UnitSystem.METRIC));
    }

    @Test
    public void testFormatHeight_metricAndImperial() {
        assertEquals("175 cm", UnitFormatter.formatHeight(175.0, SettingsRepository.UnitSystem.METRIC));
        // 175.26 cm = 69 inches = 5 ft 9 in
        assertEquals("5 ft 9 in", UnitFormatter.formatHeight(175.26, SettingsRepository.UnitSystem.IMPERIAL));
        assertEquals("0 cm", UnitFormatter.formatHeight(0.0, SettingsRepository.UnitSystem.METRIC));
        assertEquals("0 ft 0 in", UnitFormatter.formatHeight(0.0, SettingsRepository.UnitSystem.IMPERIAL));
    }
}
