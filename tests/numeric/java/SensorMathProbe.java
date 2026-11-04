package com.luopan.fengshui;

import java.util.Locale;

/**
 * P0-1 JVM 探针：只测 `SensorMath`（纯静态、无 android.* 依赖）。
 * 由 `tests/numeric/test_sensor_math.py` 调用；不进入 APK。
 *
 * 输出（ASCII）：tilt / clamp / decl 三行，全部用 Locale.ROOT。
 */
public final class SensorMathProbe {

    public static void main(String[] args) {
        System.out.printf(Locale.ROOT, "tilt %.6f %.6f %.6f %.6f%n",
                SensorMath.tiltDegrees(0f, 0f),
                SensorMath.tiltDegrees(10f, 0f),
                SensorMath.tiltDegrees(0f, 10f),
                SensorMath.tiltDegrees(10f, 10f));
        System.out.printf(Locale.ROOT, "clamp %.1f %.1f%n",
                SensorMath.clampDeclination(45f),
                SensorMath.clampDeclination(-45f));
        System.out.printf(Locale.ROOT, "decl %.2f %.2f %.2f%n",
                SensorMath.applyDeclination(359.99f, -7.5f, true),
                SensorMath.applyDeclination(0f, 5f, false),
                SensorMath.applyDeclination(94f, 5f, true));
    }
}
