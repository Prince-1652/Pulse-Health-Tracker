package com.pulse.tracker.domain;

import com.pulse.tracker.data.repository.SettingsRepository;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for CalorieCalculator verifying Mifflin-St Jeor BMR equations,
 * net active calorie calculations with walking MET, and handling of unspecified gender and edge cases.
 */
public class CalorieCalculatorTest {

    private static final double EPSILON = 0.01;

    @Test
    public void testCalculateBmr_male() {
        // Male: 70kg, 175cm, 30y
        // BMR = 10 * 70 + 6.25 * 175 - 5 * 30 + 5
        //     = 700 + 1093.75 - 150 + 5 = 1648.75
        double bmr = CalorieCalculator.calculateBmr(70.0, 175.0, 30, SettingsRepository.Gender.MALE);
        assertEquals(1648.75, bmr, EPSILON);
    }

    @Test
    public void testCalculateBmr_female() {
        // Female: 60kg, 165cm, 25y
        // BMR = 10 * 60 + 6.25 * 165 - 5 * 25 - 161
        //     = 600 + 1031.25 - 125 - 161 = 1345.25
        double bmr = CalorieCalculator.calculateBmr(60.0, 165.0, 25, SettingsRepository.Gender.FEMALE);
        assertEquals(1345.25, bmr, EPSILON);
    }

    @Test
    public void testCalculateBmr_unspecifiedGender_usesGenderNeutralMidpointEquation() {
        // Unspecified gender uses gender-neutral midpoint equation base - 78.0
        // base = 700 + 1093.75 - 150 = 1643.75 -> 1643.75 - 78.0 = 1565.75
        double bmr = CalorieCalculator.calculateBmr(70.0, 175.0, 30, SettingsRepository.Gender.UNSPECIFIED);
        assertEquals(1565.75, bmr, EPSILON);

        // Null gender also uses gender-neutral midpoint equation
        double bmrNull = CalorieCalculator.calculateBmr(70.0, 175.0, 30, null);
        assertEquals(1565.75, bmrNull, EPSILON);
    }

    @Test
    public void testCalculateStepCalories() {
        // 1000 steps, 70kg, 74cm stride: distance = 0.74 km, kcal = 0.74 * 70 * 0.75 = 38.85
        double kcal = CalorieCalculator.calculateStepCalories(1000, 70.0, 74.0);
        assertEquals(38.85, kcal, EPSILON);

        // Fallback when weight or stride missing: 1000 * 0.04 = 40.0
        double kcalFallback = CalorieCalculator.calculateStepCalories(1000, 0.0, 0.0);
        assertEquals(40.0, kcalFallback, EPSILON);

        // Overload using default step calculation
        assertEquals(40.0, CalorieCalculator.calculateStepCalories(1000), EPSILON);

        // Zero or negative steps
        assertEquals(0.0, CalorieCalculator.calculateStepCalories(0, 70.0, 74.0), EPSILON);
        assertEquals(0.0, CalorieCalculator.calculateStepCalories(-10), EPSILON);
    }

    @Test
    public void testCalculateBmr_invalidAndBoundaryInputs() {
        // Zero or negative weight
        assertEquals(0.0, CalorieCalculator.calculateBmr(0.0, 175.0, 30, SettingsRepository.Gender.MALE), EPSILON);
        assertEquals(0.0, CalorieCalculator.calculateBmr(-70.0, 175.0, 30, SettingsRepository.Gender.MALE), EPSILON);

        // Zero or negative height
        assertEquals(0.0, CalorieCalculator.calculateBmr(70.0, 0.0, 30, SettingsRepository.Gender.MALE), EPSILON);
        assertEquals(0.0, CalorieCalculator.calculateBmr(70.0, -175.0, 30, SettingsRepository.Gender.MALE), EPSILON);

        // Zero or negative age
        assertEquals(0.0, CalorieCalculator.calculateBmr(70.0, 175.0, 0, SettingsRepository.Gender.MALE), EPSILON);
        assertEquals(0.0, CalorieCalculator.calculateBmr(70.0, 175.0, -5, SettingsRepository.Gender.MALE), EPSILON);

        // NaN inputs
        assertEquals(0.0, CalorieCalculator.calculateBmr(Double.NaN, 175.0, 30, SettingsRepository.Gender.MALE), EPSILON);
        assertEquals(0.0, CalorieCalculator.calculateBmr(70.0, Double.NaN, 30, SettingsRepository.Gender.MALE), EPSILON);
    }

