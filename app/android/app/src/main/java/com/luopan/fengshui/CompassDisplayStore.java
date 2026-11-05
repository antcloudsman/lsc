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
 * - dialAlphaPercent：30..100，默认 100（只作用于底盘位图 alpha）
 *   下限 30 是硬约束：0% 会让整盘消失（artwork 模式下矢量环被跳过），而且会被持久化，
 *   下次冷启直接空盘——属于「可持久化的坏状态」，所以不提供 0%。
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

    /** 读取并把旧版本可能存下的 <30 值迁移到下限（写回，避免每次冷启都读坏值）。 */
    int alphaPercent() {
        int v = p.getInt(K_ALPHA, 100);
        int clamped = Math.max(MIN_ALPHA, Math.min(100, v));
        if (clamped != v) {
            p.edit().putInt(K_ALPHA, clamped).apply();
            android.util.Log.d("LCCOMPASS", "dialAlphaPercent " + v + " -> " + clamped + " (min clamp)");
        }
        return clamped;
    }

    void setAlphaPercent(int percent) {
        p.edit().putInt(K_ALPHA, Math.max(MIN_ALPHA, Math.min(100, percent))).apply();
    }

    /** F-17 透明度下限：低于 30% 盘面基本不可辨，且会被持久化，故不提供 0%。 */
    static final int MIN_ALPHA = 30;

    boolean tianzhiLines() {
        return p.getBoolean(K_TIANZHI, false);
    }

    void setTianzhiLines(boolean on) {
        p.edit().putBoolean(K_TIANZHI, on).apply();
    }
}
