package com.pulse.tracker.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for DistanceCalculator verifying step-to-distance mathematics,
 * metric/imperial conversions, and boundary/invalid input handling.
 */
public class DistanceCalculatorTest {

    private static final double EPSILON = 0.001;

    @Test
    public void testCalculateDistanceMeters_normalInputs() {
        // 1000 steps at 70cm stride = 700.0 meters
        double distance = DistanceCalculator.calculateDistanceMeters(1000L, 70.0);
        assertEquals(700.0, distance, EPSILON);

        // 10,000 steps at 75.5cm stride = 7550.0 meters
        double distance2 = DistanceCalculator.calculateDistanceMeters(10_000L, 75.5);
        assertEquals(7550.0, distance2, EPSILON);

        // 1 step at 65.0cm stride = 0.65 meters
        double distance3 = DistanceCalculator.calculateDistanceMeters(1L, 65.0);
        assertEquals(0.65, distance3, EPSILON);
    }

    @Test
    public void testCalculateDistanceMeters_zeroOrNegativeInputs() {
        // Zero steps
        assertEquals(0.0, DistanceCalculator.calculateDistanceMeters(0L, 70.0), EPSILON);

        // Negative steps
        assertEquals(0.0, DistanceCalculator.calculateDistanceMeters(-100L, 70.0), EPSILON);

        // Zero stride
        assertEquals(0.0, DistanceCalculator.calculateDistanceMeters(1000L, 0.0), EPSILON);

        // Negative stride
        assertEquals(0.0, DistanceCalculator.calculateDistanceMeters(1000L, -70.0), EPSILON);

        // NaN or Infinite stride
        assertEquals(0.0, DistanceCalculator.calculateDistanceMeters(1000L, Double.NaN), EPSILON);
        assertEquals(0.0, DistanceCalculator.calculateDistanceMeters(1000L, Double.POSITIVE_INFINITY), EPSILON);
    }

    @Test
    public void testMetersToKilometers() {
        assertEquals(1.0, DistanceCalculator.metersToKilometers(1000.0), EPSILON);
        assertEquals(5.24, DistanceCalculator.metersToKilometers(5240.0), EPSILON);
        assertEquals(0.0, DistanceCalculator.metersToKilometers(0.0), EPSILON);
        assertEquals(0.0, DistanceCalculator.metersToKilometers(-500.0), EPSILON);
        assertEquals(0.0, DistanceCalculator.metersToKilometers(Double.NaN), EPSILON);
    }

    @Test
    public void testMetersToMiles() {
        // 1609.344 meters = 1 statute mile
        assertEquals(1.0, DistanceCalculator.metersToMiles(1609.344), EPSILON);

        // 5000 meters = ~3.10686 miles
        assertEquals(3.10686, DistanceCalculator.metersToMiles(5000.0), 0.001);

        assertEquals(0.0, DistanceCalculator.metersToMiles(0.0), EPSILON);
        assertEquals(0.0, DistanceCalculator.metersToMiles(-100.0), EPSILON);
        assertEquals(0.0, DistanceCalculator.metersToMiles(Double.NaN), EPSILON);
    }

    @Test
    public void testCalculateDefaultStrideCm() {
        // Height 170cm * 0.415 = 70.55 cm
        assertEquals(70.55, DistanceCalculator.calculateDefaultStrideCm(170.0), EPSILON);

        // Height 180cm * 0.415 = 74.70 cm
        assertEquals(74.70, DistanceCalculator.calculateDefaultStrideCm(180.0), EPSILON);

        // Invalid height inputs
        assertEquals(0.0, DistanceCalculator.calculateDefaultStrideCm(0.0), EPSILON);
        assertEquals(0.0, DistanceCalculator.calculateDefaultStrideCm(-170.0), EPSILON);
        assertEquals(0.0, DistanceCalculator.calculateDefaultStrideCm(Double.NaN), EPSILON);
    }

    @Test
    public void testKilometersAndMilesToMeters() {
        assertEquals(2500.0, DistanceCalculator.kilometersToMeters(2.5), EPSILON);
        assertEquals(1609.344, DistanceCalculator.milesToMeters(1.0), EPSILON);
        assertEquals(0.0, DistanceCalculator.kilometersToMeters(-1.0), EPSILON);
        assertEquals(0.0, DistanceCalculator.milesToMeters(-1.0), EPSILON);
    }
}
