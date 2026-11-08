package com.pulse.tracker.domain;

import androidx.annotation.Nullable;

import com.pulse.tracker.data.repository.SettingsRepository;

/**
 * Pure domain calculator for estimated Basal Metabolic Rate (Mifflin-St Jeor)
 * and net active walking calories.
 * Note: All calorie values are estimates for fitness tracking, not medical measurements.
 */
public final class CalorieCalculator {

    /**
     * Conservative walking Metabolic Equivalent of Task (MET) assumption for moderate walking.
     */
    public static final double DEFAULT_WALKING_MET = 3.0;

    public static final double MINUTES_PER_DAY = 1440.0;
    public static final double SECONDS_PER_MINUTE = 60.0;

    private CalorieCalculator() {
        // Utility / domain calculation class; prevent direct instantiation
    }

    /**
     * Calculates the estimated Basal Metabolic Rate (BMR) in kcal/day using the Mifflin-St Jeor equations:
     * - Male:   10 * weightKg + 6.25 * heightCm - 5 * ageYears + 5
     * - Female: 10 * weightKg + 6.25 * heightCm - 5 * ageYears - 161
     * <p>
     * If gender is UNSPECIFIED or null, no equation is silently assumed and 0.0 is returned.
     *
     * @param weightKg User body mass in kilograms.
     * @param heightCm User stature in centimeters.
     * @param ageYears User age in completed years.
     * @param gender   User gender (MALE, FEMALE, or UNSPECIFIED).
     * @return Estimated daily BMR in kcal, or 0.0 if inputs are invalid or gender is unspecified.
     */
    public static double calculateBmr(double weightKg, double heightCm, int ageYears,
                                      @Nullable SettingsRepository.Gender gender) {
        if (gender == null || gender == SettingsRepository.Gender.UNSPECIFIED) {
            return 0.0;
        }

        if (weightKg <= 0.0 || heightCm <= 0.0 || ageYears <= 0 ||
                Double.isNaN(weightKg) || Double.isInfinite(weightKg) ||
                Double.isNaN(heightCm) || Double.isInfinite(heightCm)) {
            return 0.0;
        }

        double base = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * ageYears);

        double bmr;
        if (gender == SettingsRepository.Gender.MALE) {
            bmr = base + 5.0;
        } else if (gender == SettingsRepository.Gender.FEMALE) {
            bmr = base - 161.0;
        } else {
            return 0.0;
        }

        return Math.max(0.0, bmr);
    }

    /**
     * Calculates estimated net active calories burned during physical activity above resting baseline.
     * Formula:
     * activeKcal = (BMR / 1440.0) * Math.max(0, MET - 1.0) * (activeSeconds / 60.0)
     * <p>
     * Subtracting 1.0 MET ensures only energy expenditure above resting baseline is counted.
     *
     * @param bmr           User Basal Metabolic Rate in kcal/day (must be > 0).
     * @param activeSeconds Duration of physical movement in seconds.
     * @param met           Metabolic Equivalent of Task (walking typically ~3.0).
     * @return Net active calories in kcal, or 0.0 if inputs are zero/invalid or MET <= 1.0.
     */
    public static double calculateNetActiveCalories(double bmr, long activeSeconds, double met) {
        if (bmr <= 0.0 || activeSeconds <= 0 || Double.isNaN(bmr) || Double.isInfinite(bmr) ||
                Double.isNaN(met) || Double.isInfinite(met)) {
            return 0.0;
        }

        double netMet = Math.max(0.0, met - 1.0);
        if (netMet <= 0.0) {
            return 0.0;
        }

        double activeMinutes = activeSeconds / SECONDS_PER_MINUTE;
        double kcalPerMinute = (bmr / MINUTES_PER_DAY) * netMet;
        double activeKcal = kcalPerMinute * activeMinutes;

        if (Double.isNaN(activeKcal) || Double.isInfinite(activeKcal) || activeKcal < 0.0) {
            return 0.0;
        }

        return activeKcal;
    }

    /**
     * Calculates estimated net active calories using the default moderate walking MET of 3.0.
     *
     * @param bmr           User Basal Metabolic Rate in kcal/day.
     * @param activeSeconds Duration of physical movement in seconds.
     * @return Net active calories in kcal.
     */
    public static double calculateNetActiveCalories(double bmr, long activeSeconds) {
        return calculateNetActiveCalories(bmr, activeSeconds, DEFAULT_WALKING_MET);
    }

    /**
     * Convenience method combining BMR derivation and net active calorie calculation from biometrics.
     *
     * @param weightKg      User body mass in kilograms.
     * @param heightCm      User stature in centimeters.
     * @param ageYears      User age in years.
     * @param gender        User gender.
     * @param activeSeconds Duration of physical movement in seconds.
     * @return Net active calories in kcal, or 0.0 if biometrics/gender are incomplete or unspecified.
     */
    public static double calculateActiveCalories(double weightKg, double heightCm, int ageYears,
                                                 @Nullable SettingsRepository.Gender gender,
                                                 long activeSeconds) {
        double bmr = calculateBmr(weightKg, heightCm, ageYears, gender);
        if (bmr <= 0.0) {
            return 0.0;
        }
        return calculateNetActiveCalories(bmr, activeSeconds, DEFAULT_WALKING_MET);
    }
}
