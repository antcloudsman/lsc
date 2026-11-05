package com.luopan.fengshui;

import android.net.Uri;

/**
 * `assets/map.html` 的加载地址（合规：天地图 key 不进仓库）。
 *
 * 两个 key 分工（2026-11-05 实测，见 {@link LcHttp} 类注释的 UA×key 矩阵）：
 *  - **服务端 key**（`BuildConfig.TDT_SERVER_KEY`）：原生侧调 api.tianditu.gov.cn；
 *  - **网页端 key**（`BuildConfig.TDT_WEB_KEY`）：WebView 里的瓦片，必须经 query 参数交给 JS——
 *    本类就负责这件事。
 *
 * 地址形态：`file:///android_asset/map.html?tk=<webKey>#in3d&measure=area`
 * **query 必须在 hash 之前**，否则 `#…` 后面的东西都会变成 hash 的一部分。
 * hash 语义完全不变（`#in3d`/`&measure=`/`&lat=` …），页面里用
 * `new URLSearchParams(location.search).get("tk")` 取 key（map.html 顶部）。
 *
 * 取不到 webKey 时只拼裸地址：页面会 console.warn 且不加载瓦片，**不会**回退到硬编码 key。
 */
final class MapAsset {

    private static final String BASE = "file:///android_asset/map.html";

    /** @param hash 形如 `#in3d&uimode=map&lat=..&lng=..`；null/空则不带 hash */
    static String url(String hash) {
        StringBuilder sb = new StringBuilder(BASE);
        String tk = BuildConfig.TDT_WEB_KEY;
        if (tk != null && !tk.isEmpty()) sb.append("?tk=").append(Uri.encode(tk));
        if (hash != null && !hash.isEmpty()) {
            if (hash.charAt(0) != '#') sb.append('#');
            sb.append(hash);
        }
        return sb.toString();
    }

    private MapAsset() {
    }
}
