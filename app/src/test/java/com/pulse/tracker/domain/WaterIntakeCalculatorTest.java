package com.pulse.tracker.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.pulse.tracker.data.repository.SettingsRepository;

import org.junit.Test;

public class WaterIntakeCalculatorTest {

    private static final double DELTA = 0.001;

    @Test
    public void testDuBoisBsaCalculation() {
        // Reference standard adult: 170 cm, 70 kg
        // BSA = 0.007184 * 170^0.725 * 70^0.425 ~ 1.802 m^2
        double bsa = WaterIntakeCalculator.calculateBsa(170.0, 70.0);
        assertTrue("BSA should be around 1.802 m^2", bsa > 1.79 && bsa < 1.82);
    }

    @Test
    public void testStandardMaleCalculation() {
        // 70 kg male, 170 cm, 30 years old, 0 steps
        // Base: 70 * 35 = 2450.0 ml
        // BSA ~ 1.802 -> (1.802 - 1.73) * 350 ~ 25.2 ml
        // Age 30 -> 0%
        // Total raw ~ 2475.2 ml -> rounded to nearest 50 -> 2500 ml
        int target = WaterIntakeCalculator.calculateTarget(70.0, 170.0, 30, SettingsRepository.Gender.MALE, 0L);
        assertEquals(2500, target);
    }

    @Test
    public void testStandardFemaleCalculation() {
        // 60 kg female, 165 cm, 30 years old, 0 steps
        // Base: 60 * 31 = 1860.0 ml
        // BSA = 0.007184 * 165^0.725 * 60^0.425 ~ 1.660 m^2
        // BSA diff = (1.660 - 1.73) * 350 ~ -24.5 ml
        // Intermediate ~ 1835.5 ml
        // Age 30 -> 0%
        // Total raw ~ 1835.5 ml -> rounded to nearest 50 -> 1850 ml
        int target = WaterIntakeCalculator.calculateTarget(60.0, 165.0, 30, SettingsRepository.Gender.FEMALE, 0L);
        assertEquals(1850, target);
    }

    @Test
    public void testUnspecifiedGenderUses33MlPerKg() {
        double multNull = WaterIntakeCalculator.getGenderMultiplier(null);
        double multUnspecified = WaterIntakeCalculator.getGenderMultiplier(SettingsRepository.Gender.UNSPECIFIED);
        assertEquals(33.0, multNull, DELTA);
        assertEquals(33.0, multUnspecified, DELTA);
    }

    @Test
    public void testYoungAgeAdjustmentBonus() {
        // Age 25 (< 30) receives +5% bonus on intermediate baseline
        WaterIntakeCalculator.Breakdown breakdown =
                WaterIntakeCalculator.calculateBreakdown(70.0, 170.0, 25, SettingsRepository.Gender.MALE, 0L);

        double intermediate = breakdown.getBaseMl() + breakdown.getBsaAdjustmentMl();
        double expectedAgeBonus = intermediate * 0.05;
        assertEquals(expectedAgeBonus, breakdown.getAgeAdjustmentMl(), DELTA);
        assertTrue(breakdown.getTargetMl() >= 2500);
    }

    @Test
    public void testSeniorAgeAdjustmentReduction() {
        // Age 65 (> 55) receives -5% adjustment on intermediate baseline
        WaterIntakeCalculator.Breakdown breakdown =
                WaterIntakeCalculator.calculateBreakdown(70.0, 170.0, 65, SettingsRepository.Gender.MALE, 0L);

        double intermediate = breakdown.getBaseMl() + breakdown.getBsaAdjustmentMl();
        double expectedAgeReduction = intermediate * -0.05;
        assertEquals(expectedAgeReduction, breakdown.getAgeAdjustmentMl(), DELTA);
        assertTrue(breakdown.getTargetMl() <= 2400);
    }

