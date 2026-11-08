package com.pulse.tracker.domain;

/**
 * Pure domain calculator for step-to-distance conversions and default biometric stride derivations.
 * All internal distance operations use metric meters and centimeters.
 */
public final class DistanceCalculator {

    public static final double STRIDE_HEIGHT_RATIO = 0.415;
    public static final double METERS_PER_KILOMETER = 1000.0;
    public static final double METERS_PER_MILE = 1609.344;

    private DistanceCalculator() {
        // Utility / domain calculation class; prevent direct instantiation
    }

    /**
     * Calculates total distance traveled in meters from step count and stride length in centimeters.
     * Formula: steps * strideLengthCm / 100.0
     *
     * @param steps          Number of steps taken (must be non-negative).
     * @param strideLengthCm Stride length in centimeters (must be non-negative).
     * @return Distance in meters, or 0.0 if inputs are zero or invalid.
     */
    public static double calculateDistanceMeters(long steps, double strideLengthCm) {
        if (steps <= 0 || strideLengthCm <= 0.0 || Double.isNaN(strideLengthCm) || Double.isInfinite(strideLengthCm)) {
            return 0.0;
        }
        return steps * strideLengthCm / 100.0;
    }

    /**
     * Converts a distance in meters to kilometers.
     *
     * @param meters Distance in meters.
     * @return Distance in kilometers, or 0.0 if meters <= 0.
     */
    public static double metersToKilometers(double meters) {
        if (meters <= 0.0 || Double.isNaN(meters) || Double.isInfinite(meters)) {
            return 0.0;
        }
        return meters / METERS_PER_KILOMETER;
    }

    /**
     * Converts a distance in meters to international statute miles.
     * 1 statute mile = 1,609.344 meters.
     *
     * @param meters Distance in meters.
     * @return Distance in miles, or 0.0 if meters <= 0.
     */
    public static double metersToMiles(double meters) {
        if (meters <= 0.0 || Double.isNaN(meters) || Double.isInfinite(meters)) {
            return 0.0;
        }
        return meters / METERS_PER_MILE;
    }

    /**
     * Calculates the default estimated stride length in centimeters derived from user height.
     * Formula: heightCm * 0.415
     *
     * @param heightCm User height in centimeters.
     * @return Stride length in centimeters, or 0.0 if height <= 0.
     */
    public static double calculateDefaultStrideCm(double heightCm) {
        if (heightCm <= 0.0 || Double.isNaN(heightCm) || Double.isInfinite(heightCm)) {
            return 0.0;
        }
        return heightCm * STRIDE_HEIGHT_RATIO;
    }

    /**
     * Converts kilometers to meters.
     */
    public static double kilometersToMeters(double kilometers) {
        if (kilometers <= 0.0 || Double.isNaN(kilometers) || Double.isInfinite(kilometers)) {
            return 0.0;
        }
        return kilometers * METERS_PER_KILOMETER;
    }

    /**
     * Converts miles to meters.
     */
    public static double milesToMeters(double miles) {
        if (miles <= 0.0 || Double.isNaN(miles) || Double.isInfinite(miles)) {
            return 0.0;
        }
        return miles * METERS_PER_MILE;
    }
}
