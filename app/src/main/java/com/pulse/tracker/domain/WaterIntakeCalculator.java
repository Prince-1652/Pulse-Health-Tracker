package com.pulse.tracker.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pulse.tracker.data.repository.SettingsRepository;

/**
 * Pure domain calculator determining evidence-based daily hydration targets
 * grounded in published clinical guidelines:
 * - European Food Safety Authority (EFSA 2010 Scientific Opinion on Dietary Reference Values for Water)
 * - Institute of Medicine / National Academies of Medicine (IOM/NAM 2005 Dietary Reference Intakes)
 * - Holliday & Segar (1957 Maintenance Need for Water in Parenteral Fluid Therapy)
 * - Yamada et al. (Science 2022 Variation in human water turnover associated with environmental and lifestyle factors)
 * - DuBois & DuBois (1916 Body Surface Area formula)
 *
 * All hydration targets are subject to clinically safe bounds [1200 ml, 5000 ml]
 * and rounded to the nearest 50 ml increment.
 */
public final class WaterIntakeCalculator {

    public static final double MULTIPLIER_MALE = 35.0;        // 35.0 ml/kg
    public static final double MULTIPLIER_FEMALE = 31.0;      // 31.0 ml/kg
    public static final double MULTIPLIER_UNSPECIFIED = 33.0; // 33.0 ml/kg

    public static final double REFERENCE_BSA = 1.73;          // Standard average adult BSA in m^2
    public static final double BSA_EVAPORATION_FACTOR = 350.0;// 350 ml per m^2 variance from 1.73

    public static final double AGE_YOUNG_THRESHOLD = 30;      // <30 years
    public static final double AGE_SENIOR_THRESHOLD = 55;     // >55 years
    public static final double AGE_YOUNG_BONUS = 0.05;        // +5%
    public static final double AGE_SENIOR_PENALTY = -0.05;    // -5%

    public static final long SEDENTARY_STEP_THRESHOLD = 3000L;// Sedentary threshold
    public static final double ML_PER_1000_STEPS = 50.0;      // +50 ml per 1,000 steps above threshold

    public static final int MIN_SAFE_WATER_ML = 1200;         // 1.2 L minimum clinical floor
    public static final int MAX_SAFE_WATER_ML = 5000;         // 5.0 L maximum safe ceiling
    public static final int ROUNDING_INCREMENT_ML = 50;

    public static final int DEFAULT_FALLBACK_TARGET_ML = 2500;

    private WaterIntakeCalculator() {
        // Pure domain calculation utility; prevent direct instantiation
    }

    /**
     * Value object encapsulating all individual components of the hydration calculation.
     */
    public static final class Breakdown {
        private final double baseMl;
        private final double bsa;
        private final double bsaAdjustmentMl;
        private final double ageAdjustmentMl;
        private final double stepCompensationMl;
        private final double rawTotalMl;
        private final int targetMl;

        public Breakdown(double baseMl, double bsa, double bsaAdjustmentMl,
                         double ageAdjustmentMl, double stepCompensationMl,
                         double rawTotalMl, int targetMl) {
            this.baseMl = baseMl;
            this.bsa = bsa;
            this.bsaAdjustmentMl = bsaAdjustmentMl;
            this.ageAdjustmentMl = ageAdjustmentMl;
            this.stepCompensationMl = stepCompensationMl;
            this.rawTotalMl = rawTotalMl;
            this.targetMl = targetMl;
        }

        public double getBaseMl() {
            return baseMl;
        }

        public double getBsa() {
            return bsa;
        }

        public double getBsaAdjustmentMl() {
            return bsaAdjustmentMl;
        }

        public double getAgeAdjustmentMl() {
            return ageAdjustmentMl;
        }

        public double getStepCompensationMl() {
            return stepCompensationMl;
        }

        public double getRawTotalMl() {
            return rawTotalMl;
        }

        public int getTargetMl() {
            return targetMl;
        }
    }

    /**
     * Calculates Body Surface Area (BSA) in m^2 using the DuBois & DuBois formula:
     * BSA = 0.007184 * heightCm^0.725 * weightKg^0.425
     *
     * @param heightCm User height in centimeters.
     * @param weightKg User weight in kilograms.
     * @return Body Surface Area in m^2.
     */
    public static double calculateBsa(double heightCm, double weightKg) {
        if (heightCm <= 0.0 || weightKg <= 0.0 ||
                Double.isNaN(heightCm) || Double.isInfinite(heightCm) ||
                Double.isNaN(weightKg) || Double.isInfinite(weightKg)) {
            return REFERENCE_BSA;
        }
        return 0.007184 * Math.pow(heightCm, 0.725) * Math.pow(weightKg, 0.425);
    }