    @Test
    public void testStepActivityCompensation() {
        // Under sedentary threshold (<= 3,000 steps), compensation is 0
        WaterIntakeCalculator.Breakdown sedentary =
                WaterIntakeCalculator.calculateBreakdown(70.0, 170.0, 30, SettingsRepository.Gender.MALE, 2500L);
        assertEquals(0.0, sedentary.getStepCompensationMl(), DELTA);

        WaterIntakeCalculator.Breakdown threshold =
                WaterIntakeCalculator.calculateBreakdown(70.0, 170.0, 30, SettingsRepository.Gender.MALE, 3000L);
        assertEquals(0.0, threshold.getStepCompensationMl(), DELTA);

        // 8,000 steps = 5,000 steps above threshold -> 5 * 50 = +250 ml
        WaterIntakeCalculator.Breakdown active =
                WaterIntakeCalculator.calculateBreakdown(70.0, 170.0, 30, SettingsRepository.Gender.MALE, 8000L);
        assertEquals(250.0, active.getStepCompensationMl(), DELTA);

        // Target should increase accordingly
        assertEquals(sedentary.getTargetMl() + 250, active.getTargetMl());
    }

    @Test
    public void testClinicallySafeLowerBoundClamping() {
        // Extremely low weight: 20 kg
        // Unclamped would be far below 1200 ml, must be clamped to 1200 ml floor
        int target = WaterIntakeCalculator.calculateTarget(20.0, 100.0, 20, SettingsRepository.Gender.FEMALE, 0L);
        assertEquals(WaterIntakeCalculator.MIN_SAFE_WATER_ML, target);
        assertEquals(1200, target);
    }

    @Test
    public void testClinicallySafeUpperBoundClamping() {
        // High mass + extreme step count: 160 kg, 35,000 steps
        // Raw would exceed 5000 ml, must be clamped to 5000 ml ceiling
        int target = WaterIntakeCalculator.calculateTarget(160.0, 200.0, 25, SettingsRepository.Gender.MALE, 35000L);
        assertEquals(WaterIntakeCalculator.MAX_SAFE_WATER_ML, target);
        assertEquals(5000, target);
    }

    @Test
    public void testRoundingToNearest50Ml() {
        // Test multiple combinations to ensure result is always a multiple of 50
        for (int steps = 0; steps <= 15000; steps += 333) {
            int target = WaterIntakeCalculator.calculateTarget(73.5, 176.2, 28, SettingsRepository.Gender.MALE, steps);
            assertEquals(0, target % 50);
        }
    }

    @Test
    public void testBreakdownMathematicalConsistency() {
        WaterIntakeCalculator.Breakdown breakdown =
                WaterIntakeCalculator.calculateBreakdown(82.0, 182.0, 34, SettingsRepository.Gender.MALE, 11400L);

        double expectedSum = breakdown.getBaseMl() + breakdown.getBsaAdjustmentMl()
                + breakdown.getAgeAdjustmentMl() + breakdown.getStepCompensationMl();
        assertEquals(expectedSum, breakdown.getRawTotalMl(), DELTA);

        int expectedRounded = (int) (Math.round(breakdown.getRawTotalMl() / 50.0) * 50);
        int expectedClamped = Math.max(1200, Math.min(5000, expectedRounded));
        assertEquals(expectedClamped, breakdown.getTargetMl());
    }

    @Test
    public void testInvalidAndEdgeInputsFallbackSafely() {
        // Negative / zero / NaN inputs should not crash or return invalid targets
        int target1 = WaterIntakeCalculator.calculateTarget(-50.0, 0.0, -5, null, -100L);
        assertTrue(target1 >= 1200 && target1 <= 5000);
        assertEquals(0, target1 % 50);

        int target2 = WaterIntakeCalculator.calculateTarget(Double.NaN, Double.POSITIVE_INFINITY, 0, null, 0L);
        assertTrue(target2 >= 1200 && target2 <= 5000);
        assertEquals(0, target2 % 50);
    }
}
