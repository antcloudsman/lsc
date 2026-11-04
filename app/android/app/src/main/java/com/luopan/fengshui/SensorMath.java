package com.luopan.fengshui;

/**
 * P0-1 S1: pure static math, no android.* imports, JVM-testable.
 *
 * Heading pipeline (S5): H = normalize(raw + (trueNorth ? declination : 0)).
 * Every ring / sitting / status readout uses H, never the raw sensor value.
 */
public final class SensorMath {

    private SensorMath() {}

    /** Same rule as CompassData.norm / CompassToolsKt.B1: v %= 360; if v < 0 v += 360. */
    public static float normalize(float d) {
        float x = d % 360f;
        if (x < 0f) x += 360f;
        return x;
    }

    /** Raw magnetic azimuth + optional declination -> true or magnetic heading. */
    public static float applyDeclination(float raw, float decl, boolean trueNorth) {
        return normalize(raw + (trueNorth ? decl : 0f));
    }

    /** Device tilt from horizontal (deg). 0/0 -> 0, 10/0 -> 10, 10/10 -> 14.1. */
    public static float tiltDegrees(float pitchDeg, float rollDeg) {
        double c = Math.cos(Math.toRadians(pitchDeg)) * Math.cos(Math.toRadians(rollDeg));
        c = Math.max(-1.0, Math.min(1.0, c));
        return (float) Math.toDegrees(Math.acos(c));
    }

    public static boolean isFlat(float tilt, float limit) {
        return tilt <= limit;
    }

    /** Declination is clamped to [-30, +30] (AT RT-005). */
    public static float clampDeclination(float decl) {
        return Math.max(-30f, Math.min(30f, decl));
    }
}
