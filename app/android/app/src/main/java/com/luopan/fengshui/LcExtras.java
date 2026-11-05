package com.luopan.fengshui;

import android.content.Intent;
import android.os.Bundle;

/**
 * Intent 取值工具：本机 `am` 只认 `--ef`（float），`adb` 注入的是 Float，
 * 而客户端 `putExtra(double)` 是 Double；`getDoubleExtra` 只认 Double，
 * 用 `--ef` 会静默取默认值。这里两种都接，外加 Number/String 兜底。
 */
final class LcExtras {

    static double d(Intent intent, String key, double def) {
        if (intent == null) return def;
        Bundle b = intent.getExtras();
        if (b == null) return def;
        Object v = b.get(key);
        if (v instanceof Double) return (Double) v;
        if (v instanceof Float) return ((Float) v).doubleValue();
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try {
                return Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private LcExtras() {
    }
}
