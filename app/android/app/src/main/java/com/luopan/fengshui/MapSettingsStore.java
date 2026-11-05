package com.luopan.fengshui;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 地图设置的本地存储（MapSettingActivity 等价实现的数值源）。
 *
 * <h3>口径</h3>
 * 完全照 docs/gap-modules/MapSettingActivity.md §4（原版 MapManager / MMKV 实例 id = "MapManager"）：
 * <ul>
 *   <li>prefs 文件名用 "MapManager"，键名、类型、默认值逐条照抄；</li>
 *   <li>坐标系持久化值保留原版错拼 {@code gjc02}（GCJ02），不改写，便于日后同设备迁移；</li>
 *   <li>地图滤镜默认 {@code currentColorMode=2}（高亮），5 个栅格参数默认取 HIGHLIGHT 预设。</li>
 * </ul>
 *
 * <h3>与原版的差异</h3>
 * 原版 setter 还会发 EventBus 事件（event_show_road_changed 等）；LC 没有 EventBus，
 * 事件等价物要等地图页接入时再定（由 LC地图/技术总监裁决），本类只负责持久化。
 */
final class MapSettingsStore {

    static final String PREFS = "MapManager";

    // ---- 键名（原版 MMKV 键，逐条照抄）----
    static final String K_SHOW_ROAD = "showRoad";
    static final String K_IS_ROTATE = "isRotate";
    static final String K_BACK_LAST = "backToLastMapCenter";
    static final String K_KEEP_SCREEN_ON = "mapKeepScreenOn";
    static final String K_SHOW_3D_CONTROL = "show3dControl";
    static final String K_SHOW_CENTER_CROSS = "showMapCenterCross";
    static final String K_CURRENT_CRS = "currentCrs";
    static final String K_LNG_LAT_FORMAT = "currentLngLatFormat";
    static final String K_COLOR_MODE = "currentColorMode";
    static final String K_RASTER_BRIGHTNESS_MIN = "rasterBrightnessMin";
    static final String K_RASTER_BRIGHTNESS_MAX = "rasterBrightnessMax";
    static final String K_RASTER_SATURATION = "rasterSaturation";
    static final String K_RASTER_CONTRAST = "rasterContrast";
    static final String K_RASTER_HUE_ROTATE = "rasterHueRotate";

    // ---- 地图滤镜（MapColorParamEnum，doc §2）----
    static final int MODE_COMMON = 0;
    static final int MODE_NATURE = 1;
    static final int MODE_HIGHLIGHT = 2;
    static final int MODE_CUSTOM = 3;

    /** 三组预设：{brightnessMin, brightnessMax, saturation, contrast, hueRotate}；顺序与 UI 一致。 */
    static final float[][] PRESETS = {
            {0.0f, 1.0f, 0.0f, 0.0f, 0.0f},   // COMMON 常规 @0x0002
            {0.0f, 1.0f, 0.4f, 0.1f, 0.0f},   // NATURE 自然 @0x0012
            {0.1f, 1.0f, 0.6f, 0.2f, 0.0f}    // HIGHLIGHT 高亮 @0x0026
    };

    // ---- 坐标系（CrsEnum；gjc02 为原版错拼，必须保留）----
    static final String[] CRS_CODES = {"wgs84", "gjc02", "bd09", "cgcs2000"};
    static final String[] CRS_DISPLAY = {"WGS84", "GCJ02", "BD09", "CGCS2000"};
    static final String CRS_DEFAULT = "wgs84";

    // ---- 经纬度显示格式（LngLatFormatEnum）----
    static final String[] FORMAT_CODES = {"d", "dm", "dms"};
    static final String[] FORMAT_DISPLAY = {"D", "DM", "DMS"};
    static final String FORMAT_DEFAULT = "d";

    private final SharedPreferences p;

