package com.luopan.fengshui;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * F-17（LC 自建，非 1:1）罗盘显示参数存储。
 *
 * 原版工具条 13 个按钮里没有「透明度 / 天支线」，dex 只有字符串无承载类（见 docs/features/F-17-透明度-天支线.md）。
 * 因此本组参数是 LC 自己定义的增量，存独立 prefs "lc_compass"，不与 MapManager（gjc02/地图设置）混用。
 *
 * keys：
 * - dialAlphaPercent：0..100，默认 100（只作用于底盘位图 alpha）
 * - tianzhiLines：布尔，默认 false
 */
final class CompassDisplayStore {

    static final String PREFS = "lc_compass";
    static final String K_ALPHA = "dialAlphaPercent";
    static final String K_TIANZHI = "tianzhiLines";

    private final SharedPreferences p;

    CompassDisplayStore(Context c) {
        p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    int alphaPercent() {
        return Math.max(0, Math.min(100, p.getInt(K_ALPHA, 100)));
    }

    void setAlphaPercent(int percent) {
        p.edit().putInt(K_ALPHA, Math.max(0, Math.min(100, percent))).apply();
    }

    boolean tianzhiLines() {
        return p.getBoolean(K_TIANZHI, false);
    }

    void setTianzhiLines(boolean on) {
        p.edit().putBoolean(K_TIANZHI, on).apply();
    }
}