    @Test
    public void testCalculateNetActiveCalories_withWalkingMet() {
        double bmr = 1440.0; // 1.0 kcal per minute resting rate
        // 30 minutes (1800 seconds) at MET = 3.0 (default walking)
        // netMet = 3.0 - 1.0 = 2.0
        // activeKcal = (1440 / 1440) * 2.0 * (1800 / 60) = 1.0 * 2.0 * 30 = 60.0 kcal
        double activeKcal = CalorieCalculator.calculateNetActiveCalories(bmr, 1800L, 3.0);
        assertEquals(60.0, activeKcal, EPSILON);

        // Overload using default walking MET (3.0)
        double activeKcalDefault = CalorieCalculator.calculateNetActiveCalories(bmr, 1800L);
        assertEquals(60.0, activeKcalDefault, EPSILON);
    }

    @Test
    public void testCalculateNetActiveCalories_baselineOrRestingMetYieldsZero() {
        double bmr = 1600.0;
        // MET = 1.0 represents resting expenditure, so net active energy above baseline is 0.0
        assertEquals(0.0, CalorieCalculator.calculateNetActiveCalories(bmr, 3600L, 1.0), EPSILON);

        // Sub-resting MET (< 1.0)
        assertEquals(0.0, CalorieCalculator.calculateNetActiveCalories(bmr, 3600L, 0.8), EPSILON);
    }

    @Test
    public void testCalculateNetActiveCalories_zeroOrNegativeInputs() {
        // Zero or negative BMR falls back to safe adult BMR (1550.0)
        // (1550 / 1440) * 2.0 * 30 = 64.58 kcal
        assertEquals(64.58, CalorieCalculator.calculateNetActiveCalories(0.0, 1800L, 3.0), EPSILON);
        assertEquals(64.58, CalorieCalculator.calculateNetActiveCalories(-1500.0, 1800L, 3.0), EPSILON);
        assertEquals(64.58, CalorieCalculator.calculateNetActiveCalories(Double.NaN, 1800L, 3.0), EPSILON);

        // Zero or negative active seconds must always return 0.0
        assertEquals(0.0, CalorieCalculator.calculateNetActiveCalories(1600.0, 0L, 3.0), EPSILON);
        assertEquals(0.0, CalorieCalculator.calculateNetActiveCalories(1600.0, -100L, 3.0), EPSILON);
    }

    @Test
    public void testCalculateActiveCalories_convenienceMethod() {
        // Male: 70kg, 175cm, 30y -> BMR = 1648.75
        // activeSeconds = 3600 (60 minutes)
        // netMet = 3.0 - 1.0 = 2.0
        // activeKcal = (1648.75 / 1440) * 2.0 * 60 = 137.3958 kcal
        double kcal = CalorieCalculator.calculateActiveCalories(
                70.0, 175.0, 30, SettingsRepository.Gender.MALE, 3600L
        );
        assertEquals(137.3958, kcal, 0.05);

        // Unspecified gender uses gender-neutral BMR 1565.75
        // activeKcal = (1565.75 / 1440) * 2.0 * 60 = 130.479 kcal
        double kcalUnspecified = CalorieCalculator.calculateActiveCalories(
                70.0, 175.0, 30, SettingsRepository.Gender.UNSPECIFIED, 3600L
        );
        assertEquals(130.48, kcalUnspecified, 0.05);
    }
}