    MapSettingsStore(Context c) {
        p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- 6 个开关 ----

    boolean showRoad() {
        return p.getBoolean(K_SHOW_ROAD, true);
    }

    void setShowRoad(boolean v) {
        p.edit().putBoolean(K_SHOW_ROAD, v).apply();
    }

    boolean isRotate() {
        return p.getBoolean(K_IS_ROTATE, false);
    }

    void setIsRotate(boolean v) {
        p.edit().putBoolean(K_IS_ROTATE, v).apply();
    }

    boolean backToLastMapCenter() {
        return p.getBoolean(K_BACK_LAST, false);
    }

    void setBackToLastMapCenter(boolean v) {
        p.edit().putBoolean(K_BACK_LAST, v).apply();
    }

    boolean keepScreenOn() {
        return p.getBoolean(K_KEEP_SCREEN_ON, true);
    }

    void setKeepScreenOn(boolean v) {
        p.edit().putBoolean(K_KEEP_SCREEN_ON, v).apply();
    }

    boolean show3dControl() {
        return p.getBoolean(K_SHOW_3D_CONTROL, false);
    }

    void setShow3dControl(boolean v) {
        p.edit().putBoolean(K_SHOW_3D_CONTROL, v).apply();
    }

    boolean showMapCenterCross() {
        return p.getBoolean(K_SHOW_CENTER_CROSS, true);
    }

    void setShowMapCenterCross(boolean v) {
        p.edit().putBoolean(K_SHOW_CENTER_CROSS, v).apply();
    }

    // ---- 下拉 ----

    String crsCode() {
        String s = p.getString(K_CURRENT_CRS, "");
        return crsIndex(s) >= 0 ? s : CRS_DEFAULT;
    }

    void setCrsCode(String code) {
        p.edit().putString(K_CURRENT_CRS, code).apply();
    }

    String lngLatFormatCode() {
        String s = p.getString(K_LNG_LAT_FORMAT, "");
        return formatIndex(s) >= 0 ? s : FORMAT_DEFAULT;
    }

    void setLngLatFormatCode(String code) {
        p.edit().putString(K_LNG_LAT_FORMAT, code).apply();
    }

    static int crsIndex(String code) {
        for (int i = 0; i < CRS_CODES.length; i++) {
            if (CRS_CODES[i].equals(code)) return i;
        }
        return -1;
    }

    static int formatIndex(String code) {
        for (int i = 0; i < FORMAT_CODES.length; i++) {
            if (FORMAT_CODES[i].equals(code)) return i;
        }
        return -1;
    }

    // ---- 滤镜 ----

    int colorMode() {
        return p.getInt(K_COLOR_MODE, MODE_HIGHLIGHT);
    }

    /** 选预设（0..2）时同时回填 5 个栅格参数；选自定义(3)只改模式，不动参数。 */
    void selectColorMode(int mode) {
        if (mode >= 0 && mode < PRESETS.length) {
            float[] v = PRESETS[mode];
            p.edit()
                    .putInt(K_COLOR_MODE, mode)
                    .putFloat(K_RASTER_BRIGHTNESS_MIN, v[0])
                    .putFloat(K_RASTER_BRIGHTNESS_MAX, v[1])
                    .putFloat(K_RASTER_SATURATION, v[2])
                    .putFloat(K_RASTER_CONTRAST, v[3])
                    .putFloat(K_RASTER_HUE_ROTATE, v[4])
                    .apply();
        } else {
            p.edit().putInt(K_COLOR_MODE, mode).apply();
        }
    }

    float rasterBrightnessMin() {
        return p.getFloat(K_RASTER_BRIGHTNESS_MIN, PRESETS[MODE_HIGHLIGHT][0]);
    }

    float rasterBrightnessMax() {
        return p.getFloat(K_RASTER_BRIGHTNESS_MAX, PRESETS[MODE_HIGHLIGHT][1]);
    }

    float rasterSaturation() {
        return p.getFloat(K_RASTER_SATURATION, PRESETS[MODE_HIGHLIGHT][2]);
    }

    float rasterContrast() {
        return p.getFloat(K_RASTER_CONTRAST, PRESETS[MODE_HIGHLIGHT][3]);
    }

    float rasterHueRotate() {
        return p.getFloat(K_RASTER_HUE_ROTATE, PRESETS[MODE_HIGHLIGHT][4]);
    }
}
