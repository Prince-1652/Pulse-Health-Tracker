package com.pulse.tracker.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.domain.DistanceCalculator;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Localized formatting utility for steps, distances, active times, pace, calories,
 * and profile physical metrics supporting metric and imperial unit systems.
 */
public final class UnitFormatter {

    public static final String EM_DASH = "—";

    private UnitFormatter() {
        // Utility class; prevent instantiation
    }

    /**
     * Formats step count with locale-aware thousands grouping (e.g. "8,420").
     */
    @NonNull
    public static String formatSteps(long steps) {
        long safeSteps = Math.max(0, steps);
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(safeSteps);
    }

    /**
     * Formats distance in kilometers or statute miles with two decimal places (e.g. "5.24 km" or "3.26 mi").
     */
    @NonNull
    public static String formatDistance(double meters, @Nullable SettingsRepository.UnitSystem unitSystem) {
        double safeMeters = Math.max(0.0, meters);
        if (Double.isNaN(safeMeters) || Double.isInfinite(safeMeters)) {
            safeMeters = 0.0;
        }

        SettingsRepository.UnitSystem system = (unitSystem != null) ? unitSystem : SettingsRepository.UnitSystem.METRIC;

        if (system == SettingsRepository.UnitSystem.IMPERIAL) {
            double miles = DistanceCalculator.metersToMiles(safeMeters);
            return String.format(Locale.getDefault(), "%.2f mi", miles);
        } else {
            double km = DistanceCalculator.metersToKilometers(safeMeters);
            return String.format(Locale.getDefault(), "%.2f km", km);
        }
    }

    /**
     * Formats calories burned as whole kcal (e.g. "342 kcal") or "—" if unavailable or zero.
     */
    @NonNull
    public static String formatCalories(double kcal) {
        if (kcal <= 0.0 || Double.isNaN(kcal) || Double.isInfinite(kcal)) {
            return EM_DASH;
        }
        long rounded = Math.round(kcal);
        String formattedNumber = NumberFormat.getIntegerInstance(Locale.getDefault()).format(rounded);
        return formattedNumber + " kcal";
    }

    /**
     * Formats active movement time into human-readable duration (e.g. "45 min" or "1h 15m").
     */
    @NonNull
    public static String formatActiveTime(long seconds) {
        if (seconds <= 0) {
            return "0 min";
        }
        if (seconds < 60) {
            return "< 1 min";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        if (hours > 0) {
            if (minutes > 0) {
                return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
            } else {
                return String.format(Locale.getDefault(), "%dh", hours);
            }
        } else {
            return String.format(Locale.getDefault(), "%d min", minutes);
        }
    }

    /**
     * Formats pace as minutes and seconds per unit distance (e.g. "5'30\" /km" or "8'52\" /mi").
     * Returns "— min/km" or "— min/mi" when distance is zero or inputs are invalid.
     */
    @NonNull
    public static String formatPace(double meters, long seconds, @Nullable SettingsRepository.UnitSystem unitSystem) {
        SettingsRepository.UnitSystem system = (unitSystem != null) ? unitSystem : SettingsRepository.UnitSystem.METRIC;
        String fallback = (system == SettingsRepository.UnitSystem.IMPERIAL) ? (EM_DASH + " min/mi") : (EM_DASH + " min/km");

        if (meters <= 0.0 || seconds <= 0 || Double.isNaN(meters) || Double.isInfinite(meters)) {
            return fallback;
        }

        double distance = (system == SettingsRepository.UnitSystem.IMPERIAL)
                ? DistanceCalculator.metersToMiles(meters)
                : DistanceCalculator.metersToKilometers(meters);

        if (distance <= 0.0001) {
            return fallback;
        }

        double paceSeconds = seconds / distance;
        // Cap excessive pace values (> 100 hours per km/mi) as invalid
        if (paceSeconds > 359_999.0 || Double.isNaN(paceSeconds) || Double.isInfinite(paceSeconds)) {
            return fallback;
        }

        long paceMin = (long) (paceSeconds / 60);
        long paceSec = (long) (paceSeconds % 60);
        String unitSuffix = (system == SettingsRepository.UnitSystem.IMPERIAL) ? "/mi" : "/km";

        return String.format(Locale.getDefault(), "%d'%02d\" %s", paceMin, paceSec, unitSuffix);
    }

    /**
     * Formats stride length (e.g. "71 cm" or "28.0 in").
     */
    @NonNull
    public static String formatStride(double cm, @Nullable SettingsRepository.UnitSystem unitSystem) {
        double safeCm = Math.max(0.0, cm);
        if (Double.isNaN(safeCm) || Double.isInfinite(safeCm)) {
            safeCm = 0.0;
        }

        SettingsRepository.UnitSystem system = (unitSystem != null) ? unitSystem : SettingsRepository.UnitSystem.METRIC;
        if (system == SettingsRepository.UnitSystem.IMPERIAL) {
            double inches = SettingsRepository.cmToInches(safeCm);
            return String.format(Locale.getDefault(), "%.1f in", inches);
        } else {
            return String.format(Locale.getDefault(), "%.0f cm", safeCm);
        }
    }

    /**
     * Formats weight (e.g. "70.0 kg" or "154.3 lb").
     */
    @NonNull
    public static String formatWeight(double kg, @Nullable SettingsRepository.UnitSystem unitSystem) {
        double safeKg = Math.max(0.0, kg);
        if (Double.isNaN(safeKg) || Double.isInfinite(safeKg)) {
            safeKg = 0.0;
        }

        SettingsRepository.UnitSystem system = (unitSystem != null) ? unitSystem : SettingsRepository.UnitSystem.METRIC;
        if (system == SettingsRepository.UnitSystem.IMPERIAL) {
            double lbs = SettingsRepository.kgToLbs(safeKg);
            return String.format(Locale.getDefault(), "%.1f lb", lbs);
        } else {
            return String.format(Locale.getDefault(), "%.1f kg", safeKg);
        }
    }

    /**
     * Formats height (e.g. "175 cm" or "5 ft 9 in").
     */
    @NonNull
    public static String formatHeight(double cm, @Nullable SettingsRepository.UnitSystem unitSystem) {
        double safeCm = Math.max(0.0, cm);
        if (Double.isNaN(safeCm) || Double.isInfinite(safeCm)) {
            safeCm = 0.0;
        }

        SettingsRepository.UnitSystem system = (unitSystem != null) ? unitSystem : SettingsRepository.UnitSystem.METRIC;
        if (system == SettingsRepository.UnitSystem.IMPERIAL) {
            double totalInches = SettingsRepository.cmToInches(safeCm);
            int feet = (int) (totalInches / 12);
            int inches = (int) Math.round(totalInches % 12);
            if (inches >= 12) {
                feet += 1;
                inches = 0;
            }
            return String.format(Locale.getDefault(), "%d ft %d in", feet, inches);
        } else {
            return String.format(Locale.getDefault(), "%.0f cm", safeCm);
        }
    }
}
