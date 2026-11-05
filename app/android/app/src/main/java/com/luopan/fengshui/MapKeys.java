package com.luopan.fengshui;

/**
 * 天地图 key（地图页与原生图表数据共用）。
 *
 * 注意：当前值沿用 `assets/map.html` 顶部内置的原版 key（归属第三方）。
 * 上线前应换 LC 自有 key（个人配额 1 万次/日/服务，见 docs/map-bridge.md §2.3）。
 * 换 key 时只改这一处 + map.html 的 KEY 常量。
 */
final class MapKeys {

    static final String TDT = "dd18858b33229f5837ad1d97b8d3105e";

    /** 天地图分类编码：山 / 河流 / 湖泊（搜「分类编码表」Type.csv 实测）。 */
    static final String TYPE_MOUNTAIN = "260203";
    static final String TYPE_RIVER = "260204";
    static final String TYPE_LAKE = "260205";
    static final String TYPE_VALLEY = "260206";

    private MapKeys() {
    }
}
