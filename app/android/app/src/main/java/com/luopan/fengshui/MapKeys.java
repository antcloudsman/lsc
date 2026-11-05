package com.luopan.fengshui;

/**
 * 天地图 key（地图页与原生图表数据共用）。
 *
 * MD-01：**全仓只有一处 key 字面量**——`assets/map.html` 顶部的 `var KEY = "…";`。
 * Java 侧不再复制（以前 MapKeys.TDT 与 map.html 各一份，换 key 要改两处）；
 * 构建时由 `app/build.gradle` 从 map.html 解析并生成 `BuildConfig.TDT_KEY`，这里直接引用。
 * 换 key 只改 map.html 一行，解析失败构建直接失败（不会悄悄发出无 key 的包）。
 *
 * 当前值仍沿用原版内置的第三方 key（归属第三方，配 1 万次/日/服务，见 docs/map-bridge.md §2.3）；
 * 换自有 key 属发布门禁项，不在本轮。key 必须随包分发（客户端要发给天地图），
 * 所以日志与异常一律走 {@link LcHttp#redact} 脱敏（只留 host+path，剔掉 tk）。
 */
final class MapKeys {

    static final String TDT = BuildConfig.TDT_KEY;

    /** 天地图分类编码：山 / 河流 / 湖泊（搜「分类编码表」Type.csv 实测）。 */
    static final String TYPE_MOUNTAIN = "260203";
    static final String TYPE_RIVER = "260204";
    static final String TYPE_LAKE = "260205";
    static final String TYPE_VALLEY = "260206";

    private MapKeys() {
    }
}
