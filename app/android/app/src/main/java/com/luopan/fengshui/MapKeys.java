package com.luopan.fengshui;

/**
 * 天地图 key（原生侧：逆地理 / 分类搜索 / 瓦片下载）。
 *
 * 合规（MD-01 + 用户个人 key）：**仓库里没有任何 key 字面量**（仓库是 public）。
 * key 由 LC服务端 在构建期注入 `BuildConfig.TDT_SERVER_KEY`（服务端 key）/ `TDT_WEB_KEY`（网页端 key），
 * 来源是仓库外的 secrets 文件。换 key 只改构建环境，不用碰代码。
 *
 * 两个 key 分工（2026-11-05 实测）：
 *   - 服务端 key：`api.tianditu.gov.cn` 的逆地理 / v2/search（原生 HttpURLConnection，原生默认 UA → 200）；
 *   - 网页端 key：`t*.tianditu.gov.cn` 的 WMTS 瓦片，由 {@link MapAsset} 用 `?tk=` 传给 map.html，
 *     WebView 自带浏览器 UA（→ 200）。
 * 详见 {@link LcHttp} 类注释里的 UA×key 实测矩阵。
 */
final class MapKeys {

    /** 服务端 key（仅原生侧用；不要传给 WebView）。 */
    static final String TDT = BuildConfig.TDT_SERVER_KEY;

    /** 天地图分类编码：山 / 河流 / 湖泊（搜「分类编码表」Type.csv 实测）。 */
    static final String TYPE_MOUNTAIN = "260203";
    static final String TYPE_RIVER = "260204";
    static final String TYPE_LAKE = "260205";
    static final String TYPE_VALLEY = "260206";

    private MapKeys() {
    }
}