    /**
     * Resolves the gender-specific base fluid multiplier in ml/kg.
     */
    public static double getGenderMultiplier(@Nullable SettingsRepository.Gender gender) {
        if (gender == null || gender == SettingsRepository.Gender.UNSPECIFIED) {
            return MULTIPLIER_UNSPECIFIED;
        }
        switch (gender) {
            case MALE:
                return MULTIPLIER_MALE;
            case FEMALE:
                return MULTIPLIER_FEMALE;
            default:
                return MULTIPLIER_UNSPECIFIED;
        }
    }

    /**
     * Computes the complete breakdown of water intake recommendations.
     *
     * @param weightKg   Body mass in kilograms.
     * @param heightCm   Stature in centimeters.
     * @param ageYears   Age in years.
     * @param gender     User gender.
     * @param dailySteps Current daily steps tracked today.
     * @return Breakdown object containing all computed components and bounded target.
     */
    @NonNull
    public static Breakdown calculateBreakdown(double weightKg, double heightCm, int ageYears,
                                               @Nullable SettingsRepository.Gender gender,
                                               long dailySteps) {
        double safeWeight = (weightKg > 0.0 && !Double.isNaN(weightKg) && !Double.isInfinite(weightKg))
                ? weightKg : SettingsRepository.DEFAULT_WEIGHT_KG;
        double safeHeight = (heightCm > 0.0 && !Double.isNaN(heightCm) && !Double.isInfinite(heightCm))
                ? heightCm : SettingsRepository.DEFAULT_HEIGHT_CM;
        int safeAge = (ageYears > 0) ? ageYears : SettingsRepository.DEFAULT_AGE;
        long safeSteps = Math.max(0L, dailySteps);

        // 1. Base calculation: weightKg * gender multiplier
        double multiplier = getGenderMultiplier(gender);
        double baseMl = safeWeight * multiplier;

        // 2. Body Surface Area (BSA) evaporative adjustment: (BSA - 1.73) * 350.0 ml
        double bsa = calculateBsa(safeHeight, safeWeight);
        double bsaAdjustmentMl = (bsa - REFERENCE_BSA) * BSA_EVAPORATION_FACTOR;
        double intermediateBaseline = baseMl + bsaAdjustmentMl;

        // 3. Age adjustment: <30 years (+5%), >55 years (-5%)
        double ageAdjustmentMl = 0.0;
        if (safeAge < AGE_YOUNG_THRESHOLD) {
            ageAdjustmentMl = intermediateBaseline * AGE_YOUNG_BONUS;
        } else if (safeAge > AGE_SENIOR_THRESHOLD) {
            ageAdjustmentMl = intermediateBaseline * AGE_SENIOR_PENALTY;
        }
        double ageAdjustedTotal = intermediateBaseline + ageAdjustmentMl;

        // 4. Real-time activity compensation from daily steps: +50 ml per 1,000 steps above 3,000
        double stepCompensationMl = 0.0;
        if (safeSteps > SEDENTARY_STEP_THRESHOLD) {
            double activeStepsAboveThreshold = (double) (safeSteps - SEDENTARY_STEP_THRESHOLD);
            stepCompensationMl = (activeStepsAboveThreshold / 1000.0) * ML_PER_1000_STEPS;
        }

        // 5. Total unrounded intake
        double rawTotalMl = ageAdjustedTotal + stepCompensationMl;

        // 6. Round to nearest 50 ml and clamp within clinically safe bounds [1200, 5000]
        int rounded = (int) (Math.round(rawTotalMl / (double) ROUNDING_INCREMENT_ML) * ROUNDING_INCREMENT_ML);
        int finalTargetMl = Math.max(MIN_SAFE_WATER_ML, Math.min(MAX_SAFE_WATER_ML, rounded));

        return new Breakdown(baseMl, bsa, bsaAdjustmentMl, ageAdjustmentMl, stepCompensationMl, rawTotalMl, finalTargetMl);
    }

    /**
     * Calculates the daily target water intake in ml rounded to nearest 50 ml
     * and clamped within [1200 ml, 5000 ml].
     *
     * @param weightKg   Body mass in kilograms.
     * @param heightCm   Stature in centimeters.
     * @param ageYears   Age in years.
     * @param gender     User gender.
     * @param dailySteps Current daily steps tracked today.
     * @return Target water intake in ml.
     */
    public static int calculateTarget(double weightKg, double heightCm, int ageYears,
                                      @Nullable SettingsRepository.Gender gender,
                                      long dailySteps) {
        return calculateBreakdown(weightKg, heightCm, ageYears, gender, dailySteps).getTargetMl();
    }
}
