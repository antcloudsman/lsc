package com.luopan.fengshui;

/**
 * 地图工具目录（原版「更多 → 地图工具」16 项，顺序与名称取自
 * fix-p0 `docs/evidence/original-apk/orig-more.xml` 第 4–19 行，真机 dump，非推断）。
 *
 * key 只用于本地存储与排序，不展示；展示用 label。
 */
final class ToolCatalog {

    static final String[] KEYS = {
            "compass",      // 罗盘
            "hd_map",       // 超清地图
            "mountain",     // 看山脉
            "water",        // 看水系
            "altitude",     // 海拔查询
            "weather",      // 天气
            "history",      // 历史影像
            "contour",      // 等高线
            "interest",     // 收藏点
            "measure",      // 测量
            "brush",        // 画笔
            "fishing",      // 钓点
            "track",        // 轨迹记录
            "road",         // 路网
            "geo_imagery",  // 地理影像
            "hd_opt"        // 超清优化
    };

    static final String[] LABELS = {
            "罗盘",
            "超清地图",
            "看山脉",
            "看水系",
            "海拔查询",
            "天气",
            "历史影像",
            "等高线",
            "收藏点",
            "测量",
            "画笔",
            "钓点",
            "轨迹记录",
            "路网",
            "地理影像",
            "超清优化"
    };

    static final int COUNT = KEYS.length;

    private ToolCatalog() {
    }

    static String labelOf(String key) {
        for (int i = 0; i < COUNT; i++) {
            if (KEYS[i].equals(key)) return LABELS[i];
        }
        return key;
    }

    static boolean contains(String key) {
        for (String k : KEYS) {
            if (k.equals(key)) return true;
        }
        return false;
    }
}
