package com.luopan.fengshui;

import android.app.Activity;
import android.content.Intent;

/**
 * 收藏点 → 地图的零散 Intent 约定（缺口线自用，K-01 合流后如有统一导航入口再迁）。
 * 目标页 MainActivity 读取 lat/lng 后调 map.html 的 setView(lat,lng,zoom)。
 */
final class MapJump {

    static final String EXTRA_LAT = "lc_lat";
    static final String EXTRA_LNG = "lc_lng";
    /** 跳转缩放级别（语义由我们定，原版未取证。16 ≈ 街区级）。 */
    static final int ZOOM = 16;

    private MapJump() {
    }

    static Intent intent(Activity from, double lat, double lng) {
        Intent i = new Intent(from, MainActivity.class);
        i.putExtra(EXTRA_LAT, lat);
        i.putExtra(EXTRA_LNG, lng);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return i;
    }

    /**
     * 读 double extra，兼容 adb `--ef`（Float）与 `--ed`（Double）两种写法：
     * getDoubleExtra 遇到 Float 会返回默认值，AT 用 --ef 传参就会丢，故按 Number 解析。
     */
    static double doubleExtra(Intent intent, String key) {
        if (intent == null || intent.getExtras() == null) return Double.NaN;
        Object o = intent.getExtras().get(key);
        return o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
    }
}
